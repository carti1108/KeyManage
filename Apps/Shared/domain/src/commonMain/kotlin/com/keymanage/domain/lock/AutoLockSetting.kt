package com.keymanage.domain.lock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// 자동 잠금 시간 설정 (SEC-003, ADR-WEB-002). 기기마다 저장하고 계정과 묶지 않는다
// 원본: Apps/Web/src/domain/lock/autoLockSetting.ts

/** 자동 잠금 시간을 기기에 두는 포트(localStorage, SharedPreferences, UserDefaults). 실패해도 던지지 않는다 */
interface AutoLockSettingStore {
    /** 저장값. 없거나 선택지가 아니거나 읽지 못하면 기본 5분 */
    fun read(): Duration

    /**
     * 바꾼 값을 저장한다. 저장하지 못하면 이번 실행에만 쓴다
     * @throws InvalidAutoLockTimeoutException 선택지가 아닐 때
     */
    fun write(timeout: Duration)

    /** 다른 탭이 바꾼 값을 받는다. 이 화면의 write는 알리지 않는다 */
    fun subscribe(listener: (Duration) -> Unit): () -> Unit
}

// 밀리초를 십진 정수로만 받는다. 공백·부호·소수·지수 표기는 저장한 적 없는 값이다
private val CANONICAL_MS = Regex("^(0|[1-9][0-9]*)$")

/** 저장한 문자열(밀리초)을 선택지로 바꾼다. 그 밖의 값은 기본 5분 */
fun parseAutoLockSetting(raw: String?): Duration {
    if (raw == null || !CANONICAL_MS.matches(raw)) return DEFAULT_AUTO_LOCK
    val timeout = raw.toLongOrNull()?.milliseconds ?: return DEFAULT_AUTO_LOCK
    return if (isAutoLockOption(timeout)) timeout else DEFAULT_AUTO_LOCK
}

/** 저장 형식. 원본과 같은 밀리초 십진 문자열이다 */
fun formatAutoLockSetting(timeout: Duration): String = timeout.inWholeMilliseconds.toString()
