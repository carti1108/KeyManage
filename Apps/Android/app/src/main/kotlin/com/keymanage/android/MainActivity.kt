package com.keymanage.android

import android.view.ViewGroup
import com.keymanage.android.data.crypto.HkdfKeySeparator
import com.keymanage.android.data.crypto.Pbkdf2MasterKeyDeriver
import com.keymanage.android.data.remote.HttpApiClient
import com.keymanage.android.domain.auth.AuthUseCase
import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.auth.AuthDependency
import com.keymanage.android.feature.auth.impl.AuthBuilder
import com.keymanage.android.feature.root.RootDependency
import com.keymanage.android.feature.root.impl.RootBuilder
import com.uber.rib.core.RibActivity
import com.uber.rib.core.ViewRouter
import kotlinx.coroutines.Dispatchers

/** 앱의 유일한 Activity. Root RIB 트리를 만들어 붙인다. */
class MainActivity : RibActivity() {
    override fun createRouter(parentViewGroup: ViewGroup): ViewRouter<*, *> =
        RootBuilder(AppRootDependency).build(parentViewGroup)
}

/** 앱 조립. 포트 구현(:core:data)을 피처 의존성에 넘기는 곳은 여기뿐이다(ADR-AND-001). */
private object AppRootDependency : RootDependency {
    private val api by lazy { HttpApiClient(BuildConfig.SERVER_URL) }

    override val authBuildable: AuthBuildable = AuthBuilder(
        object : AuthDependency {
            override val authUseCase by lazy { AuthUseCase(api, Pbkdf2MasterKeyDeriver(), HkdfKeySeparator()) }
            override val workDispatcher = Dispatchers.Default
        },
    )
}
