package com.keymanage.domain.session

import com.keymanage.domain.crypto.VaultDecryptException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

// 옛 Swift AuthTests.login_vaultDecryptionFailure_throwsLoginFailed에서 옮겼다.
// 서버가 인증을 받아도 서버 볼트가 이 비밀번호의 키로 열리지 않으면 로그인을 실패시키고 볼트를 열지 않는다
class VaultSessionLoginDecryptTest {
    @Test
    fun loginFailsWithDecryptErrorWhenServerVaultDoesNotOpenWithThisKey() = sessionTest {
        val ctx = setup()
        ctx.session.login("user@example.com", PASSWORD)
        ctx.session.logout()

        assertFailsWith<VaultDecryptException> { ctx.session.login("user@example.com", "another master password") }

        assertIs<SessionState.SignedOut>(ctx.session.state())
    }
}
