package com.keymanage.feature.main.testing

import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.feature.testing.FakeEnvironment
import com.keymanage.feature.testing.FakeSession
import com.keymanage.feature.testing.testVault

/**
 * 볼트를 연 뒤의 화면(탭과 안내)을 한 가지 상황으로 띄우는 시나리오. Android·iOS·웹의 Example 앱이 같은 목록을 보여 주고,
 * 고른 시나리오의 [session]·[environment]·[offline]으로 Main RIB을 만든다 (modularization-plan 3장 3번).
 * 탭 안의 화면(볼트 목록·생성기·설정)은 진짜 자식 RIB이다. 설정 탭의 백업·비밀번호 변경은 이 가짜 세션이 지원하지 않는다
 *
 * @param offline 잠금 해제를 서버에 닿지 못해 기기에 저장한 볼트로 열었다
 */
enum class MainScenario(val title: String, val offline: Boolean = false) {
    ONLINE("정상 (탭 전환)"),
    OPENED_OFFLINE("오프라인으로 엶 (오프라인 안내)", offline = true),
    NETWORK_OFFLINE("네트워크 끊김"),
    NEEDS_RECONNECT("세션 없이 엶 (비밀번호로 다시 연결)"),
    RECONNECT_WRONG_PASSWORD("다시 연결 실패 (틀린 비밀번호)"),
    READ_ONLY("읽기 전용 (새 버전 앱이 저장한 볼트)"),
    LOGOUT_WITH_PENDING("로그아웃 확인 (올리지 못한 변경 있음)"),
    ;

    /** 이 상황을 만드는 가짜 세션 */
    fun session(): FakeSession = FakeSession().also { session ->
        session.email = "user@example.com"
        session.vault = testVault("GitHub", "Apple", "은행")
        when (this) {
            ONLINE, OPENED_OFFLINE, NETWORK_OFFLINE -> Unit
            NEEDS_RECONNECT -> session.signedIn = false
            RECONNECT_WRONG_PASSWORD -> {
                session.signedIn = false
                session.reconnectHandler = { _, _ -> throw WrongMasterPasswordException() }
            }
            READ_ONLY -> session.readOnly = true
            LOGOUT_WITH_PENDING -> session.pendingSync = true
        }
        session.startUnlocked()
    }

    /** 이 상황을 만드는 앱 환경 */
    fun environment(): FakeEnvironment = FakeEnvironment().also { environment ->
        environment.serverAddress = "https://keymanage.example"
        environment.offline = this == NETWORK_OFFLINE
    }
}
