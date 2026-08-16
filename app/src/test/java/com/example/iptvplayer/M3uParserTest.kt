package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun parseM3u_readsChannelMetadataAndUrl() {
        val text = """
            #EXTM3U x-tvg-url="https://example.com/epg.xml"
            #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1 高清
            https://example.com/cctv1.m3u8
        """.trimIndent()

        val channels = parseM3u(text)

        assertEquals(1, channels.size)
        assertEquals("CCTV-1 高清", channels[0].name)
        assertEquals("央视", channels[0].group)
        assertEquals(listOf("cctv1"), channels[0].tvgIds)
        assertEquals(listOf("https://example.com/cctv1.m3u8"), channels[0].urls)
    }

    @Test
    fun parseM3u_usesTvgNameWhenIdIsMissing() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Hunan TV",湖南卫视
            http://example.com/hunan.ts
        """.trimIndent()

        assertEquals(listOf("Hunan TV"), parseM3u(text).single().tvgIds)
    }

    @Test
    fun mergeChannels_combinesEquivalentNamesAndRemovesDuplicateUrls() {
        val channels = listOf(
            Channel("CCTV-1 高清", "央视", listOf("https://a/live.m3u8"), tvgIds = listOf("cctv1")),
            Channel("cctv1 HD", "综合", listOf("https://a/live.m3u8", "https://b/live.m3u8"), tvgIds = listOf("cctv-one"))
        )

        val merged = mergeChannels(channels).single()

        assertEquals("CCTV-1 高清", merged.name)
        assertEquals("央视", merged.group)
        assertEquals(listOf("https://a/live.m3u8", "https://b/live.m3u8"), merged.urls)
        assertEquals(listOf("cctv1", "cctv-one"), merged.tvgIds)
    }

    @Test
    fun extractEpgUrl_supportsBothCommonAttributes() {
        assertEquals(
            "https://example.com/epg.xml",
            extractEpgUrl("#EXTM3U url-tvg=\"https://example.com/epg.xml\"")
        )
        assertNull(extractEpgUrl("#EXTM3U"))
    }

    @Test
    fun normalizeChannelName_onlyRemovesQualityAtTheEnd() {
        assertEquals("cctv1", normalizeChannelName("CCTV-1 高清 HD"))
        assertEquals("sportstv", normalizeChannelName("Sports TV"))
    }

    @Test
    fun channelCache_checksSourcesAndCanBeInvalidated() {
        val channels = listOf(Channel("新闻", "综合", listOf("https://example.com/live")))
        val sources = listOf("https://example.com/list.m3u")

        ChannelCache.update(channels, sources)
        assertEquals(channels, ChannelCache.freshChannels(sources))
        assertNull(ChannelCache.freshChannels(listOf("https://other.example/list.m3u")))

        ChannelCache.invalidate()
        assertNull(ChannelCache.freshChannels(sources))
        assertTrue(ChannelCache.staleChannels(sources).orEmpty().isNotEmpty())
    }
}
