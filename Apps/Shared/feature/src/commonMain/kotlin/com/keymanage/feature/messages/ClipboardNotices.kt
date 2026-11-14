package com.keymanage.feature.messages

// 클립보드 자동 비우기 안내 (COM-001). 웹은 앱 전체 안내(Apps/Web/src/ui/clipboard/ClipboardNotice.tsx)로 같은 문구를 띄운다

/** 복사 직후 안내. 화면은 다음 복사 시도 전까지 보여 준다 */
data class ClipboardCopyNotice(val title: String, val body: String)

object ClipboardMessages {
    const val COPIED_TITLE: String = "클립보드 보안 소각"
    const val COPIED_BODY: String = "보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다."
    const val COPY_FAILED: String = "클립보드에 복사하지 못했습니다."
    val COPIED: ClipboardCopyNotice = ClipboardCopyNotice(COPIED_TITLE, COPIED_BODY)
}
