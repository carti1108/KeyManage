package com.keymanage.android.data.lock

import android.content.SharedPreferences
import com.keymanage.android.domain.lock.BiometricSetting
import com.keymanage.android.domain.lock.BiometricSettingStore
import java.time.Instant

/** 생체 해제 켜짐 여부와 마지막 마스터 비밀번호 해제 시각을 기기 설정에 둔다. 비밀 값은 두지 않는다. */
class PreferencesBiometricSettingStore(private val preferences: SharedPreferences) : BiometricSettingStore {
    override fun read(): BiometricSetting = try {
        BiometricSetting(
            enabled = preferences.getBoolean(ENABLED, false),
            lastPasswordUnlock = if (preferences.contains(LAST)) Instant.ofEpochMilli(preferences.getLong(LAST, 0)) else null,
        )
    } catch (error: ClassCastException) {
        BiometricSetting()
    }

    override fun write(setting: BiometricSetting) {
        val editor = preferences.edit().putBoolean(ENABLED, setting.enabled)
        val last = setting.lastPasswordUnlock
        if (last == null) editor.remove(LAST) else editor.putLong(LAST, last.toEpochMilli())
        editor.apply()
    }

    private companion object {
        const val ENABLED = "biometricEnabled"
        const val LAST = "lastPasswordUnlockMillis"
    }
}
