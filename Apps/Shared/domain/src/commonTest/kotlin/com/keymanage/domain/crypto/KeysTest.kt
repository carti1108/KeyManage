package com.keymanage.domain.crypto

import com.keymanage.domain.encoding.fromBase64
import com.keymanage.domain.encoding.toBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

// 원본: Apps/Web/src/domain/crypto/keys.test.ts (vault-spec 2장, ADR-0011)
class PreprocessPasswordTest {
    @Test
    fun matchesVectorsForEveryCase() {
        Vectors.keyCases.forEach { case ->
            assertEquals(case.text("normalizedPasswordUtf8"), toBase64(preprocessPassword(case.text("password")).encodeToByteArray()), case.text("name"))
        }
    }

    @Test
    fun keepsInnerSpacesAndUnlistedSpaceCharacters() {
        // 앞뒤는 U+2003(em space)이다. 지정한 공백 문자가 아니라 자르지 않는다
        assertEquals(" a b ", preprocessPassword(" a b "))
    }

    @Test
    fun rejectsLoneSurrogates() {
        assertFailsWith<InvalidPasswordException> { preprocessPassword("ab\uD83Dcd") }
        assertFailsWith<InvalidPasswordException> { preprocessPassword("\uDD11ab") }
        assertEquals("🔑", preprocessPassword("🔑"))
    }
}

class DeriveKeysTest {
    @Test
    fun authHashAndEncryptionKeyMatchVectors() = runTest(timeout = 5.minutes) {
        Vectors.keyCases.forEach { case ->
            val keys = Vectors.keysOf(case.text("name"))
            assertEquals(case.text("authHash"), toBase64(keys.authHash), case.text("name"))
            assertContentEquals(fromBase64(case.text("encryptionKey")), keys.encryptionKey.bytes, case.text("name"))
        }
    }

    @Test
    fun encryptionKeyDoesNotPrintItsBytes() = runTest(timeout = 5.minutes) {
        assertEquals("EncryptionKey(***)", Vectors.keysOf("ascii-passphrase").encryptionKey.toString())
    }

    @Test
    fun rejectsTooFewIterations() = runTest {
        assertFailsWith<InvalidKdfParamsException> { deriveKeys("pw", ByteArray(16), MIN_ITERATIONS - 1) }
    }

    @Test
    fun rejectsTooManyIterationsWithoutComputing() = runTest {
        assertFailsWith<InvalidKdfParamsException> { deriveKeys("pw", ByteArray(16), MAX_ITERATIONS + 1) }
    }

    @Test
    fun rejectsSaltOtherThan16Bytes() = runTest {
        assertFailsWith<InvalidKdfParamsException> { deriveKeys("pw", ByteArray(15), MIN_ITERATIONS) }
    }

    @Test
    fun rejectsLoneSurrogatePasswordBeforeDerivation() = runTest {
        assertFailsWith<InvalidPasswordException> { deriveKeys("ab\uD83D", ByteArray(16), MIN_ITERATIONS) }
    }
}
