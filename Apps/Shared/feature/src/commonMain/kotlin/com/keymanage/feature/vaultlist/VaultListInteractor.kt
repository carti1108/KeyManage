package com.keymanage.feature.vaultlist

import com.keymanage.feature.messages.ClipboardCopyNotice
import com.keymanage.feature.messages.ClipboardMessages
import com.carti.ribs.PresentableInteractor
import com.carti.ribs.checkMainThread
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultItem
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.checkItemFields
import com.keymanage.domain.vault.deleteItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.domain.vault.itemChanges
import com.keymanage.domain.vault.listItems
import com.keymanage.domain.vault.updateItem
import com.keymanage.feature.ClipboardPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.generator.PasswordGeneratorListener
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.describeErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// 열린 볼트의 목록·검색·상세·추가·수정·삭제·복사 (VLT-001, COM-001).
// 원본: Apps/Web/src/ui/vault/VaultScreen.tsx, ItemList.tsx, ItemDetail.tsx, ItemForm.tsx
// 저장은 SessionPort.save에 새 볼트를 넘기는 것으로 끝난다. 바뀐 볼트는 세션이 알려 준다(원격 변경·동기화)

class VaultListInteractor(
    presenter: VaultListPresentable,
    private val session: SessionPort,
    private val clipboard: ClipboardPort,
    private val clock: Clock,
    private val listener: VaultListListener,
) : PresentableInteractor<VaultListPresentable>(presenter), VaultListPresentableListener, PasswordGeneratorListener {
    var router: VaultListRouter? = null

    private var data: VaultData = emptyVault(clock.now())
    private var readOnly = false
    private var query = ""
    private var screen = VaultListScreen.LIST
    private var selectedId: String? = null
    private var focusItemId: String? = null
    private var form = VaultItemForm()
    private var isGeneratorOpen = false
    private var isConfirmingDelete = false
    private var isSaving = false
    private var saveError: ErrorMessage? = null
    private var copyError: String? = null
    private var copyNotice: ClipboardCopyNotice? = null
    private var unsubscribers: List<() -> Unit> = emptyList()

    override fun didBecomeActive() {
        presenter.listener = this
        // 다른 기기·탭의 변경과 동기화 결과로 볼트가 바뀐다. 콜백은 어느 스레드에서 올지 모르니 메인 스코프에서 처리한다
        // 내려간 뒤 늦게 온 알림은 무시한다. 세션은 알림을 복사한 목록으로 돌아 해지한 콜백도 한 번 더 불릴 수 있고,
        // 이때 비활성 스코프에 접근하면 던져서 세션의 알림 반복이 깨진다
        unsubscribers = listOf(
            session.onRemoteChange { if (isActive) coroutineScope.launch { reloadFromSession() } },
            session.onSyncChange { if (isActive) coroutineScope.launch { reloadFromSession() } },
        )
        syncFromSession()
        render()
    }

    override fun willResignActive() {
        presenter.listener = null
        unsubscribers.forEach { it() }
        unsubscribers = emptyList()
        clearPlaintext()
    }

    /** 내려가기 전에 볼트와 폼의 평문을 비우고 빈 화면을 그려, 뷰가 마지막으로 받은 상태에 비밀이 남지 않게 한다 */
    private fun clearPlaintext() {
        closeGenerator()
        data = emptyVault(clock.now())
        screen = VaultListScreen.LIST
        query = ""
        selectedId = null
        focusItemId = null
        form = VaultItemForm()
        isConfirmingDelete = false
        saveError = null
        copyError = null
        copyNotice = null
        render()
    }

    override fun handle(action: VaultListViewAction) {
        // 비활성 뒤 늦게 온 입력은 무시한다(옛 Presenter가 onAction을 비워 막던 것)
        if (!isActive) return
        when (action) {
            is VaultListViewAction.Search -> query = action.query
            is VaultListViewAction.OpenItem -> if (!isSaving) showDetail(action.id)
            VaultListViewAction.Back -> if (!isSaving && screen == VaultListScreen.DETAIL) navigate(VaultListScreen.LIST)
            VaultListViewAction.StartAdd -> if (!isSaving && !readOnly) navigate(VaultListScreen.ADD)
            VaultListViewAction.StartEdit -> startEdit()
            is VaultListViewAction.EditForm -> if (isFormScreen()) form = form.withField(action.field, action.value)
            VaultListViewAction.SubmitForm -> submit()
            VaultListViewAction.CancelForm -> cancelForm()
            VaultListViewAction.OpenGenerator -> openGenerator()
            VaultListViewAction.RequestDelete ->
                if (!isSaving && !readOnly && screen == VaultListScreen.DETAIL) isConfirmingDelete = true
            VaultListViewAction.CancelDelete -> if (!isSaving) isConfirmingDelete = false
            VaultListViewAction.ConfirmDelete -> confirmDelete()
            is VaultListViewAction.CopyUsername -> copy(action.itemId) { it.username }
            is VaultListViewAction.CopyPassword -> copy(action.itemId) { it.password }
        }
        render()
    }

    // --- 폼 안의 비밀번호 생성기 (자식 RIB) ---

    override fun didUsePassword(password: String) {
        form = form.copy(password = password)
        closeGenerator()
        render()
    }

    override fun didClosePasswordGenerator() {
        closeGenerator()
        render()
    }

    private fun openGenerator() {
        if (!isFormScreen() || isGeneratorOpen) return
        isGeneratorOpen = true
        router?.routeToGenerator()
    }

    private fun closeGenerator() {
        if (!isGeneratorOpen) return
        isGeneratorOpen = false
        router?.routeAwayFromGenerator()
    }

    // --- 이동 ---

    private fun isFormScreen() = screen == VaultListScreen.ADD || screen == VaultListScreen.EDIT

    private fun activeItem(id: String?): VaultItem? = id?.let { wanted -> activeItems(data).firstOrNull { it.id == wanted } }

    /** 화면을 바꾼다. 새 화면은 확인 창·오류·열어 둔 생성기 없이 시작한다 */
    private fun navigate(next: VaultListScreen, itemId: String? = null, nextForm: VaultItemForm = VaultItemForm()) {
        closeGenerator()
        screen = next
        selectedId = itemId
        form = nextForm
        isConfirmingDelete = false
        saveError = null
    }

    private fun showDetail(id: String) {
        if (activeItem(id) == null) return
        focusItemId = id
        navigate(VaultListScreen.DETAIL, id)
    }

    private fun startEdit() {
        val item = activeItem(selectedId)
        if (isSaving || readOnly || screen != VaultListScreen.DETAIL || item == null) return
        navigate(VaultListScreen.EDIT, item.id, VaultItemForm.of(item))
    }

    private fun cancelForm() {
        if (isSaving) return
        when (screen) {
            VaultListScreen.ADD -> navigate(VaultListScreen.LIST)
            VaultListScreen.EDIT -> navigate(VaultListScreen.DETAIL, selectedId)
            VaultListScreen.LIST, VaultListScreen.DETAIL -> Unit
        }
    }

    // --- 세션의 볼트 ---

    /**
     * 세션이 알림 없이 볼트를 바꿨을 수 있을 때(백업 복원·재연결·거절된 저장) 부모가 부른다. 입력 중인 폼은 건드리지 않는다.
     * 내려간 뒤에는 아무것도 하지 않는다
     */
    fun reloadFromSession() {
        if (!isActive) return
        if (!syncFromSession()) return
        render()
    }

    /** 잠겨 있으면 마지막으로 본 볼트를 그대로 둔다. 잠금 화면은 부모가 보인다 */
    private fun syncFromSession(): Boolean {
        val unlocked = session.state() as? SessionState.Unlocked ?: return false
        data = unlocked.vault.data
        readOnly = unlocked.vault.readOnly
        // 다른 기기에서 지워졌거나 동기화로 사라진 항목을 보고 있었다면 목록으로 돌아간다
        val viewingItem = screen == VaultListScreen.DETAIL || screen == VaultListScreen.EDIT
        if (viewingItem && activeItem(selectedId) == null) navigate(VaultListScreen.LIST)
        return true
    }

    // --- 저장 ---

    /**
     * 볼트 저장을 한 번에 하나만 보내고 실패는 정책 문구로 바꾼다 (웹 useSave).
     * [build]가 저장 직전 세션의 볼트를 받아 새 볼트를 만들고, 성공하면 [onSaved], 실패하면 [onFailed]를 부른다.
     * 복원·재연결·거절된 저장은 알림 없이 세션의 볼트를 바꾸므로, 캐시한 볼트가 아니라 세션의 현재 볼트를 기준으로 한다
     */
    private fun save(build: (VaultData) -> VaultData, onSaved: () -> Unit, onFailed: () -> Unit = {}) {
        if (isSaving) return
        isSaving = true
        saveError = null
        coroutineScope.launch {
            val failure = try {
                val next = build((session.state() as? SessionState.Unlocked)?.vault?.data ?: data)
                session.save(next)
                data = next
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error
            }
            // 거절돼도 변경 사항이 먼저 반영돼 있을 수 있으니 성공과 실패 모두 세션의 볼트를 다시 읽는다 (웹 useSave의 finally)
            syncFromSession()
            isSaving = false
            if (failure == null) {
                onSaved()
                listener.vaultListDidSave()
            } else {
                saveError = describeErrorMessage(failure)
                onFailed()
            }
            render()
        }
    }

    private fun submit() {
        if (isSaving || readOnly || !isFormScreen()) return
        val fields = ItemFields(form.serviceName.trim(), form.username, form.password, form.url, form.notes)
        if (checkItemFields(fields) != null) {
            saveError = ErrorMessage(body = VaultListMessages.SERVICE_NAME_REQUIRED)
            return
        }
        val editing = if (screen == VaultListScreen.EDIT) activeItem(selectedId) ?: return else null
        if (editing == null) submitNew(fields) else submitEdit(editing, fields)
    }

    private fun submitNew(fields: ItemFields) {
        val id = randomItemId()
        // 새 항목에는 비어 있는 선택 필드를 넣지 않는다
        val added = fields.copy(url = fields.url?.ifEmpty { null }, notes = fields.notes?.ifEmpty { null })
        save(build = { base -> addItem(base, added, clock.now(), id) }, onSaved = { showDetail(id) })
    }

    private fun submitEdit(item: VaultItem, fields: ItemFields) {
        val changes = itemChanges(item, fields)
        if (changes == null) {
            // 바뀐 것이 없으면 저장하지 않는다. 다른 기기가 그 사이 고친 항목을 덮어쓰지 않게 하기 위해서다
            navigate(VaultListScreen.DETAIL, item.id)
            return
        }
        save(build = { base -> updateItem(base, item.id, changes, clock.now()) }, onSaved = { navigate(VaultListScreen.DETAIL, item.id) })
    }

    private fun confirmDelete() {
        val id = selectedId
        if (isSaving || readOnly || screen != VaultListScreen.DETAIL || !isConfirmingDelete || id == null) return
        save(
            build = { base -> deleteItem(base, id, clock.now()) },
            onSaved = {
                // 삭제하는 사이 다른 항목으로 옮겼다면 그 화면을 둔다
                if (screen == VaultListScreen.DETAIL && selectedId == id) navigate(VaultListScreen.LIST)
                isConfirmingDelete = false
            },
            onFailed = { isConfirmingDelete = false },
        )
    }

    // --- 복사 (COM-001) ---

    private fun copy(itemId: String, pick: (VaultItem) -> String?) {
        val text = activeItem(itemId)?.let(pick)?.takeIf { it.isNotEmpty() } ?: return
        copyError = null
        copyNotice = null
        coroutineScope.launch {
            try {
                clipboard.copy(text)
                copyNotice = ClipboardMessages.COPIED
                render()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 탭이 활성 상태가 아니거나 권한이 없으면 플랫폼이 거절한다
                copyError = VaultListMessages.COPY_FAILED
                render()
            }
        }
    }

    private fun render() {
        checkMainThread("VaultListPresentable.render")
        presenter.render(
            VaultListViewState(
                screen = screen,
                query = query,
                items = listItems(data, query),
                isVaultEmpty = activeItems(data).isEmpty(),
                readOnly = readOnly,
                selected = activeItem(selectedId),
                focusItemId = focusItemId,
                form = form,
                isGeneratorOpen = isGeneratorOpen,
                isConfirmingDelete = isConfirmingDelete,
                isSaving = isSaving,
                saveError = saveError,
                copyError = copyError,
                copyNotice = copyNotice,
            ),
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomItemId(): String = Uuid.random().toString()
