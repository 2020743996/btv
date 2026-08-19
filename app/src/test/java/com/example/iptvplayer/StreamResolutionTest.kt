package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamResolutionTest {

    @Test
    fun hlsMaster_detectsHighestDeclaredResolution() {
        val manifest = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720
            720/stream.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080/stream.m3u8
        """.trimIndent().toByteArray()

        assertEquals(
            StreamResolution(1920, 1080),
            detectStreamResolution(manifest, "https://example.com/master.m3u8")
        )
    }

    @Test
    fun urlHints_detectCommonResolutionLabels() {
        assertEquals(
            StreamResolution(3840, 2160),
            detectStreamResolution(byteArrayOf(1), "https://example.com/live/4k/index.m3u8")
        )
        assertEquals(
            StreamResolution(3840, 2160),
            detectStreamResolution(byteArrayOf(1), "https://example.com/cctv4k.m3u8")
        )
        assertEquals(
            StreamResolution(3840, 2160),
            detectStreamResolution(byteArrayOf(1), "https://example.com/liveuhd.m3u8")
        )
        assertEquals(
            StreamResolution(3840, 2160),
            detectStreamResolution(byteArrayOf(1), "https://example.com/live.m3u8", listOf("CCTV4K 超高清"))
        )
        assertEquals(
            StreamResolution(1920, 1080),
            detectStreamResolution(byteArrayOf(1), "https://example.com/live-1080p.m3u8")
        )
        assertNull(detectStreamResolution(byteArrayOf(1), "https://example.com/live.m3u8"))
        assertNull(detectStreamResolution(byteArrayOf(1), "https://example.com/channel14k.m3u8"))
    }

    @Test
    fun lineSorting_prefersResolutionThenNetworkScore() {
        val results = listOf(
            quality("fast-720", 95, StreamResolution(1280, 720)),
            quality("slow-1080", 55, StreamResolution(1920, 1080)),
            quality("fast-1080", 90, StreamResolution(1920, 1080)),
            quality("slow-4k", 40, StreamResolution(3840, 2160)),
            quality("unknown", 100, null)
        )

        assertEquals(
            listOf("slow-4k", "fast-1080", "slow-1080", "fast-720", "unknown"),
            sortUsableLines(results).map { it.url }
        )
    }

    private fun quality(url: String, score: Int, resolution: StreamResolution?) = LineQuality(
        url = url,
        usable = true,
        latencyMs = (100 - score).toLong(),
        score = score,
        resolution = resolution
    )
}
