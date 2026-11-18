package com.keymanage.android.data.lock

import android.content.SharedPreferences
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore

/** 자동 잠금 시간을 기기 설정에 둔다(SEC-003 값 표). 계정과 묶지 않아 로그아웃 뒤에도 남는다. */
class PreferencesAutoLockStore(private val preferences: SharedPreferences) : AutoLockSettingStore {
    private var override: AutoLockOption? = null

    override fun read(): AutoLockOption = override ?: try {
        AutoLockOption.fromStored(if (preferences.contains(KEY)) preferences.getLong(KEY, 0) else null)
    } catch (error: ClassCastException) {
        AutoLockOption.DEFAULT
    }

    override fun write(option: AutoLockOption) {
        override = option
        preferences.edit().putLong(KEY, option.millis).apply()
    }

    private companion object {
        const val KEY = "autoLockMillis"
    }
}
