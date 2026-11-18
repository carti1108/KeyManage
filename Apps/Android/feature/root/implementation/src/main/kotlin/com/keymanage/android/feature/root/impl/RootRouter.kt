package com.keymanage.android.feature.root.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.unlock.UnlockBuildable
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.feature.main.MainBuildable
import com.keymanage.android.feature.main.MainListener
import com.uber.rib.core.BasicViewRouter
import com.uber.rib.core.Router

/** Root가 붙일 하위 화면. 한 번에 하나만 붙는다. */
interface RootRouting {
    fun showAuth(listener: AuthListener)
    fun showUnlock(email: String, session: Session?, listener: UnlockListener)
    /** 잠금이 풀린 화면(Main 탭 묶음). */
    fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener)
}

/**
 * Root RIB 라우터. 하위 RIB(BasicComposeRouter)은 [childSlot]에 화면을 채운다.
 * 화면을 바꿀 때는 이전 하위 RIB을 먼저 떼어, 잠그면 볼트 목록 RIB과 그 평문 상태가 함께 사라진다(SEC-003).
 */
class RootRouter(
    view: ComposeView,
    interactor: RootInteractor,
    val childSlot: MutableState<@Composable () -> Unit>,
    private val authBuildable: AuthBuildable,
    private val unlockBuildable: UnlockBuildable,
    private val mainBuildable: MainBuildable,
) : BasicViewRouter<ComposeView, RootInteractor>(view, interactor), RootRouting {
    private var current: Router<*>? = null

    override fun showAuth(listener: AuthListener) = replace { authBuildable.build(childSlot, listener) }

    override fun showUnlock(email: String, session: Session?, listener: UnlockListener) =
        replace { unlockBuildable.build(childSlot, email, session, listener) }

    override fun showMain(vault: VaultData, isOffline: Boolean, email: String, listener: MainListener) =
        replace { mainBuildable.build(childSlot, vault, isOffline, email, interactor, listener) }

    private fun replace(build: () -> Router<*>) {
        current?.let(::detachChild)
        current = build().also(::attachChild)
    }
}
