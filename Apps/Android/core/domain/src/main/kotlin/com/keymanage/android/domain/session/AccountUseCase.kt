package com.keymanage.android.domain.session

import com.keymanage.android.domain.auth.AuthUseCase
import com.keymanage.android.domain.auth.NewPasswordProblem
import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.PasswordPreprocessor
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.crypto.VaultCipher
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.ChangePasswordRequest
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.SaveVaultUseCase
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.sync.VaultSyncCoordinator
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultSerializer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** 계정 삭제·비밀번호 변경 실패(SEC-004, SEC-005). */
sealed class AccountError(message: String) : Exception(message) {
    class WrongPassword : AccountError("마스터 비밀번호가 올바르지 않음")
    class SamePassword : AccountError("새 비밀번호가 현재와 같음")
    class WeakNewPassword(val problem: NewPasswordProblem) : AccountError("새 비밀번호 규칙 위반: $problem")

    /** 세션이 없거나 서버에 닿지 못했다. 비밀번호 변경은 결과를 알 수 없을 수 있다(SEC-004 규칙 9). */
    class Offline : AccountError("서버에 연결되지 않음")
    class Conflict : AccountError("볼트가 먼저 바뀜")

    /** 세션이 만료됐다. 비밀번호 변경이면 교체는 확정됐을 수 있어 새 비밀번호로 로그인한다(SEC-004 규칙 7). */
    class LoginRequired : AccountError("다시 로그인해야 함")
}

/**
 * 계정 삭제와 마스터 비밀번호 변경. 둘 다 잠금이 풀린 볼트에서, 세션이 있을 때만 한다.
 * 입력한 비밀번호가 지금 키와 맞는지 먼저 기기에서 확인해, 틀린 비밀번호로 서버 시도 횟수를 쓰지 않는다.
 * 키 유도가 있으므로 메인 스레드 밖에서 부른다.
 */
class AccountUseCase(
    private val authApi: AuthApi,
    private val deriver: MasterKeyDeriver,
    private val separator: KeySeparator,
    private val cipher: VaultCipher,
    private val serializer: VaultSerializer,
    private val coordinator: VaultSyncCoordinator,
    private val saltSource: () -> ByteArray = ::randomSalt,
) {
    /** 계정을 지우고(SEC-005 규칙 3·4) 이 기기의 기록을 지운다(규칙 5). 자동 잠금 같은 기기 설정은 남긴다. */
    fun deleteAccount(opened: OpenedVault, password: String) {
        val session = opened.session ?: throw AccountError.Offline()
        val keys = verify(opened, password)
        try {
            call { authApi.deleteAccount(session.accessToken, keys.authHash) }
        } finally {
            keys.authHash.fill(0)
            keys.encryptionKey.fill(0)
        }
        runCatching { coordinator.logout() }
    }

    /**
     * 마스터 비밀번호를 바꾼다(SEC-004). 새 salt와 현재 반복 횟수로 새 키를 만들고, [vault](대기 중인 변경 포함)를
     * 새 키로 다시 암호화해 KDF 값·인증 해시·볼트를 한 번에 교체한다. 성공하면 새 키·새 세션으로 연 볼트를 돌려준다.
     */
    fun changePassword(opened: OpenedVault, vault: VaultData, current: String, new: String, confirmation: String): OpenedVault {
        val session = opened.session ?: throw AccountError.Offline()
        if (vault.isReadOnly) throw SyncError.ReadOnlyVault(vault.version)
        AuthUseCase.checkNewPassword(new, confirmation)?.let { throw AccountError.WeakNewPassword(it) }
        if (new == current) throw AccountError.SamePassword()
        val currentKeys = verify(opened, current)
        currentKeys.encryptionKey.fill(0)
        val newKdf = KdfParameters(saltSource(), opened.account.kdfIterations)
        val newKeys = derive(new, newKdf)
        try {
            val plaintext = serializer.encode(vault)
            val payload = try {
                cipher.encrypt(plaintext, newKeys.encryptionKey)
            } finally {
                plaintext.fill(0)
            }
            val size = payload.canonicalJson().toByteArray(Charsets.UTF_8).size
            if (size > SaveVaultUseCase.MAX_PAYLOAD_BYTES) throw SyncError.VaultTooLarge(size)
            val request = ChangePasswordRequest(
                session.accessToken, currentKeys.authHash, newKeys.authHash, newKdf.iterations, newKdf.salt,
                coordinator.expectedRevision, payload,
            )
            val result = call { authApi.changePassword(request) }
            val changed = DeviceAccount(opened.account.email, newKdf.iterations, Base64.getEncoder().encodeToString(newKdf.salt))
            coordinator.adoptPasswordChange(changed, vault, newKeys.encryptionKey, result.revision)
            return OpenedVault(changed, vault, result.session, newKeys.encryptionKey)
        } catch (error: Exception) {
            newKeys.encryptionKey.fill(0)
            throw error
        } finally {
            currentKeys.authHash.fill(0)
            newKeys.authHash.fill(0)
        }
    }

    /** 계정 KDF 값으로 키를 만들어 지금 암호화 키와 같은지 본다. 다르면 [AccountError.WrongPassword]. */
    private fun verify(opened: OpenedVault, password: String): SeparatedKeys {
        val kdf = KdfParameters(Base64.getDecoder().decode(opened.account.kdfSalt), opened.account.kdfIterations)
        val keys = derive(password, kdf)
        if (!MessageDigest.isEqual(keys.encryptionKey, opened.encryptionKey)) {
            keys.encryptionKey.fill(0)
            keys.authHash.fill(0)
            throw AccountError.WrongPassword()
        }
        return keys
    }

    private fun derive(password: String, kdf: KdfParameters): SeparatedKeys {
        val passwordBytes = try {
            PasswordPreprocessor.preprocess(password)
        } catch (error: CryptoError.InvalidPassword) {
            throw AccountError.WrongPassword()
        }
        val master = try {
            deriver.deriveMasterKey(passwordBytes, kdf)
        } catch (error: CryptoError.InvalidPassword) {
            throw AccountError.WrongPassword()
        } finally {
            passwordBytes.fill(0)
        }
        try {
            return separator.separate(master)
        } finally {
            master.fill(0)
        }
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (error: ApiError.Network) {
        throw AccountError.Offline()
    } catch (error: ApiError.Server) {
        throw when {
            error.code == ServerErrorCode.AUTH_FAILED -> AccountError.WrongPassword()
            error.code == ServerErrorCode.VAULT_CONFLICT -> AccountError.Conflict()
            error.status == UNAUTHORIZED -> AccountError.LoginRequired()
            else -> error
        }
    }

    private companion object {
        const val UNAUTHORIZED = 401

        fun randomSalt(): ByteArray = ByteArray(KdfParameters.SALT_LENGTH).also(SecureRandom()::nextBytes)
    }
}
