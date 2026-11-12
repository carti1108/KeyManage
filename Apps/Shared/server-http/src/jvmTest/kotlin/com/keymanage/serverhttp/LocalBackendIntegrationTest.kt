package com.keymanage.serverhttp

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.VaultSession
import com.keymanage.domain.session.VaultSessionDeps
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// 로컬 백엔드(Back/)에 실제 HTTP로 가입 → 저장 → 잠금 → 잠금 해제를 한 번 돌린다.
// KEYMANAGE_IT_BASE_URL(예: http://localhost:18080)이 있을 때만 실행하고, 없으면 건너뛴다(CI·평소 실행에는 서버가 없다)
class LocalBackendIntegrationTest {
    private class MemoryDeviceStore : DeviceRecordStore {
        var stored: String? = null

        override suspend fun load(): String? = stored

        override suspend fun save(recordJson: String) {
            stored = recordJson
        }

        override suspend fun clear() {
            stored = null
        }
    }

    @Test
    fun registerSaveLockUnlockAgainstLocalBackend() = runTest(timeout = 5.minutes) {
        val baseUrl = System.getenv("KEYMANAGE_IT_BASE_URL") ?: return@runTest
        val client = ApiClient(resolveBaseUrl(baseUrl), keyManageHttpClient())
        val session = VaultSession(
            VaultSessionDeps(
                auth = AuthService(HttpAuthApi(client)),
                vaultSyncFor = { token, key, memory -> VaultSync(HttpVaultApi(client, token), key, Clock.System, memory) },
                revisionMemory = RevisionMemory(),
                deviceStore = MemoryDeviceStore(),
                tabs = SingleTab(),
                scope = backgroundScope,
            ),
        )
        val email = "kmp-it-${Clock.System.now().toEpochMilliseconds()}@example.com"
        val password = "correct horse battery staple"

        session.register(email, password)
        val opened = assertIs<SessionState.Unlocked>(session.state())
        session.save(addItem(opened.vault.data, ItemFields("GitHub", "octocat", "pw-1"), Clock.System.now(), "it-item"))
        assertEquals(false, assertIs<SessionState.Unlocked>(session.state()).pendingSync)

        session.lock()
        assertIs<SessionState.Locked>(session.state())
        assertEquals(false, session.unlock(password).offline)

        val reopened = assertIs<SessionState.Unlocked>(session.state())
        assertEquals(listOf("it-item"), activeItems(reopened.vault.data).map { it.id })
        assertEquals(2, reopened.vault.revision)
        session.logout()
    }
}
