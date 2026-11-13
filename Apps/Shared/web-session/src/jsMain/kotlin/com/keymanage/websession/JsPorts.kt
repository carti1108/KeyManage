package com.keymanage.websession

import kotlin.js.Promise

// 웹(TypeScript)이 구현해 createKotlinSession에 넘기는 포트. 모양은 docs/research/kmp-migration의 web-session 계약과 같다
// 기록·메시지는 JSON 문자열로 주고받고, 실패는 Promise reject(또는 throw)로 알린다

/** 기기 저장소(IndexedDB). load는 기록이 없으면 null */
@JsExport
external interface JsDeviceStore {
    fun load(): Promise<String?>
    fun save(recordJson: String): Promise<Unit>
    fun clear(): Promise<Unit>
}

/** 탭 사이 잠금(navigator.locks)과 알림(BroadcastChannel). name은 'device' | 'sync', kind는 'record-changed' | 'locked' | 'logged-out' | 'activity' */
@JsExport
external interface JsTabs {
    /** 잠금을 못 얻으면(ifAvailable) fn을 부르지 않고 null로 끝난다 */
    fun lockRun(name: String, ifAvailable: Boolean, fn: () -> Promise<Any?>): Promise<Any?>
    fun post(kind: String)

    /** 다른 탭이 보낸 알림만 받는다. 돌려주는 함수가 해제다 */
    fun subscribe(listener: (String) -> Unit): () -> Unit
}

/** 이 탭이 마지막으로 연 계정의 KDF 값(sessionStorage). {"email","salt","iterations"} JSON */
@JsExport
external interface JsTabMemory {
    fun get(): String?
    fun set(json: String)
    fun clear()
}
