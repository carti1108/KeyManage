package com.keymanage.domain.auth

import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.InvalidKdfParamsException
import com.keymanage.domain.crypto.MAX_ITERATIONS
import com.keymanage.domain.crypto.MIN_ITERATIONS
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.openVault
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.crypto.text
import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.encoding.toBase64
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// 원본: Apps/Web/src/domain/auth/authService*.test.ts, masterPassword.test.ts (SEC-002·003·004·005, api-spec 2.5)
private val session = Session(accessToken = "token-1", expiresAt = "2026-11-28T00:00:00Z")
private val vector = Vectors.keyCases[0]
private val vectorKdf = KdfParams(PBKDF2_SHA256, Vectors.iterations, vector.text("salt"))

/** 서버 대신 요청을 기록하고 정해 둔 응답을 돌려준다 */
private class FakeAuthApi(var kdf: KdfParams = vectorKdf) : AuthApi {
    val preloginEmails = mutableListOf<String>()
    val registered = mutableListOf<RegisterRequest>()
    val logins = mutableListOf<LoginRequest>()
    val loggedOut = mutableListOf<String>()
    val changes = mutableListOf<Pair<String, ChangePasswordRequest>>()
    val deletions = mutableListOf<Pair<String, DeleteAccountRequest>>()
    var preloginError: Exception? = null
    var loginError: Exception? = null
    var changeError: Exception? = null
    var deleteError: Exception? = null

    override suspend fun prelogin(email: String): KdfParams {
        preloginError?.let { throw it }
        preloginEmails += email
        return kdf
    }

    override suspend fun register(request: RegisterRequest): Session = session.also { registered += request }

    override suspend fun login(request: LoginRequest): Session {
        loginError?.let { throw it }
        logins += request
        return session
    }

    override suspend fun logout(accessToken: String) {
        loggedOut += accessToken
    }

    override suspend fun changePassword(accessToken: String, request: ChangePasswordRequest): ChangedPassword {
        changeError?.let { throw it }
        changes += accessToken to request
        return ChangedPassword("token-2", session.expiresAt, request.vault.expectedRevision + 1)
    }

    override suspend fun deleteAccount(accessToken: String, request: DeleteAccountRequest) {
        deleteError?.let { throw it }
        deletions += accessToken to request
    }
}

private fun ByteArray.allZero() = all { it == 0.toByte() }

class NormalizeEmailTest {
    @Test
    fun trimsAndLowercases() {
        assertEquals("user@example.com", normalizeEmail("  User@Example.COM "))
    }

    @Test
    fun rejectsEmptyTooLongOrMalformed() {
        listOf("", "   ", "no-at-sign", "a@b", "@", "a b@c.d", "a@@b.cd", "${"a".repeat(250)}@b.cd", "a　b@c.d")
            .forEach { assertFailsWith<InvalidEmailException>(it) { normalizeEmail(it) } }
        assertEquals(254, normalizeEmail("${"a".repeat(249)}@b.cd").length)
    }
}

class LoginTest {
    @Test
    fun derivesFromPreloginAndSendsVectorAuthHash() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi()

        val result = AuthService(api).login(" User@Example.com ", vector.text("password"))

        assertEquals(listOf("user@example.com"), api.preloginEmails)
        assertEquals(listOf(LoginRequest("user@example.com", vector.text("authHash"))), api.logins)
        assertEquals(session, result.session)
        // 잠금 해제 때 서버 없이 키를 다시 만들 수 있도록 KDF 값을 돌려준다 (SEC-003)
        assertEquals(UnlockKdf(vector.text("salt"), Vectors.iterations), result.kdf)
    }

    @Test
    fun returnedKeyEncryptsAndDecrypts() = runTest(timeout = 5.minutes) {
        val key = AuthService(FakeAuthApi()).login("a@b.cd", vector.text("password")).encryptionKey

        assertEquals("{}", openVault(key, sealVault(key, "{}")))
    }

    @Test
    fun weakOrExcessiveIterationsStopBeforeLogin() = runTest {
        listOf(MIN_ITERATIONS - 1, MAX_ITERATIONS + 1).forEach { iterations ->
            val api = FakeAuthApi(KdfParams(PBKDF2_SHA256, iterations, vector.text("salt")))

            assertFailsWith<InvalidKdfParamsException> { AuthService(api).login("a@b.cd", "pw") }
            assertEquals(emptyList(), api.logins)
        }
    }

    @Test
    fun unknownKdfOrNonStandardSaltStopBeforeLogin() = runTest {
        val wrongKdf = FakeAuthApi(KdfParams("argon2id", MIN_ITERATIONS, vector.text("salt")))
        val brokenSalt = FakeAuthApi(KdfParams(PBKDF2_SHA256, MIN_ITERATIONS, "not base64!"))

        listOf(wrongKdf, brokenSalt).forEach { api ->
            assertFailsWith<InvalidKdfParamsException> { AuthService(api).login("a@b.cd", "pw") }
            assertEquals(emptyList(), api.logins)
        }
    }
}

