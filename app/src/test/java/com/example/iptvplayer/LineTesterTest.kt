package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineTesterTest {

    @Test
    fun retainsSmoothLowResolutionBackupAfterThree4kLines() {
        val urls = listOf("4k-a", "4k-b", "4k-c", "1080-backup")
        val channel = Channel("test", "test", urls)
        val results = urls.mapIndexed { i, url ->
            LineQuality(url, true, 10, if (i == 3) 95 else 30,
                if (i == 3) StreamResolution(1920, 1080) else StreamResolution(3840, 2160))
        }
        assertEquals(urls, applyLineTestResults(channel, results) { 0 }.urls)
    }

    @Test
    fun networkQuality_usesLatencyForSmallManifest() {
        val quality = calculateNetworkQuality(
            latencyMs = 120,
            bytesRead = 2_048,
            transferMs = 10
        )

        assertEquals(90, quality.score)
        assertNull(quality.throughputKbps)
    }

    @Test
    fun networkQuality_penalizesSlowLargeProbe() {
        val fast = calculateNetworkQuality(80, 64 * 1_024, 40)
        val slow = calculateNetworkQuality(80, 64 * 1_024, 2_000)

        assertTrue(fast.score > slow.score)
        assertTrue(fast.throughputKbps!! > slow.throughputKbps!!)
    }

    @Test
    fun streamProbe_rejectsHtmlErrorPages() {
        assertFalse(
            isLikelyStreamResponse(
                "<!doctype html><html>blocked</html>".toByteArray(),
                "text/html; charset=utf-8"
            )
        )
        assertTrue(
            isLikelyStreamResponse(
                "#EXTM3U\n#EXT-X-VERSION:3".toByteArray(),
                "application/vnd.apple.mpegurl"
            )
        )
    }
}
