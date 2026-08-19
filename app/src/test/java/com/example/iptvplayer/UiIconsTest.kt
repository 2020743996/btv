package com.example.iptvplayer

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiIconsTest {

    @Test
    fun lineIcons_initializeWithConsistentViewport() {
        val icons = listOf(
            UiIcons.Search,
            UiIcons.Refresh,
            UiIcons.Gauge,
            UiIcons.Sliders,
            UiIcons.Heart,
            UiIcons.Play,
            UiIcons.ArrowLeft,
            UiIcons.Plus,
            UiIcons.Trash,
            UiIcons.Check,
            UiIcons.X,
            UiIcons.Pencil,
            UiIcons.Info
        )

        assertEquals(13, icons.size)
        assertTrue(icons.all { it.defaultWidth == 24.dp && it.defaultHeight == 24.dp })
    }
}
