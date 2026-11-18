package com.keymanage.android.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentLogicTest {
    @Test
    fun `강도 단계 경계는 59-60, 79-80이다`() {
        assertEquals(EntropyLevel.WEAK, EntropyLevel.of(59))
        assertEquals(EntropyLevel.MEDIUM, EntropyLevel.of(60))
        assertEquals(EntropyLevel.MEDIUM, EntropyLevel.of(79))
        assertEquals(EntropyLevel.STRONG, EntropyLevel.of(80))
    }

    @Test
    fun `범위 밖 점수는 0~100으로 자른다`() {
        assertEquals(0, EntropyLevel.clamp(-5))
        assertEquals(100, EntropyLevel.clamp(150))
        assertEquals(EntropyLevel.WEAK, EntropyLevel.of(-5))
        assertEquals(EntropyLevel.STRONG, EntropyLevel.of(150))
    }

    @Test
    fun `강도 문구는 디자인 시스템과 같다`() {
        assertEquals(listOf("취약함", "보통", "초강력"), EntropyLevel.entries.map { it.label })
    }

    @Test
    fun `소각 배지는 남은 초를 0 아래로 보이지 않는다`() {
        assertEquals("클립보드 삭제까지 28초", KMBurnBadgeText.visible(28))
        assertEquals("클립보드 삭제까지 0초", KMBurnBadgeText.visible(-3))
        assertEquals("0초 남음", KMBurnBadgeText.accessibilityValue(-1))
    }

    @Test
    fun `비밀번호 자동 가림 시간은 10초다`() {
        assertEquals(10_000L, KM_PASSWORD_AUTO_MASK_MILLIS)
    }

    @Test
    fun `버튼 최소 높이는 32, 40, 48이다`() {
        assertEquals(listOf(32, 40, 48), KMButtonSize.entries.map { it.minHeight })
    }
}
