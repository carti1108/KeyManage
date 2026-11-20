package com.keymanage.feature.generator.testing

import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.generator.PasswordGeneratorListener
import kotlin.random.Random

// PasswordGenerator 피처의 테스트 더블. 테스트(features/generator/tests)와 각 플랫폼의 Example 앱이 함께 쓴다

/** 복사한 값을 기록한다. [failure]를 넣으면 복사가 그 오류로 실패한다 */
class FakeGeneratorClipboard : ClipboardPort {
    val copied = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun copy(text: String) {
        failure?.let { throw it }
        copied += text
    }
}

/** 부모에 알린 내용을 기록한다. [onEvent]를 넣으면 알릴 때마다 한 줄 설명을 넘긴다(Example 앱의 결과 표시) */
class RecordingGeneratorListener(private val onEvent: (String) -> Unit = {}) : PasswordGeneratorListener {
    val used = mutableListOf<String>()
    var closed = 0

    override fun didUsePassword(password: String) {
        used += password
        onEvent("didUsePassword(${password.length}자)")
    }

    override fun didClosePasswordGenerator() {
        closed++
        onEvent("didClosePasswordGenerator()")
    }
}

/** 시드를 고정한 난수. 몇 번 불렸는지 센다 */
class SeededRandomBytes(seed: Int = 42) : RandomBytes {
    private val random = Random(seed)
    var calls = 0
        private set

    override fun nextBytes(size: Int): ByteArray {
        calls++
        return random.nextBytes(size)
    }
}
