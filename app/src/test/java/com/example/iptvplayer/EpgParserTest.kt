package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class EpgParserTest {

    @Test
    fun programmeWindowFilteringUsesIntersectingTimeRanges() {
        val start = 1_000_000L
        val end = start + 12 * 60 * 60 * 1000L

        assertEquals(true, isProgrammeInWindow(start + 1, end - 1, end, start))
        assertEquals(false, isProgrammeInWindow(end, end + 1, end, start))
        assertEquals(false, isProgrammeInWindow(start - 2, start, end, start))
    }

    @Test
    fun epgHistoryWindowFollowsDeclaredCatchupDaysWithTwoHourBaseline() {
        val now = 2_000_000_000_000L
        assertEquals(now - 4L * 86_400_000L, epgHistoryWindowStart(now, 4))
        assertEquals(now - 2L * 3_600_000L, epgHistoryWindowStart(now, 0))
    }

    @Test
    fun sourceChangeClearsOldScheduleAndRejectsOldDownload() {
        val programme = Programme("news", Date(0), Date(3_000), "old")
        val first = setOf("https://one.example/epg")
        val second = setOf("https://two.example/epg")
        EpgCache.configureSources(first)
        EpgCache.update(mapOf("news" to listOf(programme)), first)
        assertEquals(programme, EpgCache.schedule(listOf("news"), Date(1_000)).current)
        EpgCache.configureSources(second)
        EpgCache.update(mapOf("news" to listOf(programme)), first)
        assertNull(EpgCache.schedule(listOf("news"), Date(1_000)).current)
        EpgCache.configureSources(emptySet())
    }

    @Test
    fun failedRefreshKeepsLastProgrammeData() {
        val source = setOf("https://one.example/epg")
        val programme = Programme("news", Date(0), Date(3_000), "保留的节目")
        EpgCache.configureSources(source)
        EpgCache.update(mapOf("news" to listOf(programme)), source)
        EpgCache.markAttemptFailed(source)
        assertEquals(programme, EpgCache.schedule(listOf("news"), Date(1_000)).current)
        EpgCache.configureSources(emptySet())
    }

    @Test
    fun catchupWindowChangeInvalidatesEpgWithoutRetryingEveryFrameAfterFailure() {
        val source = setOf("https://one.example/epg")
        EpgCache.configureSources(source)
        EpgCache.update(emptyMap(), source, catchupDays = 0)
        assertEquals(false, EpgCache.needsRefresh(source, 0))
        assertEquals(true, EpgCache.needsRefresh(source, 3))
        EpgCache.markAttemptFailed(source, 3)
        assertEquals(false, EpgCache.needsRefresh(source, 3))
        EpgCache.configureSources(emptySet())
    }

    @Test
    fun getProgrammeSchedule_returnsCurrentAndEarliestNextProgramme() {
        val now = Date(1_000_000)
        val current = Programme("news", Date(900_000), Date(1_100_000), "午间新闻")
        val later = Programme("news", Date(1_300_000), Date(1_400_000), "天气预报")
        val next = Programme("news", Date(1_100_000), Date(1_200_000), "财经报道")
        val otherChannel = Programme("sports", Date(1_050_000), Date(1_150_000), "体育新闻")

        val schedule = getProgrammeSchedule(
            listOf(later, otherChannel, current, next),
            listOf("news"),
            now
        )

        assertEquals(current, schedule.current)
        assertEquals(next, schedule.next)
    }

    @Test
    fun getProgrammeSchedule_treatsProgrammeEndAsExclusive() {
        val boundary = Date(2_000_000)
        val previous = Programme("news", Date(1_000_000), boundary, "上一档")
        val current = Programme("news", boundary, Date(3_000_000), "当前档")

        val schedule = getProgrammeSchedule(listOf(previous, current), listOf("news"), boundary)

        assertEquals(current, schedule.current)
        assertNull(schedule.next)
    }

    @Test
    fun getProgrammeSchedule_matchesChannelIdsIgnoringCaseAndWhitespace() {
        val now = Date(2_000)
        val current = Programme("CCTV1", Date(1_000), Date(3_000), "新闻")

        assertEquals(
            current,
            getProgrammeSchedule(listOf(current), listOf(" cctv1 "), now).current
        )
    }

    @Test
    fun guideCanQuerySevenDayWindowWithoutReturningAdjacentProgrammes() {
        val source = setOf("https://guide.example/xmltv")
        val start = Date(1_000_000L)
        val included = Programme("news", Date(2_000_000L), Date(3_000_000L), "第七天节目")
        val before = Programme("news", Date(0L), Date(999_999L), "之前")
        val after = Programme("news", Date(8_000_000L), Date(9_000_000L), "之后")
        EpgCache.configureSources(source)
        EpgCache.update(mapOf("news" to listOf(before, included, after)), source)

        assertEquals(listOf(included), EpgCache.guide(listOf("news"), start, Date(7_000_000L)))
        EpgCache.configureSources(emptySet())
    }
}
