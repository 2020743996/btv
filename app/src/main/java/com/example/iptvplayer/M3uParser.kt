package com.example.iptvplayer

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GROUP_ATTRIBUTE = Regex("group-title=\\\"([^\\\"]*)\\\"")
private val TVG_ID_ATTRIBUTE = Regex("tvg-id=\\\"([^\\\"]*)\\\"")
private val TVG_NAME_ATTRIBUTE = Regex("tvg-name=\\\"([^\\\"]*)\\\"")
private val TVG_LOGO_ATTRIBUTE = Regex("tvg-logo=\\\"([^\\\"]*)\\\"")
private val CATCHUP_ATTRIBUTE = Regex("catchup=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
private val CATCHUP_SOURCE_ATTRIBUTE = Regex("catchup-source=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
private val CATCHUP_DAYS_ATTRIBUTE = Regex("catchup-days=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
private const val MAX_CATCHUP_DAYS = 365
private val EPG_URL_ATTRIBUTE = Regex("(?:x-tvg-url|url-tvg)=\\\"([^\\\"]*)\\\"")
private val CHANNEL_SEPARATORS = Regex("[\\s\\-_.·、()（）]")
private val QUALITY_SUFFIXES = Regex("(高清|超清|标清|uhd|fhd|hd|sd|1080p|720p|4k|2160p|flv|ts|hls)+$")

data class CatchupMetadata(val mode: String, val source: String, val days: Int?)

fun buildCatchupPlaybackUrl(
    liveUrl: String,
    metadata: CatchupMetadata?,
    programme: Programme,
    nowMillis: Long = System.currentTimeMillis()
): String? {
    val catchup = metadata ?: return null
    val days = catchup.days?.takeIf { it > 0 } ?: return null
    if (catchup.mode !in setOf("default", "append") || catchup.source.isBlank()) return null
    if (programme.end.time <= programme.start.time || programme.end.time > nowMillis) return null
    if (programme.start.time < nowMillis - days.toLong() * 86_400_000L) return null

    val startSeconds = programme.start.time / 1000L
    val endSeconds = programme.end.time / 1000L
    val durationSeconds = ((programme.end.time - programme.start.time) / 1000L).coerceAtLeast(1L)
    val utc = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC)
        .format(programme.start.toInstant())
    var template = catchup.source
        .replace("{utcend}", endSeconds.toString())
        .replace("${'$'}{end}", endSeconds.toString())
        .replace("{utc}", startSeconds.toString())
        .replace("${'$'}{start}", startSeconds.toString())
        .replace("{lutc}", (nowMillis / 1000L).toString())
        .replace("${'$'}{now}", (nowMillis / 1000L).toString())
        .replace("${'$'}{timestamp}", (nowMillis / 1000L).toString())
        .replace("{Y}", utc.substring(0, 4))
        .replace("{m}", utc.substring(4, 6))
        .replace("{d}", utc.substring(6, 8))
        .replace("{H}", utc.substring(8, 10))
        .replace("{M}", utc.substring(10, 12))
        .replace("{S}", utc.substring(12, 14))
        .replace("{duration}", durationSeconds.toString())
        .replace("${'$'}{duration}", durationSeconds.toString())
    template = Regex("\\{duration:(\\d+)\\}").replace(template) { match ->
        val divisor = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return@replace match.value
        (durationSeconds / divisor).toString()
    }
    return when (catchup.mode) {
        "default" -> template
        "append" -> when {
            template.startsWith("?") && '?' in liveUrl -> liveUrl + "&" + template.drop(1)
            template.startsWith("&") && '?' !in liveUrl -> liveUrl + "?" + template.drop(1)
            else -> liveUrl + template
        }
        else -> null
    }
}

fun catchupPlaybackUrls(channel: Channel, programme: Programme, nowMillis: Long = System.currentTimeMillis()): List<String> =
    channel.urls.mapNotNull { url ->
        buildCatchupPlaybackUrl(url, channel.catchupByUrl[url], programme, nowMillis)
    }.distinct()

internal fun maxCatchupHistoryDays(channels: List<Channel>): Int = channels.asSequence()
    .flatMap { it.catchupByUrl.values.asSequence() }
    .mapNotNull { it.days?.takeIf { days -> days > 0 } }
    .maxOrNull() ?: 0

/**
 * 一个频道的信息。
 * 注意：urls 是"线路列表"——同一个频道可能来自多个 M3U 源，
 * 每个源给一条播放地址。第一条是首选线路，后面的都是备用。
 * 这是"线路聚合"的数据基础：列表页只显示一个频道，播放时按质量选线路。
 */
data class Channel(
    val name: String,        // 频道名，例如 "CCTV-1"
    val group: String,       // 分组，例如 "央视"，用于列表里归类
    val urls: List<String>,  // 所有线路的播放地址，例如 ["https://a.m3u8", "https://b.m3u8"]
    // 每条线路的检测结果（null = 还没测速）。
    // 测速后 urls 会按分辨率和网络质量重新排序：高清稳定线路在前，失效线路在后。
    val lineQuality: List<LineQuality>? = null,
    // EPG 标识列表：合并自所有源（tvg-id 或 tvg-name 属性）。
    // 不同源给同一频道的标识可能不同（如 "CCTV1" vs "CCTV1.us@SD"），
    // 全部保留，匹配节目单时任一命中即可
    val tvgIds: List<String> = emptyList(),
    // M3U 的 tvg-logo 地址；为空或加载失败时界面显示频道缩写占位。
    val logoUrl: String? = null,
    // 每条 URL 在原始 M3U 中对应的频道名/tvg-id，用于识别 CCTV4K 等线路级画质标记。
    val urlQualityHints: Map<String, String> = emptyMap(),
    // 原始完整线路集。测速可从 urls 隐藏失效线，但恢复时仍能找回，不必重新下载 M3U。
    val allUrls: List<String> = urls,
    val catchupByUrl: Map<String, CatchupMetadata> = emptyMap()
)

/**
 * 把 M3U 文件的内容解析成频道列表。
 *
 * M3U 是 IPTV 常用的频道列表格式，它长这样：
 *
 * #EXTM3U
 * #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1 综合
 * http://example.com/cctv1.m3u8
 * #EXTINF:-1 group-title="卫视",湖南卫视
 * http://example.com/hunan.m3u8
 *
 * 规则：
 * - 以 #EXTINF 开头的行，描述"下一个频道"的信息（名字在最后一个逗号后面，分组在 group-title 里）
 * - 紧跟着 EXTINF 的下一行，就是这个频道的播放地址
 *
 * 注意：真实的 M3U 数据经常不规范（缺字段、格式奇怪），
 * 所以解析时要容错——缺名字就用"未命名频道"，缺分组就用"未分组"。
 */
fun parseM3u(text: String): List<Channel> {
    val channels = mutableListOf<Channel>()

    // 先记住"最近一条 EXTINF 里声明的名字和分组"，
    // 等到出现播放地址那一行时，把它们配对成一个频道
    var pendingName: String? = null
    var pendingGroup: String? = null
    var pendingTvgId: String? = null
    var pendingLogoUrl: String? = null
    var pendingCatchup: CatchupMetadata? = null

    // lineSequence 惰性逐行遍历：大 M3U（几十万行）不必先把所有行收集成一个中间列表。
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim() // 去掉行首行尾的空格

        when {
            // 情况 1：这一行是 EXTINF，声明下一个频道的名字和分组
            line.startsWith("#EXTINF") -> {
                // 从属性里抠出 group-title="xxx" 中的 xxx
                val groupMatch = GROUP_ATTRIBUTE.find(line)
                pendingGroup = groupMatch?.groupValues?.get(1)

                // EPG 标识：优先取 tvg-id，没有就用 tvg-name（很多源只有 tvg-name）
                val tvgIdMatch = TVG_ID_ATTRIBUTE.find(line)
                pendingTvgId = tvgIdMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: TVG_NAME_ATTRIBUTE.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                pendingLogoUrl = TVG_LOGO_ATTRIBUTE.find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val catchupMode = CATCHUP_ATTRIBUTE.find(line)?.groupValues?.get(1)
                    ?.trim()?.lowercase(Locale.ROOT)
                val catchupSource = CATCHUP_SOURCE_ATTRIBUTE.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                val catchupDays = CATCHUP_DAYS_ATTRIBUTE.find(line)?.groupValues?.get(1)
                    ?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(MAX_CATCHUP_DAYS)
                pendingCatchup = if (catchupMode in setOf("default", "append") && catchupSource.isNotBlank()) {
                    CatchupMetadata(catchupMode!!, catchupSource, catchupDays)
                } else null

                // 频道名在最后一个逗号后面，例如 ...group-title="央视",CCTV-1 综合
                val commaIndex = line.lastIndexOf(',')
                pendingName = if (commaIndex >= 0) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    "未命名频道"
                }
            }

            // 情况 2：空行或注释行（#EXTVLCOPT 之类的扩展信息），直接忽略
            line.isEmpty() || line.startsWith("#") -> {
                // 什么都不做
            }

            // 情况 3：其他行都当作播放地址，和之前记住的名字配对成一个频道
            else -> {
                val channelName = pendingName ?: "未命名频道"
                val qualityHint = listOfNotNull(channelName, pendingTvgId)
                    .joinToString(separator = " ")
                channels.add(
                    Channel(
                        name = channelName,
                        group = pendingGroup ?: "未分组",
                        urls = listOf(line), // 单个源解析出来，每个频道只有这一条线路
                        tvgIds = listOfNotNull(pendingTvgId),
                        logoUrl = pendingLogoUrl,
                        urlQualityHints = mapOf(line to qualityHint),
                        catchupByUrl = pendingCatchup?.let { mapOf(line to it) }.orEmpty()
                    )
                )
                // 配对完就清空，防止漏掉 EXTINF 时把旧名字错配给下一个频道
                pendingName = null
                pendingGroup = null
                pendingTvgId = null
                pendingLogoUrl = null
                pendingCatchup = null
            }
        }
    }

    return channels
}

