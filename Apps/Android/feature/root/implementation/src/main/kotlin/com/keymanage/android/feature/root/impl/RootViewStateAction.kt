package com.keymanage.android.feature.root.impl

/**
 * Root 화면 상태.
 * [signedInEmail]은 인증을 마친 계정이다. LoggedIn RIB(볼트 화면)이 붙기 전까지 Root가 임시 화면으로 보여 준다.
 */
data class RootViewState(
    val hasAppeared: Boolean = false,
    val signedInEmail: String? = null,
)

/** Root 화면에서 View가 보내는 동작. */
sealed interface RootViewAction {
    data object ViewAppeared : RootViewAction
}
