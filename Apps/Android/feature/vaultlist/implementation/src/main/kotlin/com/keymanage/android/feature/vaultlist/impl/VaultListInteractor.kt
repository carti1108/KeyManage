package com.keymanage.android.feature.vaultlist.impl

import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.generator.GeneratorOptions
import com.keymanage.android.domain.generator.PasswordGenerator
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.sync.SaveOutcome
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.domain.vault.ItemFields
import com.keymanage.android.domain.vault.ItemFieldsProblem
import com.keymanage.android.domain.vault.ItemList
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultEditing
import com.keymanage.android.feature.vaultlist.VaultListListener
import com.keymanage.android.feature.vaultlist.VaultSaver
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** 볼트 목록·검색, 항목 상세·복사, 추가·수정·삭제(VLT-001, COM-001). */
class VaultListInteractor(
    presenter: VaultListPresenter,
    private val store: ViewStateStore<VaultListViewState>,
    private val actions: ViewActionStream<VaultListViewAction>,
    private var vault: VaultData,
    private val saver: VaultSaver,
    private val clipboard: ClipboardBurner,
    private val listener: VaultListListener,
    private val workDispatcher: CoroutineDispatcher,
    private val clock: () -> Instant,
    private val generator: PasswordGenerator = PasswordGenerator(),
) : BasicInteractor<VaultListPresenter, BasicComposeRouter<VaultListInteractor>>(presenter) {
    private var ticker: Job? = null

    /** 되돌림으로 막힌 저장. 사용자가 받아들이면 다시 한다(VLT-001 규칙 9). */
    private class RollbackRetry(val vault: VaultData, val serverRevision: Int, val onSaved: () -> Unit)

    private var rollbackRetry: RollbackRetry? = null

    init {
        store.update { it.copy(isReadOnly = vault.isReadOnly) }
        refreshRows()
    }

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    fun handle(action: VaultListViewAction) {
        when (action) {
            is VaultListViewAction.QueryChanged -> {
                store.update { it.copy(query = action.value) }
                refreshRows()
            }
            VaultListViewAction.Lock -> listener.onLockRequested()
            VaultListViewAction.Resync -> listener.onResyncRequested()
            is VaultListViewAction.OpenItem -> openDetail(action.id)
            VaultListViewAction.Back -> back()
            VaultListViewAction.CopyUsername -> copy { it.username }
            VaultListViewAction.CopyPassword -> copy { it.password }
            is VaultListViewAction.CopyItemPassword -> copyItemPassword(action.id)
            VaultListViewAction.StartAdd -> if (!vault.isReadOnly) show(VaultScreen.Edit(null, ItemFields("", "", "")))
            VaultListViewAction.StartEdit -> startEdit()
            is VaultListViewAction.FormChanged -> updateForm(action.fields)
            VaultListViewAction.GeneratePassword -> generatePassword()
            VaultListViewAction.Save -> coroutineScope.launch { save() }
            VaultListViewAction.RequestDelete -> if (!vault.isReadOnly) store.update { it.copy(isConfirmingDelete = true) }
            VaultListViewAction.ConfirmDelete -> coroutineScope.launch { delete() }
            VaultListViewAction.CancelDelete -> store.update { it.copy(isConfirmingDelete = false) }
            VaultListViewAction.DismissError -> {
                rollbackRetry = null
                store.update { it.copy(errorMessage = null, errorTitle = null, canAcceptRollback = false) }
            }
            VaultListViewAction.AcceptRollback -> coroutineScope.launch { acceptRollback() }
        }
    }

    /** 되돌림 경고에서 "서버 볼트 받아들이기"를 눌렀다. 받아들인 뒤 막혔던 저장을 다시 한다. 테스트는 직접 부른다. */
    suspend fun acceptRollback() {
        val retry = rollbackRetry ?: return
        if (store.current.isSaving) return
        rollbackRetry = null
        store.update { it.copy(isSaving = true, canAcceptRollback = false) }
        try {
            withContext(workDispatcher) { saver.acceptRollback(retry.serverRevision) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.update { it.copy(isSaving = false, errorMessage = VaultMessages.UNKNOWN, errorTitle = null) }
            return
        }
        persist(retry.vault, retry.onSaved)
    }

    /** 폼을 저장한다. 서비스 이름이 비었거나 바뀐 것이 없으면 서버에 보내지 않는다. 테스트는 직접 부른다. */
    suspend fun save() {
        val screen = store.current.screen as? VaultScreen.Edit ?: return
        if (store.current.isSaving) return
        if (VaultEditing.check(screen.fields) == ItemFieldsProblem.MISSING_SERVICE_NAME) {
            store.update { it.copy(errorMessage = VaultMessages.MISSING_SERVICE_NAME) }
            return
        }
        val id = screen.id
        val now = clock()
        val (next, savedId) = if (id == null) {
            val added = VaultEditing.add(vault, screen.fields, now)
            added to added.items.last().id
        } else {
            val updated = VaultEditing.update(vault, id, screen.fields, now) ?: return openDetail(id)
            updated to id
        }
        persist(next) { openDetail(savedId) }
    }

    /** 삭제 확인 뒤 지운다. 테스트는 직접 부른다. */
    suspend fun delete() {
        val screen = store.current.screen as? VaultScreen.Detail ?: return
        store.update { it.copy(isConfirmingDelete = false) }
        persist(VaultEditing.delete(vault, screen.id, clock())) { show(VaultScreen.List) }
    }

    /** 저장하고 성공하면 [onSaved]로 화면을 옮긴다. 되돌림으로 막히면 받아들인 뒤 다시 할 수 있게 남겨 둔다. */
    private suspend fun persist(next: VaultData, onSaved: () -> Unit) {
        rollbackRetry = null
        store.update { it.copy(isSaving = true, errorMessage = null, errorTitle = null, canAcceptRollback = false) }
        val saved = try {
            withContext(workDispatcher) { saver.save(next) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val rollback = error as? SyncError.RevisionRollback
            rollbackRetry = rollback?.let { RollbackRetry(next, it.serverRevision, onSaved) }
            store.update {
                it.copy(isSaving = false, errorMessage = messageOf(error), errorTitle = titleOf(error), canAcceptRollback = rollback != null)
            }
            if (error is ApiError.Server && error.status == UNAUTHORIZED) listener.onResyncRequested()
            return
        }
        vault = saved.vault
        store.update { it.copy(isSaving = false, isPendingSync = saved.outcome == SaveOutcome.Pending) }
        refreshRows()
        onSaved()
    }

    private fun openDetail(id: String) {
        val item = vault.visibleItems.firstOrNull { it.id == id } ?: return show(VaultScreen.List)
        show(VaultScreen.Detail(id, ItemFields.of(item)))
    }

    private fun startEdit() {
        val detail = store.current.screen as? VaultScreen.Detail ?: return
        if (!vault.isReadOnly) show(VaultScreen.Edit(detail.id, detail.fields))
    }

    private fun updateForm(fields: ItemFields) {
        val screen = store.current.screen as? VaultScreen.Edit ?: return
        store.update { it.copy(screen = screen.copy(fields = fields), errorMessage = null) }
    }

    /** 기본 옵션(16자, 문자군 4종)으로 만든 비밀번호를 폼에 넣는다(GEN-001). */
    private fun generatePassword() {
        val screen = store.current.screen as? VaultScreen.Edit ?: return
        updateForm(screen.fields.copy(password = generator.generate(GeneratorOptions())))
    }

    private fun back() {
        when (val screen = store.current.screen) {
            is VaultScreen.Edit -> if (screen.id != null) openDetail(screen.id) else show(VaultScreen.List)
            is VaultScreen.Detail -> show(VaultScreen.List)
            VaultScreen.List -> Unit
        }
    }

    private fun show(screen: VaultScreen) =
        store.update { it.copy(screen = screen, isConfirmingDelete = false, errorMessage = null) }

    private fun copy(select: (ItemFields) -> String) {
        val detail = store.current.screen as? VaultScreen.Detail ?: return
        copyToClipboard(select(detail.fields))
    }

    private fun copyItemPassword(id: String) {
        val item = vault.visibleItems.firstOrNull { it.id == id } ?: return
        copyToClipboard(ItemFields.of(item).password)
    }

    /** 복사하고 30초 뒤 비우는 타이머를 시작한다(COM-001). */
    private fun copyToClipboard(value: String) {
        try {
            clipboard.copy(value)
        } catch (error: Exception) {
            store.update { it.copy(errorMessage = VaultMessages.COPY_FAILED) }
            return
        }
        store.update { it.copy(burnSeconds = clipboard.remainingSeconds()) }
        startTicker()
    }

    /** 배지의 남은 초를 1초마다 고친다. 밖에서 복사해 타이머가 멈추면 배지도 사라진다. */
    private fun startTicker() {
        if (!isAttached()) return
        ticker?.cancel()
        ticker = coroutineScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                val seconds = clipboard.remainingSeconds()
                store.update { it.copy(burnSeconds = seconds) }
                if (seconds == null) break
            }
        }
    }

    private fun refreshRows() {
        val query = store.current.query
        val rows = ItemList.list(vault, query).map { VaultRow(it.id, it.serviceName.orEmpty(), it.username) }
        store.update { it.copy(rows = rows, totalCount = vault.visibleItems.size) }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val UNAUTHORIZED = 401

        fun titleOf(error: Exception): String? =
            if (error is SyncError.RevisionRollback) VaultMessages.ROLLBACK_TITLE else null

        fun messageOf(error: Exception): String = when (error) {
            is SyncError.VaultTooLarge -> VaultMessages.TOO_LARGE
            is SyncError.ConflictRetriesExhausted -> VaultMessages.CONFLICT
            is SyncError.RevisionRollback -> VaultMessages.ROLLBACK
            is SyncError.ReadOnlyVault -> VaultMessages.READ_ONLY
            is ApiError.Server -> if (error.status == UNAUTHORIZED) VaultMessages.SESSION_EXPIRED else VaultMessages.UNKNOWN
            else -> VaultMessages.UNKNOWN
        }
    }
}
