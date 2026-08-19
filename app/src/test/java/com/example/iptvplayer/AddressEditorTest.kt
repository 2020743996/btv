package com.example.iptvplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressEditorTest {

    @Test
    fun m3uAddressValidation_acceptsHttpSchemesCaseInsensitively() {
        assertTrue(isSupportedM3uUrl("https://example.com/live.m3u"))
        assertTrue(isSupportedM3uUrl("  HTTP://192.168.1.2/list.m3u8  "))
        assertFalse(isSupportedM3uUrl("https://"))
        assertFalse(isSupportedM3uUrl("example.com/live.m3u"))
    }
}
