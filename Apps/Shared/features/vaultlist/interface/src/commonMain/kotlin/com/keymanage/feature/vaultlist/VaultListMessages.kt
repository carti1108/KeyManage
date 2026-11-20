package com.keymanage.feature.vaultlist

/** 볼트 화면 문구. 버튼 글자 같은 라벨은 뷰가, 상태에 따라 바뀌는 안내는 여기서 정한다 */
object VaultListMessages {
    const val EMPTY_VAULT: String = "저장된 항목이 없습니다."
    const val NO_SEARCH_RESULT: String = "검색 결과가 없습니다."
    const val SERVICE_NAME_REQUIRED: String = "서비스 이름을 입력해 주세요."
    const val COPY_FAILED: String = "클립보드에 복사하지 못했습니다."

    /** 약한 비밀번호도 저장은 허용한다. 사이트마다 규칙이 달라 사용자가 고를 수 없는 경우가 있다 (GEN-001) */
    const val WEAK_PASSWORD_TITLE: String = "취약한 비밀번호 경고"
    const val WEAK_PASSWORD_BODY: String = "현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요."
    const val SEQUENTIAL_DIGITS_NOTICE: String = "123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다."

    object DeleteConfirm {
        const val TITLE: String = "항목 삭제"
        const val BODY: String = "이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다."
        const val CONFIRM: String = "삭제"
        const val CANCEL: String = "취소"
    }
}
