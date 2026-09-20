package com.example.iptvplayer

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class PlaybackOptionsTest {
    @Test fun manualLastLineFallsBackToFirstUntriedLine() {
        assertEquals(0, nextUntriedLine(listOf("a", "b", "c"), 2, setOf("c")))
        assertEquals(1, nextUntriedLine(listOf("a", "b", "c"), 2, setOf("a", "c")))
    }

    @Test fun exhaustedAndEmptyLinesDoNotLoop() {
        assertNull(nextUntriedLine(emptyList(), 0, emptySet()))
        assertNull(nextUntriedLine(listOf("a", "b", "a"), 2, setOf("a", "b")))
        assertNull(nextUntriedLine(listOf("a"), 0, setOf("a")))
    }

    @Test fun timerUsesDeadlineAndRoundsRemainingMinutesUp() {
        val timer = SleepTimer()
        timer.set(15, 100L)
        assertEquals(15, timer.remainingMinutes(101L))
        assertEquals(1, timer.remainingMinutes(900_099L))
        assertFalse(timer.isExpired(900_099L))
        assertTrue(timer.isExpired(900_100L))
        assertEquals(0, timer.remainingMinutes(950_100L))
    }

    @Test fun timerCanBeReplacedOrCancelled() {
        val timer = SleepTimer()
        timer.set(15, 0L)
        timer.set(30, 200L)
        assertEquals(1_800_200L, timer.deadline)
        timer.set(0, 300L)
        assertNull(timer.remainingMinutes(Long.MAX_VALUE))
        assertFalse(timer.isExpired(Long.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun timerRejectsInvalidDuration() { SleepTimer().set(-1, 0) }

    @Test fun unknownPicturePreferenceUsesFit() {
        assertEquals(PictureMode.FIT, PictureMode.fromStored(null))
        assertEquals(PictureMode.FIT, PictureMode.fromStored("unknown"))
        assertEquals(PictureMode.ZOOM, PictureMode.fromStored("ZOOM"))
    }

    @Test fun guideMatchesAliasesSortsDeduplicatesAndLimitsWindow() {
        val sources = setOf("https://guide.example/test")
        val current = Programme("news", Date(0), Date(2_000), "Current")
        val next = Programme("news", Date(2_000), Date(3_000), "Next")
        val expired = Programme("news", Date(0), Date(1_000), "Expired")
        val tomorrow = Programme("news", Date(86_401_000L), Date(86_402_000L), "Tomorrow")
        val invalid = Programme("news", Date(4_000), Date(3_000), "Invalid")
        EpgCache.configureSources(sources)
        try {
            EpgCache.update(mapOf("news" to listOf(next, expired, current, tomorrow, invalid),
                "alias" to listOf(next.copy(channelId = "alias"))), sources)
            assertEquals(listOf(current, next), EpgCache.guide(listOf(" NEWS ", "alias", "news"), Date(1_000)))
            assertTrue(EpgCache.guide(listOf("missing"), Date(1_000)).isEmpty())
        } finally { EpgCache.configureSources(emptySet()) }
    }
}
