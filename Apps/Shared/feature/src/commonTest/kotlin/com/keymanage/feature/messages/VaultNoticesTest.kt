package com.keymanage.feature.messages

import kotlin.test.Test
import kotlin.test.assertEquals

// iOS·macOS 화면이 그대로 그리는 VLT-002·COM-001 문구. 옛 Swift PendingSyncEngineTests·ClipboardManagerTests의 문구 검사를 옮겼다
class VaultNoticesTest {
    @Test
    fun pendingLogoutConfirmAndOfflineNoticeUseVlt002Text() {
        assertEquals("동기화되지 않은 변경 사항", PendingLogoutConfirm.TITLE)
        assertEquals("로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.", PendingLogoutConfirm.BODY)
        assertEquals("오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.", OFFLINE_NOTICE)
    }

    @Test
    fun clipboardNoticeUsesCom001Text() {
        assertEquals(ClipboardCopyNotice("클립보드 보안 소각", "보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다."), ClipboardMessages.COPIED)
        assertEquals("클립보드에 복사하지 못했습니다.", ClipboardMessages.COPY_FAILED)
    }
}
