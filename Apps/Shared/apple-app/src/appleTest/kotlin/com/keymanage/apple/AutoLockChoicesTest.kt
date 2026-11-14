package com.keymanage.apple

import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class AutoLockChoicesTest {
    @Test
    fun choicesFollowPolicyOptionsWithWebLabels() {
        val choices = autoLockChoices(SettingsViewState(autoLockTimeout = 15.minutes))

        assertEquals(listOf("즉시", "1분", "5분", "15분", "1시간"), choices.map { it.label })
        assertEquals(listOf(false, false, false, true, false), choices.map { it.isSelected })
        assertEquals(SettingsViewAction.SelectAutoLock(15.minutes), choices[3].action)
        assertEquals(listOf("autoLockChoice-0", "autoLockChoice-60", "autoLockChoice-300", "autoLockChoice-900", "autoLockChoice-3600"), choices.map { it.id })
    }
}
