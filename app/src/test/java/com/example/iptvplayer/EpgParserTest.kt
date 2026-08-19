package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class EpgParserTest {

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
}
