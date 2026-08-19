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
    fun phoneLandscape_keepsSingleColumnChannelList() {
        assertFalse(usesTwoPaneChannelLayout(WindowType.COMPACT))
        assertFalse(usesTwoPaneChannelLayout(WindowType.MEDIUM))
        assertTrue(usesTwoPaneChannelLayout(WindowType.EXPANDED))
    }
}
