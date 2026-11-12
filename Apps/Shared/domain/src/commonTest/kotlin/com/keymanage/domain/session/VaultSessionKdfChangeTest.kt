package com.keymanage.domain.session

import com.keymanage.domain.auth.MasterPasswordChangedException
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

// 원본: Apps/Web/src/domain/session/vaultSession.kdfChange.test.ts
// 기기 기록의 옛 KDF 값으로 볼트를 열지 못했을 때, 서버의 KDF 값이 바뀌었으면 비밀번호가 바뀌었다고 안내한다 (SEC-003 규칙 11, SEC-004 규칙 8)

private const val EMAIL = "user@example.com"
private const val NEW_PASSWORD = "a brand new master password"

/** 이 기기에서 로그인해 둔 뒤 같은 서버의 다른 기기가 비밀번호를 바꾼다 */
private suspend fun TestScope.changedElsewhere(): SessionSetup {
    val mine = setup()
    mine.session.login(EMAIL, PASSWORD)
    val other = setup(authApi = mine.authApi, vaultApi = mine.vaultApi)
    other.session.login(EMAIL, PASSWORD)
    other.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
    return mine
}

class VaultSessionKdfChangeUnlockTest {
    @Test
    fun newPasswordOnTabOpenedFromDeviceRecordReportsChangeAndSignsOut() = sessionTest {
        val reopened = restart(changedElsewhere())

        assertFailsWith<MasterPasswordChangedException> { reopened.session.unlock(NEW_PASSWORD) }
        assertEquals(SessionState.SignedOut, reopened.session.state())
    }

    @Test
    fun oldPasswordAlsoReportsChange() = sessionTest {
        val reopened = restart(changedElsewhere())

        assertFailsWith<MasterPasswordChangedException> { reopened.session.unlock(PASSWORD) }
        assertEquals(SessionState.SignedOut, reopened.session.state())
    }

    @Test
    fun newPasswordOnLockedTabWithTokenReportsChangeAndSignsOut() = sessionTest {
        val mine = changedElsewhere()
        mine.session.lock()

        assertFailsWith<MasterPasswordChangedException> { mine.session.unlock(NEW_PASSWORD) }
        assertEquals(SessionState.SignedOut, mine.session.state())
    }

    @Test
    fun unchangedServerKdfKeepsWrongPasswordAsWrongAndStaysLocked() = sessionTest {
        val first = setup()
        first.session.login(EMAIL, PASSWORD)
        val reopened = restart(first)

        assertFailsWith<WrongMasterPasswordException> { reopened.session.unlock("not the password") }
        assertIs<SessionState.Locked>(reopened.session.state())
    }

    @Test
    fun unreachableServerSkipsCheckAndStaysWrongPassword() = sessionTest {
        val reopened = restart(changedElsewhere())
        reopened.authApi.offline = true

        assertFailsWith<WrongMasterPasswordException> { reopened.session.unlock(NEW_PASSWORD) }
        assertIs<SessionState.Locked>(reopened.session.state())
    }

    @Test
    fun reconnectWithNewPasswordOnVaultOpenedWithoutSessionReportsChangeAndSignsOut() = sessionTest {
        val first = setup()
        first.session.login(EMAIL, PASSWORD)
        first.authApi.offline = true
        val reopened = restart(first)
        reopened.session.unlock(PASSWORD)
        first.authApi.offline = false
        val other = setup(authApi = first.authApi, vaultApi = first.vaultApi)
        other.session.login(EMAIL, PASSWORD)
        other.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

        assertFailsWith<MasterPasswordChangedException> { reopened.session.reconnect(NEW_PASSWORD) }
        assertEquals(SessionState.SignedOut, reopened.session.state())
    }
}
