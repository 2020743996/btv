package com.example.iptvplayer

/** 面向直播的稳态参数，集中维护以避免播放器各处出现互相冲突的超时值。 */
internal object PlaybackTuning {
    const val MIN_BUFFER_MS = 10_000
    const val MAX_BUFFER_MS = 40_000
    const val START_BUFFER_MS = 1_500
    const val REBUFFER_MS = 5_000
    const val BACK_BUFFER_MS = 5_000
    const val LIVE_TARGET_OFFSET_MS = 10_000L
    const val INITIAL_TIMEOUT_MS = 18_000L
    const val STALL_TIMEOUT_MS = 30_000L
    const val STABLE_PLAYBACK_MS = 15_000L
    const val LOAD_RETRY_COUNT = 5

    fun timeoutMs(playbackStarted: Boolean): Long =
        if (playbackStarted) STALL_TIMEOUT_MS else INITIAL_TIMEOUT_MS
}

/** 历史失败更少的线路优先，同分时保留源列表和测速后的原始顺序。 */
internal fun prioritizePlaybackUrls(
    urls: List<String>,
    failureCount: (String) -> Int
): List<String> = urls.withIndex()
    .sortedWith(compareBy<IndexedValue<String>> { failureCount(it.value) }.thenBy { it.index })
    .map { it.value }
