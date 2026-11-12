package com.keymanage.domain.platform

import dev.whyoleg.cryptography.random.CryptographyRandom

// 도메인이 쓰는 난수. 원본은 crypto.getRandomValues를 직접 불렀다(auth·generator·session).
// 테스트에서 결과를 고정할 수 있도록 인터페이스로 받는다. 시간은 표준 라이브러리 kotlin.time.Clock을 그대로 쓴다
fun interface RandomBytes {
    fun nextBytes(size: Int): ByteArray
}

/** 플랫폼 보안 난수(WebCrypto, SecureRandom, SecRandomCopyBytes). 실제 앱의 기본값 */
object SecureRandomBytes : RandomBytes {
    override fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must not be negative: $size" }
        return CryptographyRandom.nextBytes(size)
    }
}
