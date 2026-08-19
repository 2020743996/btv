package com.example.iptvplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoContrastTest {

    @Test
    fun whiteLogo_usesDarkBackground() {
        assertTrue(needsDarkLogoBackground(IntArray(16) { 0xFFFFFFFF.toInt() }))
    }

    @Test
    fun darkOrTransparentLogo_keepsWhiteBackground() {
        assertFalse(needsDarkLogoBackground(IntArray(16) { 0xFF20252A.toInt() }))
        assertFalse(needsDarkLogoBackground(IntArray(16) { 0x00FFFFFF }))
    }

    @Test
    fun mostlyLightLogo_ignoresTransparentPadding() {
        val pixels = IntArray(16) { 0x00000000 }
        pixels[5] = 0xFFFFFFFF.toInt()
        pixels[6] = 0xFFF4F4F4.toInt()
        pixels[9] = 0xFFEFEFEF.toInt()

        assertTrue(needsDarkLogoBackground(pixels))
    }
}
