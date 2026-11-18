package com.keymanage.android.feature.root

import android.view.ViewGroup
import com.uber.rib.core.ViewRouter

/** Root RIB을 만드는 진입점. 앱 조립(`:app`)은 이 인터페이스만 안다. */
interface RootBuildable {
    fun build(parentViewGroup: ViewGroup): ViewRouter<*, *>
}
