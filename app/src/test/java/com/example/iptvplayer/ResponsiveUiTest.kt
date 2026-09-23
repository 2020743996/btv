package com.example.iptvplayer

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveUiTest {

    @Test
    fun phoneLandscape_stacksHeaderActions() {
        assertTrue(shouldStackHeaderActions(640.dp))
        assertFalse(shouldStackHeaderActions(720.dp))
    }

    @Test
    fun phoneLandscape_usesTwoPaneWhenTallEnough() {
        assertFalse(usesTwoPaneChannelLayout(WindowType.COMPACT))
        assertTrue(usesTwoPaneChannelLayout(WindowType.MEDIUM))
        assertTrue(usesTwoPaneChannelLayout(WindowType.EXPANDED))
        assertFalse(usesTwoPaneChannelLayout(WindowType.EXPANDED, 300))
        assertTrue(usesTwoPaneChannelLayout(WindowType.EXPANDED, 800))
    }

    @Test
    fun startupDefaultDependsOnDevice() {
        assertFalse(shouldAutoPlay(StartupMode.DEVICE_DEFAULT, isTv = false))
        assertTrue(shouldAutoPlay(StartupMode.DEVICE_DEFAULT, isTv = true))
        assertTrue(shouldAutoPlay(StartupMode.PLAY_LAST, isTv = false))
        assertFalse(shouldAutoPlay(StartupMode.SHOW_LIST, isTv = true))
    }
}
