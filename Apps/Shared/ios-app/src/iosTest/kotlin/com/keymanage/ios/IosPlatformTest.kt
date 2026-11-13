package com.keymanage.ios

import com.keymanage.domain.backup.LocalDay
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.UIKit.UIPasteboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class IosPlatformTest {
    @Test
    fun calendarUsesGivenTimeZone() {
        // 2026-11-13T15:30Z는 서울(+9)에서 11월 14일이다
        val seoul = NSTimeZone.timeZoneForSecondsFromGMT(9 * 3600)
        val calendar = TimeZoneCalendar { seoul }

        assertEquals(LocalDay(2026, 11, 14), calendar.dateOf(Instant.parse("2026-11-13T15:30:00Z")))
    }

    @Test
    fun pasteboardWriterWritesAndEmptyTextClearsItems() = runTest {
        val pasteboard = UIPasteboard.pasteboardWithUniqueName()
        val writer = PasteboardClipboardWriter(pasteboard)

        writer.write("secret")
        assertEquals("secret", pasteboard.string)
        writer.write("")

        assertNull(pasteboard.string)
        UIPasteboard.removePasteboardWithName(pasteboard.name)
    }
}
