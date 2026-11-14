package com.keymanage.ios

import com.keymanage.domain.clipboard.ClipboardWriter
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString
import platform.Foundation.NSTemporaryDirectory

// ClipboardBurner(COM-001)가 쓰는 NSPasteboard 쓰기. 빈 값은 소각이라 내용을 모두 지운다(iOS PasteboardClipboardWriter와 같다)
class NSPasteboardClipboardWriter(
    private val pasteboard: NSPasteboard = NSPasteboard.generalPasteboard,
) : ClipboardWriter {
    override suspend fun write(text: String) {
        pasteboard.clearContents()
        if (text.isNotEmpty()) pasteboard.setString(text, NSPasteboardTypeString)
    }
}

internal actual fun systemClipboardWriter(): ClipboardWriter = NSPasteboardClipboardWriter()

actual fun backupStagingDirectory(): String = NSTemporaryDirectory().trimEnd('/')
