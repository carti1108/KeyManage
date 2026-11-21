package com.keymanage.applesession

import com.keymanage.domain.clipboard.ClipboardWriter
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly
import platform.UniformTypeIdentifiers.UTTypeUTF8PlainText
import kotlin.time.Duration

/**
 * 시스템 클립보드에 쓴다 (COM-001). 복사한 값은 이 기기에만 두고(유니버설 클립보드로 다른 기기에 넘기지 않음),
 * 앱이 꺼져 타이머가 돌지 못해도 iOS가 비울 시각을 함께 건다.
 * 비울 때는 마지막으로 쓴 뒤 클립보드가 바뀌지 않았을 때만 비워, 사용자가 다른 앱에서 복사한 값을 지우지 않는다. 메인 스레드에서만 부른다
 */
class PasteboardClipboardWriter(
    private val pasteboard: UIPasteboard = UIPasteboard.generalPasteboard,
    private val lifetime: Duration,
) : ClipboardWriter {
    private var ownChangeCount: Long? = null

    override suspend fun write(text: String) {
        if (text.isEmpty()) clear() else copySecret(text)
    }

    private fun copySecret(text: String) {
        val expiresAt = NSDate().dateByAddingTimeInterval(lifetime.inWholeMilliseconds / MILLIS_PER_SECOND)
        pasteboard.setItems(
            listOf(mapOf(UTTypeUTF8PlainText.identifier to text)),
            options = mapOf<Any?, Any?>(UIPasteboardOptionLocalOnly to true, UIPasteboardOptionExpirationDate to expiresAt),
        )
        ownChangeCount = pasteboard.changeCount
    }

    private fun clear() {
        val mine = ownChangeCount ?: return
        ownChangeCount = null
        if (pasteboard.changeCount == mine) pasteboard.setItems(emptyList<Map<Any?, Any?>>(), options = emptyMap<Any?, Any?>())
    }
}
