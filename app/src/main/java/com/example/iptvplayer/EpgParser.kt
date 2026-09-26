package com.example.iptvplayer

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EPG（电子节目单）解析器。
 *
 * EPG 数据用 XMLTV 格式，长这样：
 *
 * <tv>
 *   <channel id="CCTV1">
 *     <display-name>CCTV1</display-name>
 *   </channel>
 *   <programme start="20260815090000 +0800" stop="20260815100000 +0800" channel="CCTV1">
 *     <title>朝闻天下</title>
 *   </programme>
 * </tv>
 *
 * 每个 <programme> 是"一个频道在某时间段播出的一个节目"。
 * 我们要做的：把所有节目解析出来，然后按"当前时间落在哪个时间段里"
 * 查出每个频道"现在正在播什么"。
 */

/** 一个节目：频道标识 + 起止时间 + 节目名 */
data class Programme(
    val channelId: String, // 对应 M3U 里频道的 tvg-id/tvg-name
    val start: Date,
    val end: Date,
    val title: String
)

/** 去重用的节目身份键：去重比的是归一化后的频道标识，而非原始字符串。 */
private data class ProgrammeKey(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String
)

/** 一个频道此刻的节目状态：正在播的节目，以及紧随其后的下一档。 */
data class ProgrammeSchedule(
    val current: Programme?,
    val next: Programme?
)

/**
 * 解析 XMLTV 文本，返回所有节目。
 * 用安卓自带的 XmlPullParser 解析 XML（不用正则——XML 结构复杂，正则容易出错）。
 */
fun parseXmltv(
    text: String,
    startBeforeMillis: Long? = null,
    endAfterMillis: Long? = null
): List<Programme> {
    val programmes = mutableListOf<Programme>()
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(StringReader(text))

    var channelId: String? = null
    var startText = ""
    var stopText = ""
    var title = ""
    var inTitle = false

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "programme" -> {
                    channelId = parser.getAttributeValue(null, "channel")
                    startText = parser.getAttributeValue(null, "start") ?: ""
                    stopText = parser.getAttributeValue(null, "stop") ?: ""
                    title = ""
                }
                "title" -> inTitle = true
            }

            XmlPullParser.TEXT -> if (inTitle) title += parser.text

            XmlPullParser.END_TAG -> when (parser.name) {
                "title" -> inTitle = false
                "programme" -> {
                    val start = parseXmltvTime(startText)
                    val end = parseXmltvTime(stopText)
                    // 时间格式异常的数据直接跳过（常见：有的源时间字段缺失）
                    if (channelId != null && start != null && end != null &&
                        isProgrammeInWindow(start.time, end.time, startBeforeMillis, endAfterMillis)
                    ) {
                        programmes.add(Programme(channelId, start, end, title.trim()))
                    }
                    channelId = null
                }
            }
        }
        eventType = parser.next()
    }
    return programmes
}

internal fun isProgrammeInWindow(
    programmeStartMillis: Long,
    programmeEndMillis: Long,
    startBeforeMillis: Long?,
    endAfterMillis: Long?
): Boolean = (startBeforeMillis == null || programmeStartMillis < startBeforeMillis) &&
    (endAfterMillis == null || programmeEndMillis > endAfterMillis)

// SimpleDateFormat 不是线程安全的，且构造开销大；EPG 解析可能在多个后台线程跑，
// 用 ThreadLocal 让每个线程复用各自的实例，而不是每解析一条节目就新建一个。
private val xmltvTimeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US) // Z 匹配 "+0800" 这种时区写法
}

/** 解析 XMLTV 的时间格式："20260815090000 +0800" → Date */
private fun parseXmltvTime(text: String): Date? {
    return try {
        xmltvTimeFormat.get()?.parse(text)
    } catch (e: Exception) {
        null
    }
}

/**
 * 同时找出当前节目和下一节目。节目时间按 [开始, 结束) 处理，避免整点交界时
 * 上一档和下一档同时被判定为正在播放。
 */
fun getProgrammeSchedule(
    programmes: List<Programme>,
    tvgIds: List<String>,
    now: Date
): ProgrammeSchedule {
    if (tvgIds.isEmpty()) return ProgrammeSchedule(null, null)
    val channelIds = tvgIds.map(::normalizeEpgChannelId).toHashSet()
    var current: Programme? = null
    var next: Programme? = null

    for (programme in programmes) {
        if (normalizeEpgChannelId(programme.channelId) !in channelIds) continue
        val isCurrent = !now.before(programme.start) && now.before(programme.end)
        if (isCurrent && (current == null || programme.start.before(current.start))) {
            current = programme
        }
        if (programme.start.after(now) && (next == null || programme.start.before(next.start))) {
            next = programme
        }
    }
    return ProgrammeSchedule(current, next)
}

