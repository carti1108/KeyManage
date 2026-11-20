package com.keymanage.feature.main.testing

import com.keymanage.feature.main.MainListener

// Main 피처의 테스트 더블. 테스트(features/main/tests)와 각 플랫폼의 Example 앱이 함께 쓴다

/** 부모에 알린 내용을 기록한다. [onEvent]를 넣으면 알릴 때마다 한 줄 설명을 넘긴다(Example 앱의 결과 표시) */
class RecordingMainListener(private val onEvent: (String) -> Unit = {}) : MainListener {
    var locks = 0
    val logouts = mutableListOf<String?>()
    val signInRequests = mutableListOf<String>()

    override fun didLock() {
        locks += 1
        onEvent("didLock()")
    }

    override fun didLogout(notice: String?) {
        logouts += notice
        onEvent("didLogout(notice = $notice)")
    }

    override fun didRequireSignIn(notice: String) {
        signInRequests += notice
        onEvent("didRequireSignIn(notice = $notice)")
    }
}