class RegisterTest {
    @Test
    fun registersWithFresh16ByteSaltAt600k_andLoginReproducesAuthHash() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi()

        val registered = AuthService(api).register("New@Example.com", "correct horse")
        val request = api.registered.single()

        assertEquals("new@example.com", request.email)
        assertEquals(16, fromBase64(request.kdfSalt).size)
        assertEquals(MIN_ITERATIONS, request.kdfIterations)
        assertEquals(32, fromBase64(request.authHash).size)
        assertEquals(UnlockKdf(request.kdfSalt, MIN_ITERATIONS), registered.kdf)

        val loginApi = FakeAuthApi(KdfParams(PBKDF2_SHA256, request.kdfIterations, request.kdfSalt))
        AuthService(loginApi).login("new@example.com", "correct horse")
        assertEquals(request.authHash, loginApi.logins[0].authHash)
    }

    @Test
    fun eachRegistrationUsesDifferentSalt() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi()
        val service = AuthService(api)

        service.register("a@b.cd", "pw")
        service.register("a@b.cd", "pw")

        assertNotEquals(api.registered[0].kdfSalt, api.registered[1].kdfSalt)
    }
}

class LogoutTest {
    @Test
    fun revokesGivenToken() = runTest {
        val api = FakeAuthApi()

        AuthService(api).logout("token-1")

        assertEquals(listOf("token-1"), api.loggedOut)
    }
}

class ResumeTest {
    private val stored = UnlockKdf(vector.text("salt"), Vectors.iterations)

    private suspend fun derivedAuthHash() = Vectors.keysOf(vector.text("name")).authHash

    @Test
    fun sameKdfLogsInWithExistingAuthHashAndWipesBytes() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi()
        val authHash = derivedAuthHash()

        assertEquals(session, AuthService(api).resume("user@example.com", stored, authHash))
        assertEquals(listOf(LoginRequest("user@example.com", vector.text("authHash"))), api.logins)
        assertTrue(authHash.allZero())
    }

    @Test
    fun differentKdfMeansPasswordChanged_noLoginSent() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi(KdfParams(PBKDF2_SHA256, Vectors.iterations, "AAAAAAAAAAAAAAAAAAAAAA=="))
        val authHash = derivedAuthHash()

        assertFailsWith<MasterPasswordChangedException> { AuthService(api).resume("user@example.com", stored, authHash) }
        assertEquals(emptyList(), api.logins)
        assertTrue(authHash.allZero())
    }

    @Test
    fun authFailedMeansPasswordChanged() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi().apply { loginError = ServerError(401, ServerErrorCode.AUTH_FAILED) }

        assertFailsWith<MasterPasswordChangedException> { AuthService(api).resume("user@example.com", stored, derivedAuthHash()) }
    }

    @Test
    fun networkFailureWipesBytesAndPropagates() = runTest(timeout = 5.minutes) {
        val api = FakeAuthApi().apply { preloginError = NetworkError() }
        val authHash = derivedAuthHash()

        assertFailsWith<NetworkError> { AuthService(api).resume("user@example.com", stored, authHash) }
        assertTrue(authHash.allZero())
    }
}

class KdfChangedTest {
    @Test
    fun comparesSaltAndIterationsWithoutLoggingIn() = runTest {
        val api = FakeAuthApi()
        val auth = AuthService(api)
        val same = UnlockKdf(vectorKdf.salt, vectorKdf.iterations)

        assertEquals(false, auth.kdfChanged("user@example.com", same))
        assertEquals(true, auth.kdfChanged("user@example.com", same.copy(salt = "AAAAAAAAAAAAAAAAAAAAAA==")))
        assertEquals(true, auth.kdfChanged("user@example.com", same.copy(iterations = same.iterations + 1)))
        assertEquals(0, api.logins.size)
    }

