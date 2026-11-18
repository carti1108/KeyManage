package com.keymanage.android.feature.auth

import com.keymanage.android.feature.auth.impl.AuthMode
import com.keymanage.android.feature.auth.impl.AuthViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthStrengthTest {
    @Test
    fun `가입 모드에서 비밀번호가 있으면 GEN-001 점수를 보인다`() {
        val state = AuthViewState(mode = AuthMode.SIGNUP, password = "abcdefgh")
        assertEquals(37, state.passwordScore)
        assertTrue(state.isWeakPassword)
    }

    @Test
    fun `로그인 화면과 빈 비밀번호에는 게이지가 없다`() {
        assertNull(AuthViewState(mode = AuthMode.LOGIN, password = "abcdefgh").passwordScore)
        assertNull(AuthViewState(mode = AuthMode.SIGNUP, password = "").passwordScore)
        assertFalse(AuthViewState(mode = AuthMode.SIGNUP).isWeakPassword)
    }

    @Test
    fun `강한 비밀번호는 경고하지 않는다`() {
        assertFalse(AuthViewState(mode = AuthMode.SIGNUP, password = "Aa1!Aa1!Aa1!Aa1!").isWeakPassword)
    }
}
