package com.keymanage.android.feature.root.impl

/** Root 화면 상태. 하위 피처가 붙기 전까지는 앱이 화면에 나타났는지만 갖는다. */
data class RootViewState(
    val hasAppeared: Boolean = false,
)

/** Root 화면에서 View가 보내는 동작. */
sealed interface RootViewAction {
    data object ViewAppeared : RootViewAction
}
