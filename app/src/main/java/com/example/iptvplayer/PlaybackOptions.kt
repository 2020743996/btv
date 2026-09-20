package com.example.iptvplayer

enum class PictureMode(val label: String) {
    FIT("适应画面"), ZOOM("裁切铺满"), FILL("拉伸铺满");

    companion object {
        fun fromStored(value: String?): PictureMode = entries.firstOrNull { it.name == value } ?: FIT
    }
}

internal fun nextUntriedLine(urls: List<String>, current: Int, failed: Set<String>): Int? =
    (1..urls.size).map { (current + it).mod(urls.size) }
        .firstOrNull { urls[it] !in failed }

internal class SleepTimer {
    var deadline: Long? = null
        private set
    var durationMinutes: Int = 0
        private set

    fun set(minutes: Int, now: Long) {
        require(minutes in listOf(0, 15, 30, 60, 90, 120))
        durationMinutes = minutes
        deadline = if (minutes == 0) null else now + minutes * 60_000L
    }

    fun remainingMinutes(now: Long): Int? = deadline?.let {
        ((it - now).coerceAtLeast(0) + 59_999L).div(60_000L).toInt()
    }

    fun isExpired(now: Long): Boolean = deadline?.let { now >= it } ?: false
}