    @Test
    fun networkFailurePropagates() = runTest {
        val api = FakeAuthApi().apply { preloginError = NetworkError() }

        assertFailsWith<NetworkError> { AuthService(api).kdfChanged("user@example.com", UnlockKdf(vectorKdf.salt, vectorKdf.iterations)) }
    }
}

class ChangePasswordTest {
    private val payload = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn")
    private val kdf = UnlockKdf(toBase64(ByteArray(16) { 4 }), 600_000)

    @Test
    fun sendsBase64AuthHashesAndReturnsNewTokenAndRevision() = runTest {
        val api = FakeAuthApi()

        val changed = AuthService(api).changePassword("token-1", ByteArray(32) { 1 }, ByteArray(32) { 2 }, kdf, VaultReplacement(3, payload))

        assertEquals("token-2", changed.accessToken)
        assertEquals(4, changed.revision)
        assertEquals(
            listOf("token-1" to ChangePasswordRequest(toBase64(ByteArray(32) { 1 }), toBase64(ByteArray(32) { 2 }), kdf, VaultReplacement(3, payload))),
            api.changes,
        )
    }

    @Test
    fun wipesBothAuthHashesOnSuccess() = runTest {
        val current = ByteArray(32) { 1 }
        val next = ByteArray(32) { 2 }

        AuthService(FakeAuthApi()).changePassword("token-1", current, next, kdf, VaultReplacement(3, payload))

        assertTrue(current.allZero() && next.allZero())
    }

    @Test
    fun wipesBothAuthHashesOnFailureAndPropagates() = runTest {
        val api = FakeAuthApi().apply { changeError = ServerError(401, ServerErrorCode.AUTH_FAILED) }
        val current = ByteArray(32) { 1 }
        val next = ByteArray(32) { 2 }

        val error = assertFailsWith<ServerError> { AuthService(api).changePassword("token-1", current, next, kdf, VaultReplacement(3, payload)) }

        assertEquals(ServerErrorCode.AUTH_FAILED, error.code)
        assertTrue(current.allZero() && next.allZero())
    }
}

class DeleteAccountTest {
    @Test
    fun sendsBase64AuthHashWithToken() = runTest {
        val api = FakeAuthApi()

        AuthService(api).deleteAccount("token-1", ByteArray(32) { 1 })

        assertEquals(listOf("token-1" to DeleteAccountRequest(toBase64(ByteArray(32) { 1 }))), api.deletions)
    }

    @Test
    fun wipesAuthHashOnSuccess() = runTest {
        val authHash = ByteArray(32) { 1 }

        AuthService(FakeAuthApi()).deleteAccount("token-1", authHash)

        assertTrue(authHash.allZero())
    }

    @Test
    fun wipesAuthHashOnFailureAndPropagates() = runTest {
        val api = FakeAuthApi().apply { deleteError = ServerError(401, ServerErrorCode.AUTH_FAILED) }
        val authHash = ByteArray(32) { 1 }

        assertFailsWith<ServerError> { AuthService(api).deleteAccount("token-1", authHash) }
        assertTrue(authHash.allZero())
    }
}

class NewMasterPasswordTest {
    @Test
    fun minimumIs12() {
        assertEquals(12, MIN_MASTER_PASSWORD_LENGTH)
    }

    @Test
    fun under12IsTooShort_12Passes() {
        assertEquals(NewMasterPasswordProblem.TOO_SHORT, checkNewMasterPassword("a".repeat(11), "a".repeat(11)))
        assertNull(checkNewMasterPassword("a".repeat(12), "a".repeat(12)))
    }

    @Test
    fun countsCodePointsNotUtf16Units() {
        val sixEmoji = "🔑".repeat(6)
        assertEquals(NewMasterPasswordProblem.TOO_SHORT, checkNewMasterPassword(sixEmoji, sixEmoji))
    }

    @Test
    fun leadingAndTrailingSpacesCount() {
        val padded = " ${"a".repeat(10)} "
        assertNull(checkNewMasterPassword(padded, padded))
    }

    @Test
    fun differentConfirmationIsMismatch() {
        assertEquals(NewMasterPasswordProblem.MISMATCH, checkNewMasterPassword("a".repeat(12), "a".repeat(13)))
    }

    @Test
    fun tooShortIsReportedBeforeMismatch() {
        assertEquals(NewMasterPasswordProblem.TOO_SHORT, checkNewMasterPassword("short", "other"))
    }
}
