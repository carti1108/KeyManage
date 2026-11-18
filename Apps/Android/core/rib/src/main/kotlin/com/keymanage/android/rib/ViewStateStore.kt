package com.keymanage.android.rib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * RIB 하나의 화면 상태. Interactor만 [update]로 바꾸고, View는 [state]를 구독해 그린다.
 * 상태는 불변 data class이고 바꿀 때는 copy()로 새 값을 만든다(ADR-AND-001).
 */
class ViewStateStore<S : Any>(initial: S) {
    private val mutableState = MutableStateFlow(initial)

    val state: StateFlow<S> = mutableState.asStateFlow()

    val current: S
        get() = mutableState.value

    fun update(transform: (S) -> S) {
        mutableState.update(transform)
    }
}
