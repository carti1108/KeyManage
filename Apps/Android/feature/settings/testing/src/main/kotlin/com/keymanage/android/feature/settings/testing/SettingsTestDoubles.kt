package com.keymanage.android.feature.settings.testing

import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.feature.settings.SettingsListener

class MemoryAutoLockStore(var option: AutoLockOption = AutoLockOption.DEFAULT) : AutoLockSettingStore {
    override fun read() = option
    override fun write(option: AutoLockOption) {
        this.option = option
    }
}

class RecordingSettingsListener : SettingsListener {
    val autoLockChanges = mutableListOf<AutoLockOption>()
    var logoutRequests = 0
        private set

    override fun onAutoLockChanged(option: AutoLockOption) {
        autoLockChanges += option
    }

    override fun onLogoutRequested() {
        logoutRequests++
    }
}
