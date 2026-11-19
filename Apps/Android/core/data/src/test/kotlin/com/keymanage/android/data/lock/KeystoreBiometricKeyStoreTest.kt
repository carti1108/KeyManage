package com.keymanage.android.data.lock

import com.keymanage.android.domain.lock.BiometricError
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.InvalidKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeystoreBiometricKeyStoreTest {
    private val dir: File = Files.createTempDirectory("km-bio").toFile()
    private val file = File(dir, "biometric-key.json")
    private val keys = MemoryKeys()
    private val prompts = mutableListOf<Int>()
    private var failure: BiometricError? = null
    private val store = KeystoreBiometricKeyStore(file, keys) { cipher ->
        failure?.let { throw it }
        prompts += cipher.blockSize
        cipher
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `감싼 키를 생체 인증 뒤 같은 값으로 꺼낸다`() = runTest {
        store.seal(ByteArray(32) { it.toByte() })
        assertTrue(store.hasKey())
        assertFalse(file.readText().contains("AAECAwQFBgc"), "볼트 키를 평문으로 두지 않는다")
        assertContentEquals(ByteArray(32) { it.toByte() }, store.open())
        assertEquals(2, prompts.size, "감쌀 때와 꺼낼 때 모두 생체 인증을 거친다")
    }

    @Test
    fun `생체 인증을 취소하면 파일을 남기지 않는다`() = runTest {
        failure = BiometricError.Cancelled()
        assertFailsWith<BiometricError.Cancelled> { store.seal(ByteArray(32)) }
        assertFalse(file.exists())
    }

    @Test
    fun `보호 키가 무효가 되면 무효로 알린다`() = runTest {
        store.seal(ByteArray(32))
        keys.invalid = true
        assertFailsWith<BiometricError.Invalidated> { store.open() }
    }

    @Test
    fun `보호 키가 사라지면 키가 없는 것으로 본다`() = runTest {
        store.seal(ByteArray(32))
        keys.key = null
        assertFalse(store.hasKey())
        assertFailsWith<BiometricError.Invalidated> { store.open() }
    }

    @Test
    fun `감싼 파일이 손상되면 무효로 알린다`() = runTest {
        store.seal(ByteArray(32))
        file.writeText("{")
        assertFailsWith<BiometricError.Invalidated> { store.open() }
    }

    @Test
    fun `지우면 파일과 보호 키를 모두 지운다`() = runTest {
        store.seal(ByteArray(32))
        store.clear()
        assertFalse(file.exists())
        assertFalse(store.hasKey())
        assertEquals(null, keys.key)
    }

    @Test
    fun `무효가 된 보호 키로 감싸려 하면 새 키를 만들어 감싼다`() = runTest {
        store.seal(ByteArray(32))
        keys.invalid = true
        store.seal(ByteArray(32) { 9 })
        assertContentEquals(ByteArray(32) { 9 }, store.open())
    }

    private class MemoryKeys : BiometricSecretKeys {
        var key: SecretKey? = null
        var invalid = false

        override fun existing(): SecretKey? = key

        override fun create(): SecretKey {
            invalid = false
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        }

        override fun delete() {
            key = null
        }

        override fun init(cipher: Cipher, mode: Int, key: SecretKey, iv: ByteArray?) {
            if (invalid) throw InvalidKeyException("무효")
            super.init(cipher, mode, key, iv)
        }
    }
}
