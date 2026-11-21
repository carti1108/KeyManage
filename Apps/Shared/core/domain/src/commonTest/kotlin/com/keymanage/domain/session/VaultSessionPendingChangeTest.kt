package com.keymanage.domain.session

import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.addItem
import kotlin.test.Test
import kotlin.test.assertEquals

// 저장이 대기로 바뀌거나 대기가 풀리면 화면이 상태를 다시 읽도록 알린다 (VLT-002 "오프라인 전환 시 배너").
// 네트워크는 연결돼 있고 서버만 닿지 않을 때도 Main의 오프라인 안내가 떠야 한다

private fun dataOf(session: VaultSession): VaultData =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

private suspend fun saveItem(session: VaultSession, id: String) {
    session.save(addItem(dataOf(session), fields.copy(serviceName = id), NOW, id))
}

class VaultSessionPendingChangeTest {
    @Test
    fun 서버에_닿지_못해_대기가_되면_알린다() = sessionTest {
        val ctx = unlockedWithItem()
        var changes = 0
        ctx.session.onPendingSyncChange { changes += 1 }
        ctx.vaultApi.offline = true

        saveItem(ctx.session, "b")

        assertEquals(1, changes)
    }

    @Test
    fun 바로_올라가면_알리지_않는다() = sessionTest {
        val ctx = unlockedWithItem()
        var changes = 0
        ctx.session.onPendingSyncChange { changes += 1 }

        saveItem(ctx.session, "b")

        assertEquals(0, changes)
    }

    @Test
    fun 이미_대기_중에_또_저장하면_다시_알리지_않는다() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.vaultApi.offline = true
        saveItem(ctx.session, "b")
        var changes = 0
        ctx.session.onPendingSyncChange { changes += 1 }

        saveItem(ctx.session, "c")

        assertEquals(0, changes)
    }

    @Test
    fun 재전송으로_대기가_풀리면_알린다() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.vaultApi.offline = true
        saveItem(ctx.session, "b")
        var changes = 0
        ctx.session.onPendingSyncChange { changes += 1 }
        ctx.vaultApi.offline = false

        ctx.session.retryPendingSync()

        assertEquals(1, changes)
    }

    @Test
    fun 해제하면_더_알리지_않는다() = sessionTest {
        val ctx = unlockedWithItem()
        var changes = 0
        val unsubscribe = ctx.session.onPendingSyncChange { changes += 1 }
        unsubscribe()
        ctx.vaultApi.offline = true

        saveItem(ctx.session, "b")

        assertEquals(0, changes)
    }
}
