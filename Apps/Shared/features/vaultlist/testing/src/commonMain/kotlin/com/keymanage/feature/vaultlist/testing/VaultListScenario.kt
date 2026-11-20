package com.keymanage.feature.vaultlist.testing

import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSyncConflictException
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.feature.generator.testing.FakeGeneratorClipboard
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Instant

private val SAMPLE_TIME: Instant = Instant.parse("2026-10-31T00:00:00Z")

/** Example과 테스트가 쓰는 볼트. [fields]마다 항목 하나를 만든다. 항목 id는 "item-순번" */
fun sampleVault(vararg fields: ItemFields): VaultData =
    fields.foldIndexed(emptyVault(SAMPLE_TIME)) { index, vault, item -> addItem(vault, item, SAMPLE_TIME, "item-${index + 1}") }

private fun threeItems(): VaultData = sampleVault(
    ItemFields(serviceName = "GitHub", username = "octocat", password = "k7#Qv2!mR9\$wLx4&Tz8p", url = "https://github.com", notes = "회사 계정"),
    ItemFields(serviceName = "은행", username = "", password = "1234"),
    ItemFields(serviceName = "Apple", username = "tim@example.com", password = "correct horse battery staple"),
)

/**
 * 볼트 목록 화면을 한 가지 상황으로 띄우는 시나리오. Android·iOS·웹의 Example 앱이 같은 목록을 보여 주고,
 * 고른 시나리오의 [session]과 [clipboard]로 VaultList RIB을 만든다 (modularization-plan 3장 3번)
 */
enum class VaultListScenario(val title: String) {
    ITEMS("항목 3개 (추가·수정·삭제·복사)"),
    EMPTY("빈 볼트"),
    READ_ONLY("읽기 전용 (새 버전 앱이 저장한 볼트)"),
    SAVE_FAILS("저장 실패 (서버에 연결할 수 없음)"),
    SAVE_CONFLICT("저장 충돌 (자동 병합 실패)"),
    SLOW_SAVE("저장 지연 (끝나지 않음)"),
    COPY_FAILS("복사 실패"),
    ;

    /** 이 상황을 만드는 가짜 세션 */
    fun session(): FakeVaultSession = when (this) {
        ITEMS, COPY_FAILS -> FakeVaultSession(threeItems())
        EMPTY -> FakeVaultSession(sampleVault())
        READ_ONLY -> FakeVaultSession(threeItems(), readOnly = true)
        SAVE_FAILS -> FakeVaultSession(threeItems()).also { it.failNext = NetworkError() }
        SAVE_CONFLICT -> FakeVaultSession(threeItems()).also { it.applyThenFail = VaultSyncConflictException() }
        SLOW_SAVE -> FakeVaultSession(threeItems()).also { it.gate = CompletableDeferred() }
    }

    /** 이 상황을 만드는 가짜 클립보드 */
    fun clipboard(): FakeGeneratorClipboard = FakeGeneratorClipboard().also { clipboard ->
        if (this == COPY_FAILS) clipboard.failure = IllegalStateException("클립보드를 쓸 수 없음")
    }
}
