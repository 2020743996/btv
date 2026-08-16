package com.example.iptvplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * 解析 XMLTV 文本，返回所有节目。
 * 用安卓自带的 XmlPullParser 解析 XML（不用正则——XML 结构复杂，正则容易出错）。
 */
fun parseXmltv(text: String): List<Programme> {
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

            XmlPullParser.TEXT -> if (inTitle) title = parser.text

            XmlPullParser.END_TAG -> when (parser.name) {
                "title" -> inTitle = false
                "programme" -> {
                    val start = parseXmltvTime(startText)
                    val end = parseXmltvTime(stopText)
                    // 时间格式异常的数据直接跳过（常见：有的源时间字段缺失）
                    if (channelId != null && start != null && end != null) {
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

/** 解析 XMLTV 的时间格式："20260815090000 +0800" → Date */
private fun parseXmltvTime(text: String): Date? {
    return try {
        // Z 匹配 "+0800" 这种时区写法
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(text)
    } catch (e: Exception) {
        null
    }
}

/**
 * 从节目列表里找出某个频道"此刻正在播"的节目。
 * 规则：当前时间在节目的开始~结束之间，且频道的任意 EPG 标识命中。
 * 频道可能有多个 tvgId（来自不同源），任一命中即可。
 * 找不到就返回 null（界面不显示）。
 */
fun getCurrentProgramme(programmes: List<Programme>, tvgIds: List<String>, now: Date): Programme? {
    if (tvgIds.isEmpty()) return null
    return programmes.firstOrNull { programme ->
        programme.channelId in tvgIds && now in programme.start..programme.end
    }
}

/**
 * 节目单缓存：MainActivity 下载解析后写进来，列表和播放页读。
 * 和 ChannelCache 一样的思路：避免重复下载。
 * 另外记录加载时间，返回列表时不会反复下载 8MB 的 EPG。
 */
object EpgCache {
    private const val EPG_TTL_MS = 10 * 60 * 1000L

    @Volatile
    var programmes: List<Programme> = emptyList()
        private set

    @Volatile
    private var programmesByChannel: Map<String, List<Programme>> = emptyMap()

    @Volatile
    private var loadedAtMillis: Long = 0L

    /** programmesByChannel 在后台线程构建好再整体传入，避免主线程做大的 groupBy。 */
    @Synchronized
    fun update(programmes: List<Programme>, programmesByChannel: Map<String, List<Programme>>) {
        this.programmes = programmes
        this.programmesByChannel = programmesByChannel
        loadedAtMillis = System.currentTimeMillis()
    }

    /** 是否已超过有效期需要重新下载（避免频繁进出播放页反复拉 8MB EPG）。 */
    fun needsRefresh(): Boolean =
        System.currentTimeMillis() - loadedAtMillis >= EPG_TTL_MS

    /** 只扫描当前频道的节目，不再为列表中的每一行遍历整份 EPG。 */
    fun currentProgramme(tvgIds: List<String>, now: Date = Date()): Programme? {
        for (tvgId in tvgIds) {
            val match = programmesByChannel[tvgId]
                ?.firstOrNull { now in it.start..it.end }
            if (match != null) return match
        }
        return null
    }
}

/**
 * 从 M3U 文件里提取 EPG 地址并下载解析，结果存进 EpgCache。
 * 这是"增强功能"：任何一步失败都静默跳过，不影响频道列表。
 *
 * 性能注意：EPG 源（如 fanmingming e.xml）可能高达数 MB、几十万条节目。
 * - 下载在 IO 线程（downloadM3u 内部切 IO）
 * - 解析 + 过滤 + 按频道分组都是 CPU 密集，放在 Dispatchers.Default 上跑，
 *   绝不能占主线程，否则会阻塞界面/按键导致 ANR。
 * - 只保留"当前时刻附近"的节目（界面只需"现在播什么"），降低内存占用。
 */
suspend fun loadEpg(context: Context, m3uText: String?) {
    val epgUrl = m3uText?.let { extractEpgUrl(it) } ?: return
    if (!EpgCache.needsRefresh()) return // 10 分钟内加载过就不再下载
    try {
        val text = downloadM3u(epgUrl)
        val now = System.currentTimeMillis()
        // 保留窗口：开始时间不晚于"现在 + 24h"，结束时间不早于"现在 - 2h"。
        val maxStart = now + 24 * 3600_000L
        val minEnd = now - 2 * 3600_000L
        val (programmes, byChannel) = withContext(Dispatchers.Default) {
            val list = parseXmltv(text).filter { p ->
                p.start.time < maxStart && p.end.time > minEnd
            }
            list to list.groupBy { it.channelId }
        }
        EpgCache.update(programmes, byChannel)
        AppLog.log("EPG 加载成功：${programmes.size} 条节目")
    } catch (e: Exception) {
        // 记录具体原因方便排查（下载超时 / 域名不可达 / 解析异常等）。
        AppLog.log("EPG 加载失败：${e.message ?: e.javaClass.simpleName}")
        android.util.Log.w("IptvPlayer", "EPG 加载失败 $epgUrl", e)
    }
}

/** 某个频道此刻正在播的节目名（没有节目单/没匹配到就返回 null） */
fun currentProgrammeTitle(channel: Channel): String? =
    EpgCache.currentProgramme(channel.tvgIds)?.title
