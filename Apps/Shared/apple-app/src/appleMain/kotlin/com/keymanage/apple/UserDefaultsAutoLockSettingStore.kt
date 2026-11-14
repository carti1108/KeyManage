package com.keymanage.apple

import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import com.keymanage.domain.lock.formatAutoLockSetting
import com.keymanage.domain.lock.isAutoLockOption
import com.keymanage.domain.lock.parseAutoLockSetting
import platform.Foundation.NSUserDefaults
import kotlin.time.Duration

/** 저장 키. 값은 웹 localStorage와 같은 밀리초 십진 문자열이다 */
internal const val AUTO_LOCK_DEFAULTS_KEY = "keymanage.autoLockTimeoutMs"

// 자동 잠금 시간(SEC-003)을 UserDefaults에 둔다. 기기 설정이라 로그아웃해도 남는다.
// 앱은 탭이 하나라 다른 곳에서 바뀌는 일이 없어 subscribe는 알리지 않는다
class UserDefaultsAutoLockSettingStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AutoLockSettingStore {
    override fun read(): Duration = parseAutoLockSetting(defaults.stringForKey(AUTO_LOCK_DEFAULTS_KEY))

    override fun write(timeout: Duration) {
        if (!isAutoLockOption(timeout)) throw InvalidAutoLockTimeoutException(timeout)
        defaults.setObject(formatAutoLockSetting(timeout), AUTO_LOCK_DEFAULTS_KEY)
    }

    override fun subscribe(listener: (Duration) -> Unit): () -> Unit = {}
}