/**
 * 在一个已经过滤好的单频道节目列表里找当前/下一档节目。
 * 与 [getProgrammeSchedule] 的判定规则完全一致，只是省去了逐条节目的
 * channelId 归一化比较——调用方保证列表里只有这一个频道的节目。
 * 列表页每个频道行都会查一次节目单，这个快路径避免每次都归一化整列节目名。
 */
private fun findScheduleInChannel(
    programmes: List<Programme>,
    now: Date
): ProgrammeSchedule {
    var current: Programme? = null
    var next: Programme? = null

    for (programme in programmes) {
        val isCurrent = !now.before(programme.start) && now.before(programme.end)
        if (isCurrent && (current == null || programme.start.before(current.start))) {
            current = programme
        }
        if (programme.start.after(now) && (next == null || programme.start.before(next.start))) {
            next = programme
        }
    }
    return ProgrammeSchedule(current, next)
}

internal fun normalizeEpgChannelId(value: String): String = value.trim().lowercase(Locale.ROOT)

/**
 * 节目单缓存：MainActivity 下载解析后写进来，列表和播放页读。
 * 和 ChannelCache 一样的思路：避免重复下载。
 * 另外记录加载时间，返回列表时不会反复下载 8MB 的 EPG。
 * 只保留"按频道分组"这一份节目列表：所有查询都走 schedule，
 * 再单独存一份整表只会让大 EPG 的引用数组在内存里翻倍。
 */
object EpgCache {
    private const val EPG_TTL_MS = 10 * 60 * 1000L

    @Volatile
    private var programmesByChannel: Map<String, List<Programme>> = emptyMap()

    @Volatile
    private var loadedAtMillis: Long = 0L

    @Volatile
    private var complete: Boolean = true

    @Volatile
    private var sourceUrls: Set<String> = emptySet()

    @Volatile
    var configuredUrls: Set<String> = emptySet()
        private set

    @Synchronized
    fun configureSources(urls: Set<String>) {
        if (configuredUrls == urls) return
        configuredUrls = urls
        sourceUrls = emptySet()
        programmesByChannel = emptyMap()
        loadedAtMillis = 0L
        complete = true
        SourceStatuses.epg = emptyList()
    }

    /** programmesByChannel 在后台线程构建好再整体传入，避免主线程做大的 groupBy。 */
    @Synchronized
    fun update(programmesByChannel: Map<String, List<Programme>>, sourceUrls: Set<String>, complete: Boolean = true) {
        if (sourceUrls != configuredUrls) return
        this.programmesByChannel = programmesByChannel
        this.sourceUrls = sourceUrls
        loadedAtMillis = System.currentTimeMillis()
        this.complete = complete
    }

    /** 是否已超过有效期需要重新下载（避免频繁进出播放页反复拉 8MB EPG）。 */
    fun needsRefresh(requestedSources: Set<String>): Boolean =
        requestedSources != sourceUrls ||
            System.currentTimeMillis() - loadedAtMillis >= (if (complete) EPG_TTL_MS else 60_000L)

    @Synchronized
    fun markAttemptFailed(requestedSources: Set<String>) {
        if (requestedSources != configuredUrls) return
        sourceUrls = requestedSources
        loadedAtMillis = System.currentTimeMillis()
        complete = false
    }

    @Synchronized
    fun invalidate() {
        loadedAtMillis = 0L
    }

    /** 只扫描当前频道的节目，不再为列表中的每一行遍历整份 EPG。 */
    fun schedule(tvgIds: List<String>, now: Date = Date()): ProgrammeSchedule {
        if (tvgIds.isEmpty()) return ProgrammeSchedule(null, null)
        var current: Programme? = null
        var next: Programme? = null
        for (tvgId in tvgIds) {
            val programmes = programmesByChannel[normalizeEpgChannelId(tvgId)].orEmpty()
            val match = findScheduleInChannel(programmes, now)
            if (match.current != null &&
                (current == null || match.current.start.before(current.start))
            ) {
                current = match.current
            }
            if (match.next != null && (next == null || match.next.start.before(next.start))) {
                next = match.next
            }
        }
        return ProgrammeSchedule(current, next)
    }

    fun currentProgramme(tvgIds: List<String>, now: Date = Date()): Programme? =
        schedule(tvgIds, now).current

    fun guide(tvgIds: List<String>, now: Date = Date()): List<Programme> =
        guide(tvgIds, now, Date(now.time + DEFAULT_GUIDE_WINDOW_MS))

    fun guide(tvgIds: List<String>, start: Date, end: Date): List<Programme> {
        if (end <= start) return emptyList()
        val snapshot = programmesByChannel
        return tvgIds.map(::normalizeEpgChannelId).distinct()
            .flatMap { snapshot[it].orEmpty() }
            .filter { it.end.after(start) && it.end.after(it.start) && it.start.before(end) }
            .distinctBy { Triple(it.start.time, it.end.time, it.title) }
            .sortedBy { it.start }
    }

