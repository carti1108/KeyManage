package com.keymanage.android.feature.auth.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.auth.AuthListener
import com.uber.rib.core.Router

/** Root 등 부모 테스트용 Auth Buildable. 만든 횟수와 넘겨받은 listener를 기록한다. */
class FakeAuthBuildable(private val routerFactory: () -> Router<*>) : AuthBuildable {
    var buildCount = 0
        private set
    var listener: AuthListener? = null
        private set

    override fun build(slot: MutableState<@Composable () -> Unit>, listener: AuthListener): Router<*> {
        buildCount++
        this.listener = listener
        return routerFactory()
    }
}

/** 받은 인증 결과를 모으는 listener. */
class RecordingAuthListener : AuthListener {
    val results = mutableListOf<AuthResult>()
    override fun onAuthenticated(result: AuthResult) {
        results += result
    }
}
