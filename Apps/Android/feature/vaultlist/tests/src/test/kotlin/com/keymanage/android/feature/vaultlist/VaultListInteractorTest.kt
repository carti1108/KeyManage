package com.keymanage.android.feature.vaultlist

import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.domain.vault.VaultItem
import com.keymanage.android.domain.vault.VaultRules
import com.keymanage.android.feature.vaultlist.impl.VaultListInteractor
import com.keymanage.android.feature.vaultlist.impl.VaultListPresenter
import com.keymanage.android.feature.vaultlist.impl.VaultListViewAction
import com.keymanage.android.feature.vaultlist.impl.VaultListViewState
import com.keymanage.android.feature.vaultlist.impl.VaultRow
import com.keymanage.android.feature.vaultlist.testing.RecordingVaultListListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultListInteractorTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private fun item(id: String, service: String, username: String?) =
        VaultItem(id = id, serviceName = service, username = username, password = "secret", createdAt = t0, updatedAt = t0)

    private val vault = VaultData(
        1, t0,
        listOf(item("1", "Notion", "dev@example.com"), item("2", "GitHub", "octo"), VaultRules.tombstone(item("3", "AWS", "admin"), t0)),
    )
    private val listener = RecordingVaultListListener()
    private val store = ViewStateStore(VaultListViewState())
    private val interactor = VaultListInteractor(VaultListPresenter(store, ViewActionStream()), store, ViewActionStream(), vault, listener)

    @Test
    fun `삭제되지 않은 항목을 이름순으로 보이고 비밀번호는 상태에 담지 않는다`() {
        assertEquals(listOf(VaultRow("2", "GitHub", "octo"), VaultRow("1", "Notion", "dev@example.com")), store.current.rows)
        assertEquals(2, store.current.totalCount)
    }

    @Test
    fun `검색어로 목록을 좁힌다`() {
        interactor.handle(VaultListViewAction.QueryChanged("noti"))
        assertEquals(listOf("1"), store.current.rows.map { it.id })
        assertEquals(2, store.current.totalCount)
    }

    @Test
    fun `잠금과 동기화 요청은 부모에 넘긴다`() {
        interactor.handle(VaultListViewAction.Lock)
        interactor.handle(VaultListViewAction.Resync)
        assertEquals(1, listener.lockRequests)
        assertEquals(1, listener.resyncRequests)
    }
}
