package com.keymanage.ios

import com.keymanage.domain.clipboard.ClipboardWriter
import platform.UIKit.UIPasteboard

// ClipboardBurner(COM-001)가 쓰는 UIPasteboard 쓰기. 빈 값은 소각이라 항목을 모두 지운다(Swift SystemPasteboard와 같다)
class PasteboardClipboardWriter(
    private val pasteboard: UIPasteboard = UIPasteboard.generalPasteboard,
) : ClipboardWriter {
    override suspend fun write(text: String) {
        if (text.isEmpty()) {
            pasteboard.items = emptyList<Any>()
        } else {
            pasteboard.string = text
        }
    }
}
