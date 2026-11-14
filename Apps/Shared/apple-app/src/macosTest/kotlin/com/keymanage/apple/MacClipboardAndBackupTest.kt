package com.keymanage.apple

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.AppKit.NSPasteboard
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// macOS 리뷰 반영(domain-migration-plan 12.5 4단계): 클립보드 쓰기 실패 전파, 남은 백업 임시 파일 지움
class MacClipboardAndBackupTest {
    @Test
    fun failedSetStringThrowsSoTheCopyFailedNoticeShows() = runTest {
        // 옛 Swift SystemPasteboard가 setString 실패에 ClipboardError.copyFailed를 던진 것과 같다(COM-001)
        val pasteboard = NSPasteboard.pasteboardWithUniqueName()
        val writer = NSPasteboardClipboardWriter(pasteboard, putString = { _, _ -> false })

        assertFailsWith<ClipboardWriteException> { writer.write("secret") }
        writer.write("")
        pasteboard.releaseGlobally()
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun staleStagedBackupsAreRemovedAndOtherFilesAreKept() {
        // 저장 패널이 열린 채 앱이 끝나면 임시 백업 파일이 남는다. 다음 시작 때 이 앱의 백업 이름만 지운다
        val directory = NSTemporaryDirectory().trimEnd('/') + "/staged-backup-test-" + NSUUID().UUIDString
        val files = NSFileManager.defaultManager
        files.createDirectoryAtPath(directory, true, null, null)
        listOf("keymanage-backup-20261114.json", "keymanage-backup-2026.json", "notes.json").forEach { name ->
            files.createFileAtPath("$directory/$name", null, null)
        }

        removeStagedBackups(directory)

        assertFalse(files.fileExistsAtPath("$directory/keymanage-backup-20261114.json"))
        assertTrue(files.fileExistsAtPath("$directory/keymanage-backup-2026.json"))
        assertTrue(files.fileExistsAtPath("$directory/notes.json"))
        files.removeItemAtPath(directory, null)
    }
}
