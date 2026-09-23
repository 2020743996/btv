package com.example.iptvplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressEditorTest {

    @Test
    fun m3uAddressValidation_acceptsHttpSchemesCaseInsensitively() {
        assertTrue(isSupportedM3uUrl("https://example.com/live.m3u"))
        assertTrue(isSupportedM3uUrl("  HTTP://192.168.1.2/list.m3u8  "))
        assertFalse(isSupportedM3uUrl("https://"))
        assertFalse(isSupportedM3uUrl("example.com/live.m3u"))
        assertFalse(isSupportedM3uUrl("https://?"))
        assertFalse(isSupportedM3uUrl("https://host with space/live"))
        assertFalse(isSupportedM3uUrl("https://example.com:99999/live"))
        assertTrue(isSupportedM3uUrl("http://[::1]:8080/live"))
    }

    @Test
    fun submitDetectsDuplicatesWithoutLosingDraft() {
        val state = AddressEditorState(listOf("https://example.com/live"))
            .edit(1).changeDraft("HTTPS://EXAMPLE.COM/live").submit()
        assertTrue(state.editing)
        assertEquals("这个地址已在列表中", state.error)
        assertEquals(1, state.urls.size)
    }

    @Test
    fun addEditDeleteAndBackPreserveDraftConfirmation() {
        val initial = AddressEditorState(emptyList())
        val added = initial.edit(0).changeDraft("https://example.com/one").submit()
        assertFalse(added.editing)
        assertTrue(added.dirty)
        assertEquals(AddressConfirmation.NONE, added.requestBack().confirmation)
        val editing = added.edit(0).changeDraft("https://example.com/two")
        assertEquals(AddressConfirmation.DISCARD_EDIT, editing.requestBack().confirmation)
        assertEquals(added.urls, editing.closeEditor().urls)
        val modified = editing.submit()
        assertEquals(listOf("https://example.com/two"), modified.urls)
        assertEquals(emptyList<String>(), modified.copy(deleteIndex = 0).confirmDelete().urls)
    }
}
