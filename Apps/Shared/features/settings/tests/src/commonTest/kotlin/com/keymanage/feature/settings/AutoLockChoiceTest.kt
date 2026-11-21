package com.keymanage.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AutoLockChoiceTest {
    @Test
    fun autoLockLabel_formatsZeroMinutesAndHours() {
        assertEquals("즉시", autoLockLabel(Duration.ZERO))
        assertEquals("5분", autoLockLabel(5.minutes))
        assertEquals("1시간", autoLockLabel(60.minutes))
        assertEquals("90분", autoLockLabel(90.minutes))
    }

    @Test
    fun autoLockChoices_marksOnlyCurrentTimeout() {
        val state = SettingsViewState(autoLockTimeout = 15.minutes)

        val selected = state.autoLockChoices.filter { it.isSelected }

        assertEquals(listOf(15.minutes), selected.map { it.timeout })
        assertEquals(state.autoLockOptions, state.autoLockChoices.map { it.timeout })
        assertEquals("15분", state.autoLockTimeoutLabel)
    }
}
