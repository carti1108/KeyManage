package com.keymanage.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.auth.AuthUseCase
import com.uber.rib.core.Router
import kotlinx.coroutines.CoroutineDispatcher

/** Auth RIB(로그인·가입)을 만드는 진입점. 부모는 화면을 채울 [slot]과 결과를 받을 [listener]를 넘긴다. */
interface AuthBuildable {
    fun build(slot: MutableState<@Composable () -> Unit>, listener: AuthListener): Router<*>
}

/** Auth RIB이 부모에게서 받는 의존성. 키 유도(60만 회)와 통신은 [workDispatcher]에서 돈다. */
interface AuthDependency {
    val authUseCase: AuthUseCase
    val workDispatcher: CoroutineDispatcher
}

/** 인증 결과를 부모에게 알린다. [AuthResult.encryptionKey]의 소유권은 받은 쪽으로 넘어간다. */
fun interface AuthListener {
    fun onAuthenticated(result: AuthResult)
}
