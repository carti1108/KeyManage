package com.keymanage.android.feature.settings.testing

import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.feature.settings.BiometricPort
import com.keymanage.android.feature.settings.SettingsListener

class MemoryAutoLockStore(var option: AutoLockOption = AutoLockOption.DEFAULT) : AutoLockSettingStore {
    override fun read() = option
    override fun write(option: AutoLockOption) {
        this.option = option
    }
}

/** 켜기·끄기를 기록하고 [failure]를 던지는 생체 해제 포트. */
class StubBiometricPort : BiometricPort {
    override var isAvailable = true
    override var isEnabled = false
    var failure: Exception? = null
    var enableCalls = 0
        private set
    var disableCalls = 0
        private set

    override suspend fun enable() {
        enableCalls++
        failure?.let { throw it }
        isEnabled = true
    }

    override fun disable() {
        disableCalls++
        isEnabled = false
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

    var deletedCount = 0
        private set
    var sessionExpiredCount = 0
        private set

    override fun onAccountDeleted() {
        deletedCount++
    }

    override fun onSessionExpired() {
        sessionExpiredCount++
    }
}
