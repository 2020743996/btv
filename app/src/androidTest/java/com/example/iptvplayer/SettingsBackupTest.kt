package com.example.iptvplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackupTest {
    @Test fun backupRoundTripAndInvalidInput() {
        val original = SettingsBackup(
            listOf("https://example.com/list.m3u?token=secret"), "https://example.com/guide.xml",
            listOf("CCTV-1"), listOf("CCTV-1"), true, 2,
            StartupMode.SHOW_LIST, PictureMode.FIT
        )
        assertEquals(original, decodeBackup(encodeBackup(original)))
        val invalid = JSONObject(encodeBackup(original)).put("m3uUrls", JSONArray().put("invalid"))
        assertThrows(IllegalArgumentException::class.java) {
            decodeBackup(invalid.toString())
        }
    }

    @Test fun sourceSnapshotRetainsLastSuccess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshots = SourceSnapshots(context)
        val url = "https://example.com/test-${System.nanoTime()}.m3u"
        assertNull(snapshots.load("m3u", url))
        val text = "#EXTM3U\n#EXTINF:-1,CCTV-1\nhttps://example.com/live.m3u8"
        snapshots.save("m3u", url, text)
        assertEquals(text, snapshots.load("m3u", url))
        val stale = loadChannelSource(context, url) { throw java.io.IOException("offline") }
        assertEquals(SourceHealth.STALE, stale.status.health)
        assertEquals("CCTV-1", stale.channels.single().name)
        val missing = loadChannelSource(context, "$url-missing") { throw java.io.IOException("offline") }
        assertEquals(SourceHealth.FAILED, missing.status.health)
        assertTrue(missing.channels.isEmpty())
    }

    @Test fun partialFailureMergesLiveAndStaleSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldUrl = "https://example.com/old-${System.nanoTime()}.m3u"
        val liveUrl = "https://example.com/live-${System.nanoTime()}.m3u"
        SourceSnapshots(context).save("m3u", oldUrl,
            "#EXTM3U\n#EXTINF:-1,旧源频道\nhttps://example.com/old.ts")
        val old = loadChannelSource(context, oldUrl) { throw java.io.IOException("offline") }
        val live = loadChannelSource(context, liveUrl) {
            "#EXTM3U\n#EXTINF:-1,新源频道\nhttps://example.com/live.ts"
        }
        assertEquals(SourceHealth.STALE, old.status.health)
        assertEquals(SourceHealth.NORMAL, live.status.health)
        assertEquals(setOf("旧源频道", "新源频道"), mergeChannels(old.channels + live.channels).map { it.name }.toSet())
    }
}
