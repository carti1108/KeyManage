package com.keymanage.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.uber.rib.core.Router

/** 설정 탭 RIB(목업 7단계). */
interface SettingsBuildable {
    fun build(slot: MutableState<@Composable () -> Unit>, email: String, listener: SettingsListener): Router<*>
}

/** 서버에 올리지 못한 변경 사항이 있는지(VLT-002 규칙 11). */
fun interface PendingChanges {
    fun exist(): Boolean
}

interface SettingsDependency {
    val autoLockStore: AutoLockSettingStore
    val pendingChanges: PendingChanges
}

interface SettingsListener {
    /** 자동 잠금 시간을 바꿨다. 그때부터 새 시간으로 잰다(SEC-003 값 표). */
    fun onAutoLockChanged(option: AutoLockOption)

    /** 로그아웃을 확정했다. */
    fun onLogoutRequested()
}
