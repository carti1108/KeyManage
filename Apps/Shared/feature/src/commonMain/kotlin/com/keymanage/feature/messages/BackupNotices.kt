package com.keymanage.feature.messages

// 암호화 백업 내보내기·복원 문구. 정책 문구를 그대로 쓴다 (VLT-004)
// 원본: Apps/Web/src/ui/messages/backupNotices.ts

/** 내보내기 전 안내. 마스터 비밀번호를 다시 묻지 않는다 */
object ExportNotice {
    const val TITLE = "백업 내보내기"
    const val BODY = "이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다."
    const val CONFIRM = "내보내기"
    const val CANCEL = "취소"
}

/** 복원 전 안내(제목·본문·버튼은 정책 문구)와 비밀번호 입력을 한 대화상자로 보인다. 칸 이름·취소·진행 문구는 기본값 */
object RestoreDialog {
    const val TITLE = "백업에서 복원"
    const val BODY = "백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다."
    const val PASSWORD_LABEL = "백업 파일 비밀번호"
    const val SUBMIT = "복원"
    const val CANCEL = "취소"
    const val PROGRESS = "백업을 복원하는 중입니다…"
}

/** 비밀번호가 틀렸거나 파일이 손상됨. AES-GCM은 둘을 구분하지 못한다 */
const val BACKUP_OPEN_FAILED = "백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요."

/** 형식이 아니거나 5MB 초과 */
const val NOT_A_BACKUP = "KeyManage 백업 파일이 아닙니다."

const val NEWER_BACKUP = "더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요."

fun restoredMessage(count: Int): String = "백업에서 항목 ${count}개를 복원했습니다."
