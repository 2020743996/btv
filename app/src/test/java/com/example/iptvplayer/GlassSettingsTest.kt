package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassSettingsTest {
    @Test
    fun transparencyIsClampedToLightweightMaterialRange() {
        assertEquals(0, normalizeGlassTransparency(-1))
        assertEquals(18, normalizeGlassTransparency(18))
        assertEquals(40, normalizeGlassTransparency(100))
    }
}
