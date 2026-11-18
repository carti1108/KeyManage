package com.keymanage.android.domain.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KdfParametersTest {
    private val salt = ByteArray(KdfParameters.SALT_LENGTH)

    @Test
    fun `반복 횟수 600,000과 10,000,000은 경계값으로 허용한다`() {
        assertEquals(600_000, KdfParameters(salt, 600_000).iterations)
        assertEquals(10_000_000, KdfParameters(salt, 10_000_000).iterations)
    }

    @Test
    fun `반복 횟수 599,999와 10,000,001은 거절한다`() {
        assertFailsWith<CryptoError.InvalidKdfParameters> { KdfParameters(salt, 599_999) }
        assertFailsWith<CryptoError.InvalidKdfParameters> { KdfParameters(salt, 10_000_001) }
    }

    @Test
    fun `salt가 16바이트가 아니면 거절한다`() {
        assertFailsWith<CryptoError.InvalidKdfParameters> { KdfParameters(ByteArray(15), 600_000) }
        assertFailsWith<CryptoError.InvalidKdfParameters> { KdfParameters(ByteArray(17), 600_000) }
    }
}
