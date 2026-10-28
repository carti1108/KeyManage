package com.keymanage.back.infrastructure.auth

import com.keymanage.back.domain.auth.ServerSecret
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ServerSecretLoaderTests {

    @TempDir
    lateinit var root: Path

    private val file get() = root.resolve("auth").resolve("server-secret")

    // 비밀키 값은 밖으로 꺼내지 않으므로 같은 입력에 대한 HMAC 결과로 같은 키인지 비교한다
    private fun fingerprint(secret: ServerSecret) = secret.hmac("probe".toByteArray()).toList()

    @Test
    fun `환경 변수가 있으면 그 값을 쓰고 파일을 만들지 않는다`() {
        val value = ByteArray(32) { 5 }

        val secret = ServerSecretLoader.load(Base64.getEncoder().encodeToString(value), file)

        assertEquals(fingerprint(ServerSecret(value)), fingerprint(secret))
        assertFalse(Files.exists(file))
    }

    @Test
    fun `환경 변수가 없으면 첫 실행에 만들고 다음 실행에도 같은 값을 쓴다`() {
        val first = ServerSecretLoader.load(null, file)
        val second = ServerSecretLoader.load("", file)

        assertEquals(fingerprint(first), fingerprint(second))
        assertEquals(32, Files.size(file).toInt())
    }

    @Test
    fun `설치본마다 다른 값이 만들어진다`() {
        val a = ServerSecretLoader.load(null, root.resolve("a"))
        val b = ServerSecretLoader.load(null, root.resolve("b"))

        assertNotEquals(fingerprint(a), fingerprint(b))
    }

    @Test
    fun `환경 변수가 32바이트 Base64가 아니면 기동을 멈춘다`() {
        assertFailsWith<IllegalStateException> { ServerSecretLoader.load("not-base64!", file) }
        assertFailsWith<IllegalStateException> { ServerSecretLoader.load(Base64.getEncoder().encodeToString(ByteArray(16)), file) }
    }

    @Test
    fun `저장된 파일이 32바이트가 아니면 기동을 멈춘다`() {
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(10))

        assertFailsWith<IllegalStateException> { ServerSecretLoader.load(null, file) }
    }
}
