@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.ViewControllable
import com.carti.ribs.web.ScreenSlot
import com.carti.ribs.web.ViewStore
import com.carti.ribs.web.WebViewControllable
import com.keymanage.domain.vault.VaultItem
import com.keymanage.feature.vaultlist.VaultItemForm
import com.keymanage.feature.vaultlist.VaultItemFormField
import com.keymanage.feature.vaultlist.VaultListPresentable
import com.keymanage.feature.vaultlist.VaultListViewContract
import com.keymanage.feature.vaultlist.VaultListPresentableListener
import com.keymanage.feature.vaultlist.VaultListScreen
import com.keymanage.feature.vaultlist.VaultListViewAction
import com.keymanage.feature.vaultlist.VaultListViewState

// 볼트 목록·상세·추가·수정 화면의 웹 뷰

/** 항목 하나. 없는 값은 빈 문자열이다 */
@JsExport
data class WebVaultItem(
    val id: String,
    val serviceName: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    /** ISO 8601 */
    val createdAt: String,
    val updatedAt: String,
)

private fun VaultItem.toWeb() = WebVaultItem(
    id = id,
    serviceName = serviceName ?: "",
    username = username ?: "",
    password = password ?: "",
    url = url ?: "",
    notes = notes ?: "",
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** 추가·수정 폼에 입력 중인 값 */
@JsExport
data class WebVaultItemForm(
    val serviceName: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
)

private fun VaultItemForm.toWeb() = WebVaultItemForm(serviceName, username, password, url, notes)

/** 볼트 목록 화면이 그리는 전부. [screen]은 'list' | 'detail' | 'add' | 'edit' */
@JsExport
class WebVaultListState(
    val screen: String,
    val query: String,
    /** 삭제되지 않은 항목 중 [query]에 걸리는 것을 서비스 이름순으로 */
    val items: Array<WebVaultItem>,
    val isVaultEmpty: Boolean,
    val readOnly: Boolean,
    /** 'detail'·'edit'에서 보고 있는 항목 */
    val selected: WebVaultItem?,
    /** 목록으로 돌아올 때 포커스를 돌려줄 항목 */
    val focusItemId: String?,
    val form: WebVaultItemForm,
    /** 폼 안에서 비밀번호 생성기를 열었음. 생성기 화면은 [WebVaultListView.generatorSlot]에 들어온다 */
    val isGeneratorOpen: Boolean,
    val isConfirmingDelete: Boolean,
    /** 저장·삭제 요청 중. 이동·취소·삭제를 막는다 */
    val isSaving: Boolean,
    val saveError: WebMessage?,
    val copyError: String?,
    /** 항목 추가·수정·삭제를 보일지 */
    val canEdit: Boolean,
    /** 목록이 비었을 때 보일 안내. 비어 있지 않으면 null */
    val emptyMessage: String?,
    val showsFormStrength: Boolean,
    /** 0~100 */
    val formPasswordScore: Int,
    val showsFormWeakWarning: Boolean,
    val showsFormSequentialDigitsNotice: Boolean,
    /** [isConfirmingDelete]일 때 그리는 확인 창 문구 */
    val deleteConfirm: WebConfirmText,
    /** [showsFormWeakWarning]일 때 보이는 경고. 저장은 허용한다 (GEN-001) */
    val weakPasswordWarning: WebMessage,
    /** [showsFormSequentialDigitsNotice]일 때 보이는 안내 (GEN-001) */
    val sequentialDigitsNotice: String,
)

private fun VaultListScreen.wire(): String = when (this) {
    VaultListScreen.LIST -> "list"
    VaultListScreen.DETAIL -> "detail"
    VaultListScreen.ADD -> "add"
    VaultListScreen.EDIT -> "edit"
}

private fun VaultListViewState.toWeb() = WebVaultListState(
    screen = screen.wire(),
    query = query,
    items = items.map { it.toWeb() }.toTypedArray(),
    isVaultEmpty = isVaultEmpty,
    readOnly = readOnly,
    selected = selected?.toWeb(),
    focusItemId = focusItemId,
    form = form.toWeb(),
    isGeneratorOpen = isGeneratorOpen,
    isConfirmingDelete = isConfirmingDelete,
    isSaving = isSaving,
    saveError = saveError?.toWeb(),
    copyError = copyError,
    canEdit = canEdit,
    emptyMessage = emptyMessage,
    showsFormStrength = showsFormStrength,
    formPasswordScore = formPasswordScore,
    showsFormWeakWarning = showsFormWeakWarning,
    showsFormSequentialDigitsNotice = showsFormSequentialDigitsNotice,
    deleteConfirm = DELETE_CONFIRM_TEXT,
    weakPasswordWarning = WEAK_ITEM_PASSWORD_TEXT,
    sequentialDigitsNotice = SEQUENTIAL_DIGITS_TEXT,
)

private fun formFieldOf(wire: String): VaultItemFormField = when (wire) {
    "serviceName" -> VaultItemFormField.SERVICE_NAME
    "username" -> VaultItemFormField.USERNAME
    "password" -> VaultItemFormField.PASSWORD
    "url" -> VaultItemFormField.URL
    "notes" -> VaultItemFormField.NOTES
    else -> throw IllegalArgumentException("알 수 없는 폼 칸: $wire")
}

@JsExport
class WebVaultListView : VaultListViewContract, WebViewControllable {
    override val screenKey: String = "vaultList"
    val store: ViewStore<WebVaultListState> = ViewStore(VaultListViewState().toWeb())

    /** 폼 안에서 연 비밀번호 생성기가 들어온다 */
    val generatorSlot: ScreenSlot = ScreenSlot()

    @JsExport.Ignore
    override var listener: VaultListPresentableListener? = null

    // items는 배열이라 상태 객체끼리 값 비교가 안 된다. 원본 상태가 같으면 다시 그리지 않는다
    private var lastRendered: VaultListViewState? = null

    fun search(query: String) = send(VaultListViewAction.Search(query))
    fun openItem(id: String) = send(VaultListViewAction.OpenItem(id))

    /** 상세에서 목록으로 */
    fun back() = send(VaultListViewAction.Back)
    fun startAdd() = send(VaultListViewAction.StartAdd)
    fun startEdit() = send(VaultListViewAction.StartEdit)

    /** @param field 'serviceName' | 'username' | 'password' | 'url' | 'notes' */
    fun editForm(field: String, value: String) = send(VaultListViewAction.EditForm(formFieldOf(field), value))
    fun submitForm() = send(VaultListViewAction.SubmitForm)

    /** 추가 폼은 목록으로, 수정 폼은 상세로 돌아간다 */
    fun cancelForm() = send(VaultListViewAction.CancelForm)
    fun openGenerator() = send(VaultListViewAction.OpenGenerator)
    fun requestDelete() = send(VaultListViewAction.RequestDelete)
    fun cancelDelete() = send(VaultListViewAction.CancelDelete)
    fun confirmDelete() = send(VaultListViewAction.ConfirmDelete)
    fun copyUsername(itemId: String) = send(VaultListViewAction.CopyUsername(itemId))
    fun copyPassword(itemId: String) = send(VaultListViewAction.CopyPassword(itemId))

    private fun send(action: VaultListViewAction) {
        listener?.handle(action)
    }

    @JsExport.Ignore
    override fun render(state: VaultListViewState) {
        if (state == lastRendered) return
        lastRendered = state
        store.set(state.toWeb())
    }

    @JsExport.Ignore
    override fun showGenerator(view: ViewControllable) = generatorSlot.show(view.asWeb())

    @JsExport.Ignore
    override fun hideGenerator(view: ViewControllable) = generatorSlot.hide(view.asWeb())
}