/**
 * 从 M3U 文件开头提取 EPG（节目单）地址。
 * 规范的 M3U 第一行会写：#EXTM3U x-tvg-url="https://xxx/e.xml"
 * 返回 null 表示这个源没有节目单。
 */
fun extractEpgUrls(m3uText: String): List<String> {
    // 只需要第一行，用惰性行序列避免把整个大 M3U 切成一个列表。
    val firstLine = m3uText.lineSequence().firstOrNull() ?: return emptyList()
    return EPG_URL_ATTRIBUTE.findAll(firstLine)
        .flatMap { match -> match.groupValues[1].split(',', ';').asSequence() }
        .map { it.trim() }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .distinct()
        .toList()
}

fun extractEpgUrl(m3uText: String): String? = extractEpgUrls(m3uText).firstOrNull()

/**
 * 频道名标准化：把各种写法统一成"比较用"的归一化名字。
 *
 * 真实世界的 IPTV 源里，同一个频道有各种写法：
 *   "CCTV-1"、"CCTV1"、"CCTV1高清"、"cctv1"
 * 归一化规则（按顺序）：
 *   1. 转小写
 *   2. 去掉空格、连字符、点号等分隔符
 *   3. 去掉"高清/超清/HD/720p"这类画质后缀
 * 例如 "CCTV-1 高清" → "cctv1"。
 * 注意：这只影响"合并判断"，显示时仍用原始名字。
 */
