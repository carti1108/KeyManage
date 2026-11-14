package com.keymanage.apple

import kotlinx.coroutines.test.runTest
import platform.UIKit.UIPasteboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosPlatformTest {
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
