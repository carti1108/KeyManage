package com.keymanage.back.domain.auth

import com.keymanage.back.domain.vault.VaultId
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 시작 때 만료·계정 없음·볼트 ID 불일치 세션을 지우고 정상 세션은 남긴다 (ADR-BE-002 결정 4) */
class CleanUpSessionsUseCaseTests {

    private val accounts = InMemoryAccountRepository()
    private val sessions = InMemorySessionRepository()
    private val clock = MutableClock()
    private val email = Email.of("user@example.com")
    private val vaultId = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val otherVaultId = VaultId.of("650e8400-e29b-41d4-a716-446655440000")

    init {
        accounts.createIfAbsent(Account(email, kdfOf(1), "hash", vaultId))
    }

    private fun session(name: Char, owner: Email = email, vault: VaultId = vaultId, expiresIn: Duration = Duration.ofDays(1)) =
        Session(name.toString().repeat(64), owner, clock.instant().plus(expiresIn), vault).also(sessions::save)

    private fun cleanUp(accountRepository: AccountRepository = accounts, sessionRepository: SessionRepository = sessions) =
        CleanUpSessionsUseCase(sessionRepository, accountRepository, clock).execute()

    @Test
    fun `정상 세션은 남긴다`() {
        val valid = session('a')

        assertEquals(0, cleanUp())

        assertNotNull(sessions.find(valid.tokenHash))
    }

    @Test
    fun `만료된 세션은 지운다 - 만료 시각과 같으면 만료다`() {
        val expired = session('a', expiresIn = Duration.ofSeconds(-1))
        val boundary = session('b', expiresIn = Duration.ZERO)
        val valid = session('c', expiresIn = Duration.ofSeconds(1))

        assertEquals(2, cleanUp())

        assertNull(sessions.find(expired.tokenHash))
        assertNull(sessions.find(boundary.tokenHash))
        assertNotNull(sessions.find(valid.tokenHash))
    }

    @Test
    fun `계정이 없는 세션은 지운다`() {
        val ghost = session('a', owner = Email.of("ghost@example.com"))

        assertEquals(1, cleanUp())

        assertNull(sessions.find(ghost.tokenHash))
    }

    @Test
    fun `계정의 볼트 ID와 다른 세션은 지운다`() {
        val stale = session('a', vault = otherVaultId)
        val valid = session('b')

        assertEquals(1, cleanUp())

        assertNull(sessions.find(stale.tokenHash))
        assertNotNull(sessions.find(valid.tokenHash))
    }

    @Test
    fun `계정을 하나라도 읽지 못하면 세션 정리를 건너뛴다`() {
        val ghost = session('a', owner = Email.of("ghost@example.com"))
        val broken = object : AccountRepository by accounts {
            override fun allVaultIds(): Set<VaultId> = throw IOException("계정 읽기 실패")
        }

        assertEquals(0, cleanUp(accountRepository = broken))

        assertNotNull(sessions.find(ghost.tokenHash))
    }

    @Test
    fun `세션 하나를 읽거나 지우지 못해도 나머지는 정리한다`() {
        val unreadable = session('a', owner = Email.of("ghost@example.com"))
        val ghost = session('b', owner = Email.of("ghost@example.com"))
        val flaky = object : SessionRepository by sessions {
            override fun find(tokenHash: String): Session? =
                if (tokenHash == unreadable.tokenHash) throw IOException("읽기 실패") else sessions.find(tokenHash)
        }

        assertEquals(1, cleanUp(sessionRepository = flaky))

        assertNull(sessions.find(ghost.tokenHash))
        assertNotNull(sessions.find(unreadable.tokenHash))
    }
}
