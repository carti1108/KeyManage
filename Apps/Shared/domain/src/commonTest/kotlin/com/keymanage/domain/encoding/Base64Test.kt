package com.keymanage.domain.encoding

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 원본: Apps/Web/src/domain/crypto/base64.ts. 플랫폼마다 판단이 갈리지 않게 표준 Base64만 받는다
class Base64Test {
    @Test
    fun encodesStandardAlphabetWithPadding() {
        assertEquals("", toBase64(byteArrayOf()))
        assertEquals("Zg==", toBase64("f".encodeToByteArray()))
        assertEquals("Zm8=", toBase64("fo".encodeToByteArray()))
        assertEquals("Zm9v", toBase64("foo".encodeToByteArray()))
        // 62·63번 문자는 URL용(-, _)이 아니라 +, /다
        assertEquals("+/8=", toBase64(byteArrayOf(0xFB.toByte(), 0xFF.toByte())))
    }

    @Test
    fun decodesWhatItEncodes() {
        val bytes = ByteArray(256) { it.toByte() }
        assertContentEquals(bytes, fromBase64(toBase64(bytes)))
    }

    @Test
    fun handlesLargeVaultSizedInput() {
        // 볼트 한도(5MB) 근처에서도 동작해야 한다
        val bytes = ByteArray(5 * 1024 * 1024) { (it * 31).toByte() }
        assertContentEquals(bytes, fromBase64(toBase64(bytes)))
    }

    @Test
    fun rejectsMissingPadding() {
        assertFailsWith<InvalidBase64Exception> { fromBase64("Zg") }
    }

    @Test
    fun rejectsWhitespace() {
        assertFailsWith<InvalidBase64Exception> { fromBase64("Zm9v\n") }
        assertFailsWith<InvalidBase64Exception> { fromBase64("Zm 9v") }
    }

    @Test
    fun rejectsUrlSafeAlphabet() {
        assertFailsWith<InvalidBase64Exception> { fromBase64("-_8=") }
    }

    @Test
    fun rejectsPaddingInTheMiddle() {
        assertFailsWith<InvalidBase64Exception> { fromBase64("Zg==Zg==") }
    }

    @Test
    fun rejectsTooMuchPadding() {
        assertFailsWith<InvalidBase64Exception> { fromBase64("Z===") }
    }
}
