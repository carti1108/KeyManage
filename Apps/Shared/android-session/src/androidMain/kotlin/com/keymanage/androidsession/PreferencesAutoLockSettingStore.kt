package com.keymanage.androidsession

import android.content.Context
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import com.keymanage.domain.lock.formatAutoLockSetting
import com.keymanage.domain.lock.isAutoLockOption
import com.keymanage.domain.lock.parseAutoLockSetting
import kotlin.time.Duration

/**
 * 자동 잠금 시간을 앱 전용 SharedPreferences에 둔다 (SEC-003). 형식은 웹과 같은 밀리초 십진 문자열이다.
 * 읽기·쓰기가 실패해도 던지지 않고, 쓴 값은 이번 실행 동안 메모리에서 돌려준다.
 */
class PreferencesAutoLockSettingStore(context: Context, name: String = PREFERENCES_NAME) : AutoLockSettingStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private var written: Duration? = null

    override fun read(): Duration = written ?: parseAutoLockSetting(savedText())

    override fun write(timeout: Duration) {
        if (!isAutoLockOption(timeout)) throw InvalidAutoLockTimeoutException(timeout)
        written = timeout
        preferences.edit().putString(KEY, formatAutoLockSetting(timeout)).apply()
    }

    /** 앱은 화면이 하나라 다른 탭이 값을 바꾸는 일이 없다 */
    override fun subscribe(listener: (Duration) -> Unit): () -> Unit = {}

    // 다른 타입으로 저장돼 있으면 getString이 ClassCastException을 던진다
    private fun savedText(): String? = try {
        preferences.getString(KEY, null)
    } catch (_: ClassCastException) {
        null
    }

    private companion object {
        const val PREFERENCES_NAME = "keymanage.settings"
        const val KEY = "autoLockMs"
    }
}
