package com.keymanage.ios

import com.keymanage.domain.clipboard.ClipboardWriter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun systemClipboardWriter(): ClipboardWriter = PasteboardClipboardWriter()

actual fun backupStagingDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
