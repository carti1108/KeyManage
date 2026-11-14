package com.keymanage.ios

import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.feature.AppEnvironment
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** 백업 파일 이름의 현지 날짜. 시간대는 부를 때마다 읽어 사용자가 바꾼 시간대를 따른다 */
class TimeZoneCalendar(
    private val timeZone: () -> NSTimeZone = { NSTimeZone.localTimeZone },
) : LocalCalendar {
    override fun dateOf(time: Instant): LocalDay {
        val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)!!
        calendar.timeZone = timeZone()
        val date = NSDate.dateWithTimeIntervalSince1970(time.toEpochMilliseconds() / 1000.0)
        val parts = calendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, date)
        return LocalDay(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
    }
}

// 공유 RIB의 AppEnvironment iOS 구현. 진입점(2단계)이 NetworkPathMonitor를 시작·중지한다
class IosAppEnvironment(
    override val serverAddress: String,
    private val network: NetworkStatus,
    override val autoLockSetting: AutoLockSettingStore = UserDefaultsAutoLockSettingStore(),
    override val clock: Clock = Clock.System,
    override val calendar: LocalCalendar = TimeZoneCalendar(),
) : AppEnvironment {
    override fun isNetworkOffline(): Boolean = network.isOffline()

    override fun subscribeNetwork(listener: () -> Unit): () -> Unit = network.subscribe(listener)
}
