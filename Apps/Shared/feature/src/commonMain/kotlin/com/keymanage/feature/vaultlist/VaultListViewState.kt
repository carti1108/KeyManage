package com.keymanage.feature.vaultlist

import com.keymanage.domain.generator.hasSequentialDigits
import com.keymanage.domain.generator.isWeak
import com.keymanage.domain.generator.scorePassword
import com.keymanage.domain.vault.VaultItem
import com.keymanage.feature.messages.ErrorMessage

enum class VaultListScreen { LIST, DETAIL, ADD, EDIT }

enum class VaultItemFormField { SERVICE_NAME, USERNAME, PASSWORD, URL, NOTES }

/** 추가·수정 폼에 입력 중인 값 */
data class VaultItemForm(
    val serviceName: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
) {
    fun withField(field: VaultItemFormField, value: String): VaultItemForm = when (field) {
        VaultItemFormField.SERVICE_NAME -> copy(serviceName = value)
        VaultItemFormField.USERNAME -> copy(username = value)
        VaultItemFormField.PASSWORD -> copy(password = value)
        VaultItemFormField.URL -> copy(url = value)
        VaultItemFormField.NOTES -> copy(notes = value)
    }

    companion object {
        fun of(item: VaultItem): VaultItemForm = VaultItemForm(
            serviceName = item.serviceName ?: "",
            username = item.username ?: "",
            password = item.password ?: "",
            url = item.url ?: "",
            notes = item.notes ?: "",
        )
    }
}

data class VaultListViewState(
    val screen: VaultListScreen = VaultListScreen.LIST,
    val query: String = "",
    /** 삭제되지 않은 항목 중 [query]에 걸리는 것을 서비스 이름순으로 */
    val items: List<VaultItem> = emptyList(),
    /** 저장된 항목이 하나도 없음. 목록이 비었을 때 "항목 없음"과 "검색 결과 없음"을 가른다 */
    val isVaultEmpty: Boolean = true,
    /** 새 버전 앱이 저장한 볼트. 추가·수정·삭제를 막는다 */
    val readOnly: Boolean = false,
    /** [VaultListScreen.DETAIL]·[VaultListScreen.EDIT]에서 보고 있는 항목 */
    val selected: VaultItem? = null,
    /** 목록으로 돌아올 때 포커스를 돌려줄 항목 */
    val focusItemId: String? = null,
    val form: VaultItemForm = VaultItemForm(),
    /** 폼 안에서 비밀번호 생성기를 열었음. 생성기 화면은 자식 RIB이 보인다 */
    val isGeneratorOpen: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    /** 저장·삭제 요청 중. 이동·취소·삭제를 막는다 */
    val isSaving: Boolean = false,
    val saveError: ErrorMessage? = null,
    val copyError: String? = null,
) {
    /** 항목 추가·수정·삭제를 보일지 */
    val canEdit: Boolean get() = !readOnly

    /** 목록이 비었을 때 보일 안내. 비어 있지 않으면 null */
    val emptyMessage: String?
        get() = if (items.isNotEmpty()) null else if (isVaultEmpty) VaultListMessages.EMPTY_VAULT else VaultListMessages.NO_SEARCH_RESULT

    /** 비밀번호를 입력해야 강도를 보인다 (GEN-001) */
    val showsFormStrength: Boolean get() = form.password.isNotEmpty()
    val formPasswordScore: Int get() = scorePassword(form.password)
    val showsFormWeakWarning: Boolean get() = showsFormStrength && isWeak(formPasswordScore)

    /** 점수와 별개로 안내만 한다. 점수가 높아도 보인다 (GEN-001) */
    val showsFormSequentialDigitsNotice: Boolean get() = hasSequentialDigits(form.password)
}

sealed interface VaultListViewAction {
    data class Search(val query: String) : VaultListViewAction
    data class OpenItem(val id: String) : VaultListViewAction
    /** 상세에서 목록으로 */
    data object Back : VaultListViewAction
    data object StartAdd : VaultListViewAction
    data object StartEdit : VaultListViewAction
    data class EditForm(val field: VaultItemFormField, val value: String) : VaultListViewAction
    data object SubmitForm : VaultListViewAction
    /** 추가 폼은 목록으로, 수정 폼은 상세로 돌아간다 */
    data object CancelForm : VaultListViewAction
    data object OpenGenerator : VaultListViewAction
    data object RequestDelete : VaultListViewAction
    data object CancelDelete : VaultListViewAction
    data object ConfirmDelete : VaultListViewAction
    data class CopyUsername(val itemId: String) : VaultListViewAction
    data class CopyPassword(val itemId: String) : VaultListViewAction
}