fun normalizeChannelName(name: String): String {
    var n = name.lowercase()
    n = n.replace(CHANNEL_SEPARATORS, "")
    n = n.replace(QUALITY_SUFFIXES, "")
    return n.trim()
}

/**
 * 把多个源解析出来的频道合并成一份列表（线路聚合）。
 *
 * 规则：频道名标准化后相同的合并为一个频道，线路全部保留（去重）；
 * 分组取"第一次出现"的那个。标准化的目的是把 CCTV1、CCTV-1、CCTV1高清
 * 这类"实际是同一个台"的写法识别为同一频道。
 */
fun mergeChannels(allChannels: List<Channel>): List<Channel> {
    // LinkedHashMap 保持"第一次出现"的顺序，列表顺序稳定
    val merged = LinkedHashMap<String, Channel>()

    for (channel in allChannels) {
        // 用标准化名字做合并判断
        val key = normalizeChannelName(channel.name).ifBlank { channel.name.lowercase().trim() }
        val existing = merged[key]

        if (existing == null) {
            // 第一次见到这个频道：原样放进结果（保留原始显示名）
            merged[key] = channel
        } else {
            // 见过：把新线路追加到已有线路后面（去重，顺序是"先来的在前"），
            // EPG 标识也合并去重（不同源给同一频道的标识可能不同）。
            // distinct() 保持首次出现的顺序，等效于"只追加没见过的"，但只需一趟哈希。
            val newUrls = (existing.urls + channel.urls).distinct()
            val newTvgIds = (existing.tvgIds + channel.tvgIds).distinct()
            val newQualityHints = channel.urlQualityHints + existing.urlQualityHints
            val newCatchupByUrl = channel.catchupByUrl + existing.catchupByUrl
            merged[key] = existing.copy(
                urls = newUrls,
                tvgIds = newTvgIds,
                logoUrl = existing.logoUrl ?: channel.logoUrl,
                urlQualityHints = newQualityHints,
                catchupByUrl = newCatchupByUrl,
                allUrls = newUrls
            )
        }
    }

    return merged.values.toList()
}

/**
 * 频道数据的内存缓存。
 * MainActivity 加载完频道后写进来，SearchActivity 直接读——
 * 避免搜索页再下载一次源。App 重启后缓存清空（下次加载再填）。
 */
