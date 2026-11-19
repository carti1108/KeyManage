package com.keymanage.android.feature.vaultlist.impl

import com.keymanage.android.domain.vault.ItemFields

/** 목록 한 줄. 비밀번호·메모는 목록 상태에 담지 않는다. */
data class VaultRow(val id: String, val serviceName: String, val username: String?)

/** 볼트 RIB 안의 화면. */
sealed interface VaultScreen {
    data object List : VaultScreen

    /** 항목 상세. 비밀번호는 이 화면을 보는 동안만 상태에 둔다. */
    data class Detail(val id: String, val fields: ItemFields) : VaultScreen

    /** 추가([id]가 null)·수정 폼. */
    data class Edit(val id: String?, val fields: ItemFields) : VaultScreen
}

/** 볼트 목록·상세·편집 상태. */
data class VaultListViewState(
    val query: String = "",
    val rows: List<VaultRow> = emptyList(),
    val totalCount: Int = 0,
    val isOffline: Boolean = false,
    val isReadOnly: Boolean = false,
    val screen: VaultScreen = VaultScreen.List,
    val isSaving: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    val errorMessage: String? = null,
    /** [errorMessage]의 제목. 정책 문구 표에 제목이 있는 오류만 채운다. */
    val errorTitle: String? = null,
    /** 마지막 저장이 서버에 올라가지 못하고 대기 중이다(VLT-002). */
    val isPendingSync: Boolean = false,
    /** 클립보드 자동 삭제까지 남은 초. 기다리는 값이 없으면 null(COM-001). */
    val burnSeconds: Int? = null,
)

sealed interface VaultListViewAction {
    data class QueryChanged(val value: String) : VaultListViewAction
    data object Lock : VaultListViewAction
    data object Resync : VaultListViewAction
    data class OpenItem(val id: String) : VaultListViewAction
    data object Back : VaultListViewAction
    data object CopyUsername : VaultListViewAction
    data object CopyPassword : VaultListViewAction
    data object StartAdd : VaultListViewAction
    data object StartEdit : VaultListViewAction
    data class FormChanged(val fields: ItemFields) : VaultListViewAction
    data object GeneratePassword : VaultListViewAction
    data object Save : VaultListViewAction
    data object RequestDelete : VaultListViewAction
    data object ConfirmDelete : VaultListViewAction
    data object CancelDelete : VaultListViewAction
    data object DismissError : VaultListViewAction
}

/** VLT-001·VLT-002·COM-001·COM-002·SEC-002·SEC-003 사용자 문구. */
object VaultMessages {
    const val RESYNC_NOTICE = "서버와 동기화하려면 마스터 비밀번호를 다시 입력해 주세요."
    const val MISSING_SERVICE_NAME = "서비스 이름을 입력해 주세요."
    const val DELETE_TITLE = "항목 삭제"
    const val DELETE_BODY = "이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다."
    const val TOO_LARGE = "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다."
    const val CONFLICT = "다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요."
    const val ROLLBACK_TITLE = "서버 볼트가 이전 상태로 돌아감"
    const val OFFLINE = "오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다."
    const val CLIPBOARD_TITLE = "클립보드 보안 소각"
    const val CLIPBOARD_BODY = "보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다."
    const val ROLLBACK = "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요."
    const val READ_ONLY = "새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다."
    const val SESSION_EXPIRED = "로그인이 만료되었습니다. 다시 로그인해 주세요."
    const val COPY_FAILED = "클립보드에 복사하지 못했습니다."
    const val SEQUENTIAL_DIGITS = "123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다."
    const val WEAK_PASSWORD = "현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요."
    const val UNKNOWN = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
}
