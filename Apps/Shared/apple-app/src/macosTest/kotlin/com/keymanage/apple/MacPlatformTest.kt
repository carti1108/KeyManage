package com.keymanage.apple

import kotlinx.coroutines.test.runTest
import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacPlatformTest {
    @Test
    fun pasteboardWriterWritesAndEmptyTextClearsContents() = runTest {
        val pasteboard = NSPasteboard.pasteboardWithUniqueName()
        val writer = NSPasteboardClipboardWriter(pasteboard)

        writer.write("secret")
        assertEquals("secret", pasteboard.stringForType(NSPasteboardTypeString))
        writer.write("")

        assertNull(pasteboard.stringForType(NSPasteboardTypeString))
        pasteboard.releaseGlobally()
    }

    @Test
    fun backupFilesAreStagedInTemporaryDirectoryNotDocuments() {
        // macOS 문서 폴더는 사용자 폴더라 저장 패널로 고르기 전에 파일을 남기지 않는다
        assertTrue(backupStagingDirectory().startsWith(NSTemporaryDirectory().trimEnd('/')))
    }

    @Test
    fun systemClipboardWriterUsesGeneralPasteboard() {
        assertTrue(systemClipboardWriter() is NSPasteboardClipboardWriter)
    }
}
