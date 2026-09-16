package com.example.iptvplayer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleStyleTest {
    @Test fun actionAndSecondaryTextRemainReadableOnWhite() {
        listOf(UiColors.Info, UiColors.Delete, AppleUi.Secondary).forEach { color ->
            val contrast = (Color.White.luminance() + 0.05f) / (color.luminance() + 0.05f)
            assertTrue("Insufficient contrast: $contrast", contrast >= 4.5f)
        }
    }
}