    fun programmesByChannel(tvgIds: List<String>, start: Date, end: Date): Map<String, List<Programme>> {
        if (end <= start) return emptyMap()
        val snapshot = programmesByChannel
        return tvgIds.map(::normalizeEpgChannelId).distinct().mapNotNull { channelId ->
            val matches = snapshot[channelId].orEmpty().filter {
                it.end.after(start) && it.end.after(it.start) && it.start.before(end)
            }
            matches.takeIf { it.isNotEmpty() }?.let { channelId to it }
        }.toMap()
    }
}

private const val GUIDE_HORIZON_MS = 7L * 24 * 60 * 60 * 1000
private const val DEFAULT_GUIDE_WINDOW_MS = 24L * 60 * 60 * 1000

/**
 * 从 M3U 文件里提取 EPG 地址并下载解析，结果存进 EpgCache。
 * 这是"增强功能"：任何一步失败都静默跳过，不影响频道列表。
 *
 * 性能注意：EPG 源（如 fanmingming e.xml）可能高达数 MB、几十万条节目。
 * - 多个 EPG 源互不依赖，并行下载解析，慢源不再拖长整体等待
 * - 下载在 IO 线程（downloadM3u 内部切 IO）
 * - 解析 + 过滤 + 按频道分组都是 CPU 密集，放在 Dispatchers.Default 上跑，
 *   绝不能占主线程，否则会阻塞界面/按键导致 ANR
 * - 在解析阶段只保留最近节目到未来七天的时间窗，支持完整节目浏览并限制内存占用
 */
private val epgRefreshLock = Mutex()

suspend fun refreshConfiguredEpg(context: Context) = epgRefreshLock.withLock {
    val epgUrls = EpgCache.configuredUrls
    if (epgUrls.isEmpty() || !EpgCache.needsRefresh(epgUrls)) return@withLock
    val snapshots = SourceSnapshots(context)

    val now = System.currentTimeMillis()
    val maxStart = now + GUIDE_HORIZON_MS
    val minEnd = now - 2 * 3600_000L

    val perSource = coroutineScope {
        val limit = Semaphore(2)
        epgUrls.map { epgUrl ->
            async {
                try {
                    limit.withPermit {
                        val text = downloadM3u(epgUrl)
                        val programmes = withContext(Dispatchers.Default) {
                            parseXmltv(text, startBeforeMillis = maxStart, endAfterMillis = minEnd)
                        }
                        try {
                            snapshots.save("epg", epgUrl, text)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            AppLog.log("节目单快照保存失败")
                        }
                        programmes to SourceStatus(epgUrl, SourceHealth.NORMAL, programmes.size)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cached = snapshots.load("epg", epgUrl)
                    val programmes = cached?.let { text ->
                        withContext(Dispatchers.Default) {
                            runCatching { parseXmltv(text, startBeforeMillis = maxStart, endAfterMillis = minEnd) }.getOrNull()
                        }
                    }
                    if (programmes != null) {
                        programmes to SourceStatus(epgUrl, SourceHealth.STALE, programmes.size, "连接失败，使用上次数据")
                    } else {
                        emptyList<Programme>() to SourceStatus(epgUrl, SourceHealth.FAILED, 0, "连接失败")
                    }
                }
            }
        }.awaitAll()
    }
    if (epgUrls != EpgCache.configuredUrls) return@withLock
    SourceStatuses.epg = perSource.map { it.second }
    val collected = perSource.filter { it.second.health != SourceHealth.FAILED }.map { it.first }
    if (collected.isEmpty()) {
        EpgCache.markAttemptFailed(epgUrls)
        return@withLock
    }

    val (byChannel, programmeCount) = withContext(Dispatchers.Default) {
        val merged = collected
            .flatten()
            .distinctBy { programme ->
                ProgrammeKey(
                    normalizeEpgChannelId(programme.channelId),
                    programme.start.time,
                    programme.end.time,
                    programme.title
                )
            }
            .sortedBy { it.start }
        merged.groupBy { normalizeEpgChannelId(it.channelId) } to merged.size
    }
    EpgCache.update(byChannel, epgUrls, complete = perSource.all { it.second.health == SourceHealth.NORMAL })
    AppLog.log(
        "EPG 加载完成：${perSource.count { it.second.health == SourceHealth.NORMAL }}/${epgUrls.size} 个源正常，" +
            "$programmeCount 条节目"
    )
}

/** 某个频道此刻正在播的节目名（没有节目单/没匹配到就返回 null） */
fun currentProgrammeTitle(channel: Channel): String? =
    EpgCache.currentProgramme(channel.tvgIds)?.title
