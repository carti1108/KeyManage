package com.keymanage.feature.auth.testing

import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.vault.RevisionRollbackException
import com.keymanage.feature.messages.PASSWORD_CHANGED
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * 로그인·가입 화면을 한 가지 상황으로 띄우는 시나리오. Android·iOS·웹의 Example 앱이 같은 목록을 보여 주고,
 * 고른 시나리오의 [session]과 [notice]로 Auth RIB을 만든다
 *
 * @param notice 화면을 열 때 보일 안내(부모가 넘기는 값)
 */
enum class AuthScenario(val title: String, val notice: String? = null) {
    SUCCESS("성공 (로그인·가입)"),
    WRONG_CREDENTIALS("로그인 실패 (이메일 또는 비밀번호 틀림)"),
    EMAIL_TAKEN("가입 실패 (이미 가입한 이메일)"),
    TOO_MANY_ATTEMPTS("시도 횟수 초과"),
    NETWORK_ERROR("서버에 연결할 수 없음"),
    SLOW("지연 (2초 뒤 성공)"),
    ROLLBACK("되돌림 경고 (받아들이면 로그인)"),
    SESSION_EXPIRED_NOTICE("안내와 함께 열림 (세션 만료)", notice = "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    PASSWORD_CHANGED_NOTICE("안내와 함께 열림 (다른 기기에서 비밀번호 변경)", notice = PASSWORD_CHANGED),
    ;

    /** 이 상황을 만드는 가짜 세션 */
    fun session(): FakeAuthSession = FakeAuthSession().also { session ->
        when (this) {
            SUCCESS, SESSION_EXPIRED_NOTICE, PASSWORD_CHANGED_NOTICE -> Unit
            WRONG_CREDENTIALS -> session.loginHandler = { _, _, _ -> throw ServerError(401, ServerErrorCode.AUTH_FAILED) }
            EMAIL_TAKEN -> session.registerHandler = { _, _ -> throw ServerError(409, ServerErrorCode.EMAIL_ALREADY_REGISTERED) }
            TOO_MANY_ATTEMPTS -> {
                session.loginHandler = { _, _, _ -> throw ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS) }
                session.registerHandler = { _, _ -> throw ServerError(429, ServerErrorCode.TOO_MANY_ATTEMPTS) }
            }
            NETWORK_ERROR -> {
                session.loginHandler = { _, _, _ -> throw NetworkError() }
                session.registerHandler = { _, _ -> throw NetworkError() }
            }
            SLOW -> {
                session.loginHandler = { _, _, _ -> delay(2.seconds) }
                session.registerHandler = { _, _ -> delay(2.seconds) }
            }
            ROLLBACK -> session.loginHandler = { _, _, acceptRollback ->
                if (!acceptRollback) throw RevisionRollbackException(lastSeenRevision = 7, serverRevision = 5)
            }
        }
    }
}
