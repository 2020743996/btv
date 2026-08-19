package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineTesterTest {

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
