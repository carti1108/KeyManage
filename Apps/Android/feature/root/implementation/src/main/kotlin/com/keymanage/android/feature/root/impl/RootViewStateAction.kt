package com.keymanage.android.feature.root.impl

/**
 * Root 화면 상태.
 * [notice]는 하위 화면이 바뀌어도 남겨 보여 줄 안내(비밀번호 바뀜, 볼트 준비 실패 등)다.
 */
data class RootViewState(
    val hasAppeared: Boolean = false,
    val notice: String? = null,
)

/** Root 화면에서 View가 보내는 동작. */
sealed interface RootViewAction {
    data object ViewAppeared : RootViewAction
    data object DismissNotice : RootViewAction
}
