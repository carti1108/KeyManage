package com.keymanage.android.feature.main.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.keymanage.android.designsystem.component.KMIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.main.MainBuildable
import com.keymanage.android.feature.main.MainDependency
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.settings.AccountPort
import com.keymanage.android.feature.settings.BiometricPort
import com.keymanage.android.feature.settings.BackupPort
import com.keymanage.android.feature.vaultlist.VaultSaver
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.ComposePresenter
import com.uber.rib.core.Router

/** 하단 탭 바와 탭 내용. 탭 내용은 하위 RIB이 [childSlot]에 채운다. */
class MainPresenter(
    private val store: ViewStateStore<MainViewState>,
    private val actions: ViewActionStream<MainViewAction>,
) : ComposePresenter() {
    val childSlot: MutableState<@Composable () -> Unit> = mutableStateOf({})

    override val composable: @Composable () -> Unit = {
        val state by store.state.collectAsState()
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) { childSlot.value() }
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == state.tab,
                        onClick = { actions.send(MainViewAction.SelectTab(tab)) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    }
}

/** 탭 아이콘(platform-bindings.md 아이콘 표). */
private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.VAULT -> KMIcons.Lock
        MainTab.GENERATOR -> KMIcons.Key
        MainTab.SETTINGS -> KMIcons.Settings
    }

/** 탭마다 하위 RIB을 하나만 붙인다. */
class MainRouter(
    presenter: MainPresenter,
    interactor: MainInteractor,
    slot: MutableState<@Composable () -> Unit>,
    private val dependency: MainDependency,
    private val email: String,
    private val isOffline: Boolean,
    private val account: AccountPort,
    private val biometric: BiometricPort,
) : BasicComposeRouter<MainInteractor>(presenter, interactor, slot), MainRouting {
    private val childSlot = presenter.childSlot
    private var current: Router<*>? = null

    override fun showVault(vault: VaultData) =
        replace { dependency.vaultListBuildable.build(childSlot, vault, isOffline, interactor, interactor.mainListener) }

    override fun showGenerator() = replace { dependency.generatorBuildable.build(childSlot) }

    override fun showSettings() = replace { dependency.settingsBuildable.build(childSlot, email, interactor, account, biometric, interactor.mainListener) }

    private fun replace(build: () -> Router<*>) {
        current?.let(::detachChild)
        current = build().also(::attachChild)
    }
}

class MainBuilder(private val dependency: MainDependency) : MainBuildable {
    override fun build(
        slot: MutableState<@Composable () -> Unit>,
        vault: VaultData,
        isOffline: Boolean,
        email: String,
        saver: VaultSaver,
        backup: BackupPort,
        account: AccountPort,
        biometric: BiometricPort,
        listener: MainListener,
    ): Router<*> {
        val store = ViewStateStore(MainViewState())
        val actions = ViewActionStream<MainViewAction>()
        val presenter = MainPresenter(store, actions)
        val interactor = MainInteractor(presenter, store, actions, vault, saver, listener, parentBackup = backup)
        return MainRouter(presenter, interactor, slot, dependency, email, isOffline, account, biometric)
    }
}
