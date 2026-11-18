package com.keymanage.android

import android.view.ViewGroup
import com.keymanage.android.feature.root.RootDependency
import com.keymanage.android.feature.root.impl.RootBuilder
import com.uber.rib.core.RibActivity
import com.uber.rib.core.ViewRouter

/** 앱의 유일한 Activity. Root RIB 트리를 만들어 붙인다. */
class MainActivity : RibActivity() {
    override fun createRouter(parentViewGroup: ViewGroup): ViewRouter<*, *> =
        RootBuilder(AppRootDependency).build(parentViewGroup)
}

/** 앱 조립용 Root 의존성. 하위 피처를 붙일 때 실제 구현을 여기서 넘긴다. */
private object AppRootDependency : RootDependency
