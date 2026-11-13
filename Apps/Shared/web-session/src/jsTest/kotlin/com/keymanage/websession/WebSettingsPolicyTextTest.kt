package com.keymanage.websession

import com.keymanage.feature.settings.SettingsViewState
import kotlin.test.Test
import kotlin.test.assertEquals

// 설정 화면의 고정 보안 정책 줄과 동기화 상태 문구를 웹 State가 내려주는지 본다. React는 이 값을 그대로 그린다
// (Scripts/check-policy-acceptance.sh가 테스트 원문을 보므로 문구는 상수가 아니라 글자 그대로 쓴다)

private val SECURITY_POLICY_TEXT = WebSettingsPolicyText(
    clipboardLabel = "클립보드 자동 삭제",
    clipboardValue = "30초 (고정, COM-001)",
    encryptionLabel = "암호화 구조",
    encryptionValue = "클라이언트 영지식 (AES-256-GCM)",
)

class WebSettingsPolicyTextTest {
    @Test
    fun settingsStateCarriesSecurityPolicyRows() {
        assertEquals(SECURITY_POLICY_TEXT, WebSettingsView().store.getSnapshot().securityPolicy)
    }

    @Test
    fun securityPolicyDoesNotDependOnRenderedState() {
        val view = WebSettingsView()
        view.render(SettingsViewState(email = "user@example.com", syncOffline = true))

        assertEquals(SECURITY_POLICY_TEXT, view.store.getSnapshot().securityPolicy)
    }

    @Test
    fun syncStatusIsUpToDateByDefault() {
        assertEquals("최신 상태 (동기화 완료)", WebSettingsView().store.getSnapshot().syncStatus)
    }

    @Test
    fun syncStatusFollowsOfflineState() {
        val view = WebSettingsView()

        view.render(SettingsViewState(syncOffline = true))
        assertEquals("오프라인", view.store.getSnapshot().syncStatus)

        view.render(SettingsViewState(syncOffline = false))
        assertEquals("최신 상태 (동기화 완료)", view.store.getSnapshot().syncStatus)
    }
}
