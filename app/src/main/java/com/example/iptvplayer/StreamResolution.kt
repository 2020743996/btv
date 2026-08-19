package com.example.iptvplayer

data class StreamResolution(val width: Int, val height: Int) {
    val pixelCount: Long get() = width.toLong() * height

    val label: String
        get() = when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            else -> "${height}p"
        }
}

private val DIMENSION_PATTERN = Regex("(?i)(?:RESOLUTION\\s*=\\s*)?(\\d{3,5})x(\\d{3,5})")
private val HEIGHT_PATTERN = Regex("(?i)(?:^|[^0-9])(2160|1440|1080|720|576|480|360)p(?:[^0-9]|$)")
// 允许 CCTV4K / liveuhd 这类紧凑写法，但排除 14k、4kplus 等容易误判的片段。
private val FOUR_K_PATTERN = Regex("(?i)(?<!\\d)(?:4k|uhd)(?![a-z0-9])")
private val FULL_HD_PATTERN = Regex("(?i)fhd(?![a-z0-9])")

/** 从 HLS 清单、最终 URL 和该线路自己的 M3U 标签中识别最高视频分辨率。 */
internal fun detectStreamResolution(
    probe: ByteArray,
    finalUrl: String,
    hints: List<String> = emptyList()
): StreamResolution? {
    val manifestText = probe.toString(Charsets.UTF_8)
    val candidates = buildList {
        addAll(parseDimensions(manifestText))
        addAll(parseDimensions(finalUrl))
        inferNamedResolution(finalUrl)?.let(::add)
        hints.forEach { hint ->
            addAll(parseDimensions(hint))
            inferNamedResolution(hint)?.let(::add)
        }
    }
    return candidates.maxByOrNull { it.pixelCount }
}

private fun parseDimensions(text: String): List<StreamResolution> =
    DIMENSION_PATTERN.findAll(text).mapNotNull { match ->
        val width = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val height = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        StreamResolution(width, height).takeIf { width in 160..8192 && height in 120..4320 }
    }.toList() + HEIGHT_PATTERN.findAll(text).mapNotNull { match ->
        match.groupValues[1].toIntOrNull()?.let(::resolutionFromHeight)
    }.toList()

private fun inferNamedResolution(text: String): StreamResolution? = when {
    FOUR_K_PATTERN.containsMatchIn(text) -> StreamResolution(3840, 2160)
    FULL_HD_PATTERN.containsMatchIn(text) -> StreamResolution(1920, 1080)
    else -> null
}

private fun resolutionFromHeight(height: Int): StreamResolution {
    val width = when (height) {
        2160 -> 3840
        1440 -> 2560
        1080 -> 1920
        720 -> 1280
        576 -> 1024
        480 -> 854
        else -> 640
    }
    return StreamResolution(width, height)
}

internal fun sortUsableLines(results: List<LineQuality>): List<LineQuality> =
    results.filter { it.usable }.sortedWith(
        compareByDescending<LineQuality> { it.resolution?.pixelCount ?: 0L }
            .thenByDescending { it.score }
            .thenBy { it.latencyMs ?: Long.MAX_VALUE }
    )
