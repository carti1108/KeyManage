package com.keymanage.android.feature.root.testing

import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.root.RootDependency

/** 테스트용 Root 의존성. 하위 피처 Buildable에 테스트 더블을 넣는다. */
class TestRootDependency(override val authBuildable: AuthBuildable) : RootDependency