object ChannelCache {
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    // @Volatile：频道数据可能被后台协程写入、被前台/搜索页读取，
    // 保证线程间立即可见（List 本身不可变，替换引用是原子的）。
    @Volatile
    var channels: List<Channel> = emptyList()
        private set

    @Volatile
    private var sourceUrls: Set<String> = emptySet()

    @Volatile
    private var loadedAtMillis: Long = 0L

    @Volatile
    private var complete: Boolean = true

    @Volatile
    var revision: Long = 0L
        private set

    @Synchronized
    fun update(channels: List<Channel>, sources: List<String>, complete: Boolean = true) {
        revision++
        this.channels = channels
        sourceUrls = sources.toSet()
        loadedAtMillis = System.currentTimeMillis()
        this.complete = complete
    }

    /** 返回仍在有效期内的缓存；源地址变化时不会误用旧数据。 */
    fun freshChannels(sources: List<String>): List<Channel>? {
        val age = System.currentTimeMillis() - loadedAtMillis
        return channels.takeIf {
            it.isNotEmpty() && sourceUrls == sources.toSet() && age in 0..(if (complete) CACHE_TTL_MS else 60_000L)
        }
    }

    /** 网络临时失败时可以回退到同一批源的旧缓存。 */
    fun staleChannels(sources: List<String>): List<Channel>? =
        channels.takeIf { it.isNotEmpty() && sourceUrls == sources.toSet() }

    /** 测速只改变频道顺序和线路，不应重置缓存时间。 */
    @Synchronized
    fun replaceChannels(channels: List<Channel>) {
        this.channels = channels
    }

    @Synchronized
    fun replaceIfCurrent(expectedRevision: Long, channels: List<Channel>): Boolean {
        if (revision != expectedRevision) return false
        // 保留测速期间由播放器写入的实测分辨率。
        val measurements = this.channels.flatMap { it.lineQuality.orEmpty() }
            .mapNotNull { quality -> quality.measuredResolution?.let { quality.url to it } }.toMap()
        this.channels = channels.map { channel ->
            val qualities = channel.lineQuality?.map { it.copy(measuredResolution = measurements[it.url] ?: it.measuredResolution) }
            val ranked = sortUsableLines(qualities.orEmpty()).map { it.url }.filter { it in channel.urls }
            channel.copy(lineQuality = qualities, urls = ranked + channel.urls.filter { it !in ranked })
        }
        revision++
        return true
    }

    /** 失效线复测成功后立即放回对应频道，不必等待用户强制刷新源。 */
    @Synchronized
    fun restoreLine(url: String, quality: LineQuality? = null) {
        revision++
        channels = channels.map { channel ->
            if (url !in channel.allUrls) return@map channel
            val restoredUrls = (channel.urls + url).distinct()
            val qualities = channel.lineQuality.orEmpty()
                .filterNot { it.url == url } + listOfNotNull(quality)
            val rankedUrls = sortUsableLines(qualities).map { it.url }
            channel.copy(
                urls = (rankedUrls.filter { it in restoredUrls } +
                    restoredUrls.filter { it !in rankedUrls }).distinct(),
                lineQuality = qualities.takeIf { it.isNotEmpty() }
            )
        }
    }

    /** 清空失效记录后恢复每个频道的完整原始线路，并清除过期测速状态。 */
    @Synchronized
    fun restoreAllLines() {
        revision++
        channels = channels.map { channel ->
            channel.copy(urls = channel.allUrls.distinct(), lineQuality = null)
        }
    }

    /** 播放器拿到真实视频格式后回写清晰度，列表和下次播放可直接受益。 */
    @Synchronized
    fun updateLineResolution(channelName: String, url: String, resolution: StreamResolution) {
        channels = channels.map { channel ->
            if (channel.name != channelName || url !in channel.allUrls) return@map channel
            val existing = channel.lineQuality.orEmpty().firstOrNull { it.url == url }
            val updated = (channel.lineQuality.orEmpty().filterNot { it.url == url } +
                (existing?.copy(usable = true, measuredResolution = resolution)
                    ?: LineQuality(url, usable = true, latencyMs = null, score = 70,
                        resolution = resolution, measuredResolution = resolution)))
            val rankedUrls = sortUsableLines(updated).map { it.url }
            channel.copy(
                urls = (rankedUrls.filter { it in channel.urls } +
                    channel.urls.filter { it !in rankedUrls }).distinct(),
                lineQuality = updated
            )
        }
    }

    @Synchronized
    fun invalidate() {
        revision++
        loadedAtMillis = 0L
    }
}
