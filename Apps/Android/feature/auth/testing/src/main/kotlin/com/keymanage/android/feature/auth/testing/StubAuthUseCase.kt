package com.keymanage.android.feature.auth.testing

import com.keymanage.android.domain.auth.AuthUseCase
import com.keymanage.android.domain.crypto.KdfParameters
import com.keymanage.android.domain.crypto.KeySeparator
import com.keymanage.android.domain.crypto.MasterKeyDeriver
import com.keymanage.android.domain.crypto.SeparatedKeys
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.ChangePasswordRequest
import com.keymanage.android.domain.remote.ChangePasswordResult
import com.keymanage.android.domain.remote.PreloginInfo
import com.keymanage.android.domain.remote.Session
import java.time.Instant

/** 서버 대신 쓰는 AuthApi. [failure]가 있으면 가입·로그인에서 던진다. */
class StubAuthApi(var failure: Exception? = null) : AuthApi {
    var loginCalls = 0
        private set
    var registerCalls = 0
        private set

    override fun prelogin(email: String) = PreloginInfo("PBKDF2-HMAC-SHA256", KdfParameters.DEFAULT_ITERATIONS, ByteArray(KdfParameters.SALT_LENGTH))

    override fun register(email: String, authHash: ByteArray, kdfSalt: ByteArray, kdfIterations: Int): Session {
        registerCalls++
        failure?.let { throw it }
        return SESSION
    }

    override fun login(email: String, authHash: ByteArray): Session {
        loginCalls++
        failure?.let { throw it }
        return SESSION
    }

    override fun logout(accessToken: String) = Unit
    override fun changePassword(request: ChangePasswordRequest): ChangePasswordResult = error("Auth 테스트에서 쓰지 않음")
    override fun deleteAccount(accessToken: String, authHash: ByteArray) = Unit

    companion object {
        val SESSION = Session("test-token", Instant.parse("2026-12-18T00:00:00Z"))
    }
}

/** 60만 회 유도 없이 바로 키를 만드는 테스트 더블. */
object InstantKeyDerivation : MasterKeyDeriver, KeySeparator {
    override fun deriveMasterKey(password: ByteArray, parameters: KdfParameters) = password.copyOf(KdfParameters.MASTER_KEY_LENGTH)
    override fun separate(masterKey: ByteArray) = SeparatedKeys(masterKey.copyOf(), masterKey.reversedArray())
}

/** [StubAuthApi]와 [InstantKeyDerivation]으로 만든 AuthUseCase. */
fun stubAuthUseCase(api: StubAuthApi = StubAuthApi()) =
    AuthUseCase(api, InstantKeyDerivation, InstantKeyDerivation, saltSource = { ByteArray(KdfParameters.SALT_LENGTH) })
