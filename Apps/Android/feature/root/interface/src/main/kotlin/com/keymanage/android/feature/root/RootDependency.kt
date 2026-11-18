package com.keymanage.android.feature.root

import com.keymanage.android.feature.auth.AuthBuildable

/**
 * Root RIB이 부모(앱)에게서 받는 의존성.
 * 하위 피처(Auth, Unlock, Main)를 붙일 때 그 피처의 Buildable을 여기에 추가한다.
 */
interface RootDependency {
    val authBuildable: AuthBuildable
}
