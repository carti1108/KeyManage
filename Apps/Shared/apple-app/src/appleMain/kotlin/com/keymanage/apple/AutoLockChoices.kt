package com.keymanage.apple

import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewState
import kotlin.time.Duration

// Kotlin Duration은 Swift에 내부 표현(Int64)으로 넘어가 글자로 바꿀 수 없다. 설정 화면이 그릴 선택지를 여기서 만든다

/** 자동 잠금 선택지 하나. [action]을 그대로 보내면 고른다. [id]는 화면 요소 식별자(UI 테스트)로 쓰는 초 단위 값이다 */
class AutoLockChoice(val label: String, val isSelected: Boolean, val action: SettingsViewAction, val id: String)

fun autoLockChoices(state: SettingsViewState): List<AutoLockChoice> = state.autoLockOptions.map { timeout ->
    AutoLockChoice(
        autoLockLabel(timeout),
        timeout == state.autoLockTimeout,
        SettingsViewAction.SelectAutoLock(timeout),
        "autoLockChoice-${timeout.inWholeSeconds}",
    )
}

/** 0은 "즉시", 한 시간 단위로 떨어지면 "N시간", 아니면 "N분" (웹 AutoLockSelect와 같다) */
private fun autoLockLabel(timeout: Duration): String {
    val minutes = timeout.inWholeMinutes
    return when {
        timeout == Duration.ZERO -> "즉시"
        minutes % 60 == 0L -> "${minutes / 60}시간"
        else -> "${minutes}분"
    }
}
