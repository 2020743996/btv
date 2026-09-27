package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class M3uParserTest {

    @Test
    fun cacheRejectsTestResultAfterRefreshOrSourceChange() {
        val old = listOf(Channel("old", "test", listOf("old-url")))
        val fresh = listOf(Channel("new", "test", listOf("new-url")))
        ChannelCache.update(old, listOf("source-a"))
        val revision = ChannelCache.revision
        ChannelCache.invalidate()
        assertFalse(ChannelCache.replaceIfCurrent(revision, old))
        ChannelCache.update(fresh, listOf("source-b"))
        assertFalse(ChannelCache.replaceIfCurrent(revision, old))
        assertEquals(fresh, ChannelCache.channels)
        assertTrue(ChannelCache.replaceIfCurrent(ChannelCache.revision, fresh))
    }

    @Test
    fun measured1080Corrects4kHintAndSurvivesPendingTest() {
        val hint = LineQuality("url", true, 30, 90, StreamResolution(3840, 2160))
        val channel = Channel("test", "test", listOf("url"), lineQuality = listOf(hint))
        ChannelCache.update(listOf(channel), listOf("source"))
        val revision = ChannelCache.revision
        ChannelCache.updateLineResolution("test", "url", StreamResolution(1920, 1080))
        assertTrue(ChannelCache.replaceIfCurrent(revision, listOf(channel)))
        val quality = ChannelCache.channels.single().lineQuality!!.single()
        assertEquals(StreamResolution(1920, 1080), quality.measuredResolution)
        assertEquals("1080p · 流畅", channelStatusText(ChannelCache.channels.single()))
    }

    @Test
    fun parseM3u_readsChannelMetadataAndUrl() {
        val text = """
            #EXTM3U x-tvg-url="https://example.com/epg.xml"
            #EXTINF:-1 tvg-id="cctv1" tvg-logo="https://example.com/cctv1.png" group-title="央视",CCTV-1 高清
            https://example.com/cctv1.m3u8
        """.trimIndent()

        val channels = parseM3u(text)

        assertEquals(1, channels.size)
        assertEquals("CCTV-1 高清", channels[0].name)
        assertEquals("央视", channels[0].group)
        assertEquals(listOf("cctv1"), channels[0].tvgIds)
        assertEquals("https://example.com/cctv1.png", channels[0].logoUrl)
        assertEquals(listOf("https://example.com/cctv1.m3u8"), channels[0].urls)
        assertEquals(
            "CCTV-1 高清 cctv1",
            channels[0].urlQualityHints["https://example.com/cctv1.m3u8"]
        )
    }

    @Test
    fun parseM3u_preservesCatchupMetadataPerUrlAndMergesItWithoutCrossingLines() {
        val parsed = parseM3u(
            """
                #EXTM3U
                #EXTINF:-1 catchup="default" catchup-source="https://archive.example/{utc}.m3u8" catchup-days="3",CCTV-1
                https://one.example/live
                #EXTINF:-1 catchup="append" catchup-source="&utc={utc}&duration={duration}" catchup-days="5",CCTV1 HD
                https://two.example/live
            """.trimIndent()
        )

        val merged = mergeChannels(parsed).single()
        assertEquals("default", merged.catchupByUrl["https://one.example/live"]?.mode)
        assertEquals(3, merged.catchupByUrl["https://one.example/live"]?.days)
        assertEquals("append", merged.catchupByUrl["https://two.example/live"]?.mode)
        assertEquals(5, merged.catchupByUrl["https://two.example/live"]?.days)
    }

    @Test
    fun catchupUrlBuilderSupportsDefaultAndAppendTemplatesAndEnforcesDeclaredWindow() {
        val now = 1_800_000_000_000L
        val programme = Programme("news", java.util.Date(now - 3_600_000L), java.util.Date(now - 1_800_000L), "新闻")
        val startSeconds = programme.start.time / 1000L
        val durationSeconds = (programme.end.time - programme.start.time) / 1000L

        assertEquals(
            "https://archive.example/$startSeconds/$durationSeconds",
            buildCatchupPlaybackUrl(
                "https://live.example/channel.m3u8",
                CatchupMetadata("default", "https://archive.example/{utc}/{duration}", 2),
                programme,
                now
            )
        )
        assertEquals(
            "https://archive.example/$startSeconds/${durationSeconds / 60}",
            buildCatchupPlaybackUrl(
                "https://live.example/channel.m3u8",
                CatchupMetadata("default", "https://archive.example/{utc}/{duration:60}", 2),
                programme,
                now
            )
        )
        assertEquals(
            "https://live.example/channel.m3u8?utc=$startSeconds&duration=$durationSeconds",
            buildCatchupPlaybackUrl(
                "https://live.example/channel.m3u8",
                CatchupMetadata("append", "&utc={utc}&duration={duration}", 2),
                programme,
                now
            )
        )
        assertNull(buildCatchupPlaybackUrl(
            "https://live.example/channel.m3u8",
            CatchupMetadata("default", "https://archive.example/{utc}", null),
            programme,
            now
        ))
        val tooOld = programme.copy(
            start = java.util.Date(now - 2 * 86_400_000L),
            end = java.util.Date(now - 2 * 86_400_000L + 1_800_000L)
        )
        assertNull(buildCatchupPlaybackUrl(
            "https://live.example/channel.m3u8",
            CatchupMetadata("default", "https://archive.example/{utc}", 1),
            tooOld,
            now
        ))
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
    fun parseM3u_usesTvgNameWhenIdIsBlank() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="" tvg-name="Hunan TV",湖南卫视
            http://example.com/hunan.ts
        """.trimIndent()

        assertEquals(listOf("Hunan TV"), parseM3u(text).single().tvgIds)
    }

    @Test
    fun mergeChannels_combinesEquivalentNamesAndRemovesDuplicateUrls() {
        val channels = listOf(
            Channel("CCTV-1 高清", "央视", listOf("https://a/live.m3u8"), tvgIds = listOf("cctv1")),
            Channel(
                "cctv1 HD",
                "综合",
                listOf("https://a/live.m3u8", "https://b/live.m3u8"),
                tvgIds = listOf("cctv-one"),
                logoUrl = "https://example.com/cctv1.png"
            )
        )

        val merged = mergeChannels(channels).single()

        assertEquals("CCTV-1 高清", merged.name)
        assertEquals("央视", merged.group)
        assertEquals(listOf("https://a/live.m3u8", "https://b/live.m3u8"), merged.urls)
        assertEquals(listOf("cctv1", "cctv-one"), merged.tvgIds)
        assertEquals("https://example.com/cctv1.png", merged.logoUrl)
    }

    @Test
    fun mergeChannels_preservesPerLine4kHint() {
        val channels = parseM3u(
            """
                #EXTM3U
                #EXTINF:-1,CCTV-1
                https://example.com/hd.m3u8
                #EXTINF:-1,CCTV-1 4K
                https://example.com/uhd.m3u8
            """.trimIndent()
        )

        val merged = mergeChannels(channels).single()

        assertEquals("CCTV-1 4K", merged.urlQualityHints["https://example.com/uhd.m3u8"])
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
    fun extractEpgUrls_supportsMultipleDeclaredSources() {
        assertEquals(
            listOf("https://one.example/epg.xml", "https://two.example/epg.xml"),
            extractEpgUrls(
                "#EXTM3U x-tvg-url=\"https://one.example/epg.xml,https://two.example/epg.xml\""
            )
        )
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

    @Test
    fun channelCache_restoresHiddenLinesAndUpdatesDetectedResolution() {
        val primary = "https://example.com/hd.m3u8"
        val backup = "https://example.com/uhd.m3u8"
        ChannelCache.update(
            channels = listOf(
                Channel(
                    name = "测试频道",
                    group = "测试",
                    urls = listOf(primary),
                    allUrls = listOf(primary, backup)
                )
            ),
            sources = listOf("https://example.com/list.m3u")
        )

        ChannelCache.restoreLine(
            backup,
            LineQuality(backup, usable = true, latencyMs = 80, score = 75)
        )
        assertEquals(listOf(backup, primary), ChannelCache.channels.single().urls)

        ChannelCache.updateLineResolution("测试频道", backup, StreamResolution(3840, 2160))
        assertEquals(
            StreamResolution(3840, 2160),
            ChannelCache.channels.single().lineQuality?.first { it.url == backup }?.measuredResolution
        )

        ChannelCache.restoreAllLines()
        assertEquals(listOf(primary, backup), ChannelCache.channels.single().urls)
        assertNull(ChannelCache.channels.single().lineQuality)
    }

    @Test
    fun orderedStringListEncoding_preservesOrderAndRemovesBlankDuplicates() {
        val values = listOf(
            " https://one.example/list.m3u ",
            "",
            "https://two.example/list.m3u",
            "https://one.example/list.m3u"
        )

        val encodedText = encodeOrderedStringList(values)

        assertEquals(
            listOf("https://one.example/list.m3u", "https://two.example/list.m3u"),
            decodeOrderedStringList(encodedText)
        )
    }

    @Test
    fun orderedStringListDecoding_returnsEmptyListForBrokenData() {
        assertEquals(emptyList<String>(), decodeOrderedStringList("not encoded"))
    }

    @Test
    fun channelLogoFallback_handlesChineseLatinAndBlankNames() {
        assertEquals("湖南", channelLogoFallback("湖南卫视"))
        assertEquals("CCT", channelLogoFallback("CCTV-1 高清"))
        assertEquals("TV", channelLogoFallback("---"))
    }
}
