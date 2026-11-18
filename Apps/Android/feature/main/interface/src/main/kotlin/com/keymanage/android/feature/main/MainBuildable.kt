package com.keymanage.android.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.generator.GeneratorBuildable
import com.keymanage.android.feature.settings.SettingsBuildable
import com.keymanage.android.feature.settings.SettingsListener
import com.keymanage.android.feature.vaultlist.VaultListBuildable
import com.keymanage.android.feature.vaultlist.VaultListListener
import com.keymanage.android.feature.vaultlist.VaultSaver
import com.uber.rib.core.Router

/** 잠금이 풀린 뒤의 탭 묶음(볼트·생성기·설정, ADR-AND-001의 Main). 잠그면 부모가 통째로 뗀다. */
interface MainBuildable {
    fun build(
        slot: MutableState<@Composable () -> Unit>,
        vault: VaultData,
        isOffline: Boolean,
        email: String,
        saver: VaultSaver,
        listener: MainListener,
    ): Router<*>
}

interface MainDependency {
    val vaultListBuildable: VaultListBuildable
    val generatorBuildable: GeneratorBuildable
    val settingsBuildable: SettingsBuildable
}

/** 하위 탭이 부모(Root)에 올리는 요청. */
interface MainListener : VaultListListener, SettingsListener
