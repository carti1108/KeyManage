package com.keymanage.feature.settings

import kotlin.time.Duration

/**
 * 자동 잠금 선택지 하나. Duration은 Apple로 내보내면 내부 표현(Int64)이 되므로, Swift 뷰는 글자를 이 값으로 그리고
 * [timeout]은 해석하지 않고 그대로 [SettingsViewAction.SelectAutoLock]에 돌려보낸다
 */
data class AutoLockChoice(val label: String, val timeout: Duration, val isSelected: Boolean)

/** 자동 잠금 시간 표시. 선택지는 공유 도메인이 정하고 여기서는 글자로만 바꾼다 (SEC-003 값 표) */
fun autoLockLabel(timeout: Duration): String = when {
    timeout == Duration.ZERO -> "즉시"
    timeout.inWholeMinutes >= MINUTES_PER_HOUR && timeout.inWholeMinutes % MINUTES_PER_HOUR == 0L -> "${timeout.inWholeHours}시간"
    else -> "${timeout.inWholeMinutes}분"
}

private const val MINUTES_PER_HOUR = 60
