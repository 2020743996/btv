package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTuningTest {

    @Test
    fun bufferProfile_prioritizesStablePlayback() {
        assertTrue(PlaybackTuning.MIN_BUFFER_MS >= PlaybackTuning.REBUFFER_MS)
        assertTrue(PlaybackTuning.MAX_BUFFER_MS > PlaybackTuning.MIN_BUFFER_MS)
        assertTrue(PlaybackTuning.REBUFFER_MS > PlaybackTuning.START_BUFFER_MS)
        assertTrue(PlaybackTuning.STALL_TIMEOUT_MS > PlaybackTuning.INITIAL_TIMEOUT_MS)
    }

    @Test
    fun timeout_usesLongerRecoveryWindowAfterPlaybackStarts() {
        assertEquals(PlaybackTuning.INITIAL_TIMEOUT_MS, PlaybackTuning.timeoutMs(false))
        assertEquals(PlaybackTuning.STALL_TIMEOUT_MS, PlaybackTuning.timeoutMs(true))
    }

    @Test
    fun linePriority_prefersLowerFailureCountAndKeepsStableOrder() {
        val urls = listOf("first", "second", "third")
        val failures = mapOf("first" to 2, "second" to 0, "third" to 0)

        assertEquals(
            listOf("second", "third", "first"),
            prioritizePlaybackUrls(urls) { failures[it] ?: 0 }
        )
    }
}
