package com.keymanage.apple

import com.keymanage.domain.clipboard.ClipboardWriter
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

/** NSPasteboard가 글자를 받지 않음. 공유 복사 경로가 "클립보드에 복사하지 못했습니다." 안내를 띄운다(COM-001) */
class ClipboardWriteException : Exception("클립보드에 쓰지 못함")

// ClipboardBurner(COM-001)가 쓰는 NSPasteboard 쓰기. 빈 값은 소각이라 내용을 모두 지운다(iOS PasteboardClipboardWriter와 같다).
// setString이 false면 던진다(옛 Swift SystemPasteboard의 ClipboardError.copyFailed와 같다)
class NSPasteboardClipboardWriter(
    private val pasteboard: NSPasteboard = NSPasteboard.generalPasteboard,
    private val putString: (NSPasteboard, String) -> Boolean = { board, text -> board.setString(text, NSPasteboardTypeString) },
) : ClipboardWriter {
    override suspend fun write(text: String) {
        pasteboard.clearContents()
        if (text.isNotEmpty() && !putString(pasteboard, text)) throw ClipboardWriteException()
    }
}

internal actual fun systemClipboardWriter(): ClipboardWriter = NSPasteboardClipboardWriter()

actual fun backupStagingDirectory(): String = NSTemporaryDirectory().trimEnd('/')

private val STAGED_BACKUP_NAME = Regex("""keymanage-backup-\d{8}\.json""")

/**
 * 저장 패널이 열린 채 앱이 끝나 임시 폴더에 남은 백업 파일(VLT-004, 볼트 암호문)을 지운다. 앱 시작 때 부른다.
 * 이 앱이 쓰는 이름(backupFileName)만 지우고, 지우지 못한 파일은 다음 시작 때 다시 지운다
 */
@OptIn(ExperimentalForeignApi::class)
fun removeStagedBackups(directory: String) {
    val files = NSFileManager.defaultManager
    val names = files.contentsOfDirectoryAtPath(directory, null) ?: return
    names.filterIsInstance<String>()
        .filter { STAGED_BACKUP_NAME.matches(it) }
        .forEach { files.removeItemAtPath("$directory/$it", null) }
}
