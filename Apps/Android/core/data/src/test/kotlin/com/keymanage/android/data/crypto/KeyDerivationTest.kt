package com.keymanage.android.data.crypto

import com.keymanage.android.domain.crypto.CryptoError
import com.keymanage.android.domain.crypto.KdfParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyDerivationTest {
    private val parameters = KdfParameters(ByteArray(KdfParameters.SALT_LENGTH), KdfParameters.DEFAULT_ITERATIONS)

    @Test
    fun `전처리 후 빈 비밀번호는 키를 유도하지 않고 거절한다`() {
        assertFailsWith<CryptoError.InvalidPassword> {
            Pbkdf2MasterKeyDeriver().deriveMasterKey(ByteArray(0), parameters)
        }
    }

    @Test
    fun `마스터 키가 32바이트가 아니면 키 분리를 거절한다`() {
        assertFailsWith<CryptoError.InvalidInput> { HkdfKeySeparator().separate(ByteArray(31)) }
    }

    @Test
    fun `분리한 두 키는 각각 32바이트다`() {
        val keys = HkdfKeySeparator().separate(ByteArray(KdfParameters.MASTER_KEY_LENGTH) { 7 })
        assertEquals(32, keys.encryptionKey.size)
        assertEquals(32, keys.authHash.size)
    }
}
