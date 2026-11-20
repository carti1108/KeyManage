package com.keymanage.androidsession

import android.content.Context
import com.keymanage.domain.lock.BiometricSetting
import com.keymanage.domain.lock.BiometricSettingStore
import kotlin.time.Instant

/**
 * 생체 해제 켜짐 여부와 마지막 마스터 비밀번호 해제 시각을 앱 전용 SharedPreferences에 둔다 (SEC-003 규칙 4).
 * 비밀 값은 두지 않는다. 읽지 못하면 꺼짐으로 본다
 */
class PreferencesBiometricSettingStore(context: Context, name: String = PREFERENCES_NAME) : BiometricSettingStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun read(): BiometricSetting = try {
        BiometricSetting(
            enabled = preferences.getBoolean(ENABLED, false),
            lastPasswordUnlock = if (preferences.contains(LAST)) Instant.fromEpochMilliseconds(preferences.getLong(LAST, 0)) else null,
        )
    } catch (_: ClassCastException) {
        BiometricSetting()
    }

    override fun write(setting: BiometricSetting) {
        val editor = preferences.edit().putBoolean(ENABLED, setting.enabled)
        val last = setting.lastPasswordUnlock
        if (last == null) editor.remove(LAST) else editor.putLong(LAST, last.toEpochMilliseconds())
        editor.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "keymanage.settings"
        const val ENABLED = "biometricEnabled"
        const val LAST = "lastPasswordUnlockMs"
    }
}
