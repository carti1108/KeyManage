package com.keymanage.feature.vaultlist

import com.carti.ribs.Builder
import com.carti.ribs.Component
import com.carti.ribs.Dependency
import com.carti.ribs.Interactor
import com.carti.ribs.RibState
import com.carti.ribs.ViewControllable
import com.carti.ribs.ViewPresenter
import com.carti.ribs.ViewRouter
import com.carti.ribs.checkMainThread
import com.keymanage.domain.generator.hasSequentialDigits
import com.keymanage.domain.generator.isWeak
import com.keymanage.domain.generator.scorePassword
import com.keymanage.domain.platform.RandomBytes
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
import com.keymanage.feature.generator.PasswordGeneratorBuilder
import com.keymanage.feature.generator.PasswordGeneratorDependency
import com.keymanage.feature.generator.PasswordGeneratorListener
import com.keymanage.feature.generator.PasswordGeneratorRouter
import com.keymanage.feature.generator.PasswordGeneratorViewFactory
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

interface VaultListPresentableListener {
    fun handle(action: VaultListViewAction)
}

interface VaultListPresentable : ViewControllable {
    var listener: VaultListPresentableListener?
    fun render(state: VaultListViewState)
    fun showGenerator(view: ViewControllable)
    fun hideGenerator(view: ViewControllable)
}

fun interface VaultListViewFactory {
    fun makeVaultListView(): VaultListPresentable
}

interface VaultListDependency : Dependency {
    val vaultListViewFactory: VaultListViewFactory
    val session: SessionPort
    val clipboard: ClipboardPort
    val clock: Clock
    /** 폼 안의 비밀번호 생성기(자식 RIB)가 쓴다 */
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val randomBytes: RandomBytes
}

/** 부모에게 올릴 이벤트는 아직 없다. 잠김·로그아웃은 세션이 알리고 부모(Main)가 처리한다 */
interface VaultListListener

class VaultListComponent(dependency: VaultListDependency) :
    Component<VaultListDependency>(dependency), PasswordGeneratorDependency {
    override val passwordGeneratorViewFactory: PasswordGeneratorViewFactory get() = dependency.passwordGeneratorViewFactory
    override val clipboard: ClipboardPort get() = dependency.clipboard
    override val randomBytes: RandomBytes get() = dependency.randomBytes
}

class VaultListBuilder(dependency: VaultListDependency) : Builder<VaultListDependency>(dependency) {
    fun build(listener: VaultListListener): VaultListRouter {
        val component = VaultListComponent(dependency)
        val presenter = VaultListPresenter(dependency.vaultListViewFactory.makeVaultListView())
        val interactor = VaultListInteractor(
            presenter = presenter,
            session = dependency.session,
            clipboard = dependency.clipboard,
            clock = dependency.clock,
            listener = listener,
        )
        return VaultListRouter(presenter.view, interactor, PasswordGeneratorBuilder(component))
    }
}

class VaultListPresenter(view: VaultListPresentable) :
    ViewPresenter<VaultListPresentable>(view), VaultListPresentableListener {
    var onAction: ((VaultListViewAction) -> Unit)? = null

    override fun didLoad() {
        view.listener = this
    }

    override fun willUnload() {
        view.listener = null
    }

    override fun handle(action: VaultListViewAction) {
        onAction?.invoke(action)
    }

    fun render(state: VaultListViewState) {
        checkMainThread("VaultListPresenter.render")
        view.render(state)
    }
}

class VaultListInteractor(
    presenter: VaultListPresenter,
    private val session: SessionPort,
    private val clipboard: ClipboardPort,
    private val clock: Clock,
    @Suppress("unused") private val listener: VaultListListener,
) : Interactor<VaultListPresenter, VaultListRouter>(presenter), PasswordGeneratorListener {
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
    private var unsubscribers: List<() -> Unit> = emptyList()

    override fun didBecomeActive(savedInstanceState: RibState?) {
        presenter.onAction = ::handle
        // 다른 기기·탭의 변경과 동기화 결과로 볼트가 바뀐다. 콜백은 어느 스레드에서 올지 모르니 메인 스코프에서 처리한다
        unsubscribers = listOf(
            session.onRemoteChange { coroutineScope.launch { refreshFromSession() } },
            session.onSyncChange { coroutineScope.launch { refreshFromSession() } },
        )
        syncFromSession()
        render()
    }

    override fun willResignActive() {
        presenter.onAction = null
        unsubscribers.forEach { it() }
        unsubscribers = emptyList()
    }

    private fun handle(action: VaultListViewAction) {
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
        router.routeToGenerator()
    }

    private fun closeGenerator() {
        if (!isGeneratorOpen) return
        isGeneratorOpen = false
        router.routeAwayFromGenerator()
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

    private fun refreshFromSession() {
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
     * [build]가 새 볼트를 만들고, 성공하면 [onSaved], 실패하면 [onFailed]를 부른다
     */
    private fun save(build: () -> VaultData, onSaved: () -> Unit, onFailed: () -> Unit = {}) {
        if (isSaving) return
        isSaving = true
        saveError = null
        coroutineScope.launch {
            val failure = try {
                val next = build()
                session.save(next)
                data = next
                syncFromSession()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error
            }
            isSaving = false
            if (failure == null) {
                onSaved()
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
        save(build = { addItem(data, added, clock.now(), id) }, onSaved = { showDetail(id) })
    }

    private fun submitEdit(item: VaultItem, fields: ItemFields) {
        val changes = itemChanges(item, fields)
        if (changes == null) {
            // 바뀐 것이 없으면 저장하지 않는다. 다른 기기가 그 사이 고친 항목을 덮어쓰지 않게 하기 위해서다
            navigate(VaultListScreen.DETAIL, item.id)
            return
        }
        save(build = { updateItem(data, item.id, changes, clock.now()) }, onSaved = { navigate(VaultListScreen.DETAIL, item.id) })
    }

    private fun confirmDelete() {
        val id = selectedId
        if (isSaving || readOnly || screen != VaultListScreen.DETAIL || !isConfirmingDelete || id == null) return
        save(
            build = { deleteItem(data, id, clock.now()) },
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
        coroutineScope.launch {
            try {
                clipboard.copy(text)
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
            ),
        )
    }
}

class VaultListRouter(
    view: VaultListPresentable,
    interactor: VaultListInteractor,
    private val generatorBuilder: PasswordGeneratorBuilder,
) : ViewRouter<VaultListPresentable, VaultListInteractor>(view, interactor) {
    private var generator: PasswordGeneratorRouter? = null

    /** 폼 안에서 쓰는 생성기라 사용·닫기만 두고 복사는 두지 않는다 */
    fun routeToGenerator() {
        if (generator != null) return
        val child = generatorBuilder.build(interactor, allowCopy = false)
        attachChild(child)
        view.showGenerator(child.view)
        generator = child
    }

    fun routeAwayFromGenerator() {
        val child = generator ?: return
        detachChild(child)
        view.hideGenerator(child.view)
        generator = null
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomItemId(): String = Uuid.random().toString()
