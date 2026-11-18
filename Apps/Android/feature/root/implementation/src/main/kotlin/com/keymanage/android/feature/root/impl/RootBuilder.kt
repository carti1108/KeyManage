package com.keymanage.android.feature.root.impl

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.feature.root.RootBuildable
import com.keymanage.android.feature.root.RootDependency
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.EmptyPresenter
import com.uber.rib.core.ViewRouter

/** Root RIB 조립. 상태 저장소와 동작 스트림을 만들어 View와 Interactor에 함께 넘긴다. */
class RootBuilder(
    private val dependency: RootDependency,
) : RootBuildable {

    override fun build(parentViewGroup: ViewGroup): ViewRouter<*, *> {
        val store = ViewStateStore(RootViewState())
        val actions = ViewActionStream<RootViewAction>()
        val childSlot = mutableStateOf<@Composable () -> Unit>({})

        val view = ComposeView(parentViewGroup.context).apply {
            setContent {
                val state by store.state.collectAsState()
                KeyManageTheme {
                    RootView(state = state, onAction = { actions.send(it) }, childContent = { childSlot.value() })
                }
            }
        }
        val interactor = RootInteractor(EmptyPresenter(), store, actions, dependency.sessionGateway, dependency.workDispatcher)
        return RootRouter(
            view, interactor, childSlot,
            dependency.authBuildable, dependency.unlockBuildable, dependency.vaultListBuildable,
        )
    }
}
