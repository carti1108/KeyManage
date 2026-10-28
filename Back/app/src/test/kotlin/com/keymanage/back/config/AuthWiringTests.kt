package com.keymanage.back.config

import com.keymanage.back.domain.auth.AuthHash
import com.keymanage.back.domain.auth.AuthenticateUseCase
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.KdfParams
import com.keymanage.back.domain.auth.LoginUseCase
import com.keymanage.back.domain.auth.LogoutUseCase
import com.keymanage.back.domain.auth.PreloginUseCase
import com.keymanage.back.domain.auth.RegisterUseCase
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 파일 저장소·Argon2·서버 비밀키가 조립된 상태로 가입 -> 로그인 -> 인증 -> 로그아웃이 동작하는지 확인한다 */
@SpringBootTest
class AuthWiringTests {

    @Autowired lateinit var prelogin: PreloginUseCase
    @Autowired lateinit var register: RegisterUseCase
    @Autowired lateinit var login: LoginUseCase
    @Autowired lateinit var authenticate: AuthenticateUseCase
    @Autowired lateinit var logout: LogoutUseCase

    @Test
    fun `가입한 계정으로 로그인하고 토큰으로 계정을 찾을 수 있다`() {
        val email = Email.of("wiring@example.com")
        val authHash = AuthHash.of(ByteArray(AuthHash.BYTES) { 4 })
        val kdf = KdfParams(ByteArray(KdfParams.SALT_BYTES) { 6 }, KdfParams.MIN_ITERATIONS)

        register.execute(email, authHash, kdf)
        val token = login.execute(email, authHash).token

        assertEquals(email, authenticate.execute(token).email)
        assertEquals(kdf, prelogin.execute(email))
        logout.execute(token)
        assertTrue(Files.exists(storageDir.resolve("server-secret")))
    }

    companion object {
        @TempDir
        @JvmStatic
        lateinit var storageDir: Path

        @DynamicPropertySource
        @JvmStatic
        fun storage(registry: DynamicPropertyRegistry) {
            registry.add("keymanage.auth.storage-dir") { storageDir.toString() }
        }
    }
}
