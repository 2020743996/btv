package com.example.iptvplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Base64

/**
 * 软件里的"本地设置"都通过这个文件读写。
 *
 * SharedPreferences 是安卓最简单的持久化存储：一个"键 → 值"的字典，
 * 存在 App 自己的数据目录里，软件重启后还在。
 * 我们用的键：
 * - m3u_urls：M3U 频道源地址（可以多个）
 * - favorite_channels：收藏的频道名集合
 * - line_fail_counts：线路连续失败次数（疑似失效机制）
 * - elder_mode：老人模式开关
 * - font_size：字体大小（0 标准 / 1 大 / 2 特大）
 * - recent_channels：最近观看的频道名（按时间倒序）
 */

private const val PREFS_NAME = "iptv_settings"
private const val KEY_M3U_URLS = "m3u_urls"
private const val KEY_M3U_URLS_ORDERED = "m3u_urls_ordered"
private const val KEY_FAVORITES = "favorite_channels"
private const val KEY_FAIL_COUNTS = "line_fail_counts"
private const val KEY_ELDER_MODE = "elder_mode"
private const val KEY_FONT_SIZE = "font_size"
private const val KEY_RECENT = "recent_channels"
private val failRecordLock = Any()

/** 统一入口，避免每个函数重复拼 `getSharedPreferences`。 */
private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

/** 读取保存的所有 M3U 地址；没设置过就返回空列表（首次使用需要手动添加源） */
fun getM3uUrls(context: Context): List<String> {
    val p = prefs(context)
    p.getString(KEY_M3U_URLS_ORDERED, null)?.let { encodedText ->
        return decodeOrderedStringList(encodedText)
    }
    // 兼容旧版本：历史数据用 StringSet 存储，无法保证顺序，但仍要能读出来。
    return p.getStringSet(KEY_M3U_URLS, emptySet()).orEmpty().toList()
}

/** 保存 M3U 地址列表（自动去重并保留用户添加顺序） */
fun saveM3uUrls(context: Context, urls: List<String>) {
    val normalized = normalizeOrderedStringList(urls)
    prefs(context).edit()
        .putString(KEY_M3U_URLS_ORDERED, encodeOrderedStringList(normalized))
        // 继续写旧键，方便从旧版升级/回退时仍能读到地址。
        .putStringSet(KEY_M3U_URLS, normalized.toSet())
        .apply() // apply：异步写盘，不卡界面
}

fun normalizeOrderedStringList(values: List<String>): List<String> =
    values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun encodeOrderedStringList(values: List<String>): String =
    normalizeOrderedStringList(values).joinToString(separator = "\n") { value ->
        Base64.getUrlEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    }

fun decodeOrderedStringList(encodedText: String): List<String> = try {
    if (encodedText.isBlank()) {
        emptyList()
    } else {
        normalizeOrderedStringList(
            encodedText.lineSequence().map { encoded ->
                String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            }.toList()
        )
    }
} catch (e: Exception) {
    emptyList()
}

/** 读取收藏的频道名集合 */
fun getFavorites(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

/** 切换收藏状态：如果已收藏就取消，没收藏就加上。返回切换后的状态（true=已收藏） */
fun toggleFavorite(context: Context, channelName: String): Boolean {
    // getStringSet 返回的集合不允许直接修改，先复制成可变集合
    val favorites = prefs(context).getStringSet(KEY_FAVORITES, emptySet())
        ?.toMutableSet()
        ?: mutableSetOf()

    val nowFavorite = if (channelName in favorites) {
        favorites.remove(channelName)
        false
    } else {
        favorites.add(channelName)
        true
    }

    prefs(context).edit().putStringSet(KEY_FAVORITES, favorites).apply()
    return nowFavorite
}

// ============ 线路失败计数（疑似失效机制） ============
// 线路"连续失败"的次数。规则：
// - 测速失败一次 → 计数 +1，此时是"疑似失效"（还能用，但排后面）
// - 连续失败 2 次 → 判定"失效"，自动隐藏
// - 测速成功 → 计数清零（说明线路恢复了）
// 存储格式：StringSet，每个元素是 "地址|次数"，例如 "http://a.m3u8|2"
// 性能：测速会逐条线路读计数（getFailCount），如果每次都去扫描整个
// SharedPreferences 字符串集合，几千条线路时会反复做 O(n) 遍历。
// 所以进程内维护一份内存 Map，首次读取时从磁盘加载一次，之后都走内存。

private const val FAIL_THRESHOLD = 2

/** 进程内失败计数缓存：url → 连续失败次数。 */
private val failCounts = HashMap<String, Int>()
private var failCountsLoaded = false

/** 首次访问时把 SharedPreferences 里的记录加载进内存缓存。 */
private fun ensureFailCountsLoaded(context: Context) {
    synchronized(failRecordLock) {
        if (failCountsLoaded) return
        val entries = prefs(context).getStringSet(KEY_FAIL_COUNTS, emptySet()).orEmpty()
        for (entry in entries) {
            val separator = entry.lastIndexOf('|')
            if (separator > 0) {
                val count = entry.substring(separator + 1).toIntOrNull()
                if (count != null && count > 0) {
                    failCounts[entry.substring(0, separator)] = count
                }
            }
        }
        failCountsLoaded = true
    }
}

/** 把内存快照异步写回磁盘；进程内读取始终走锁保护的 Map，不需要阻塞调用线程。 */
private fun flushFailCounts(context: Context) {
    val entries = failCounts.map { (url, count) -> "$url|$count" }.toSet()
    prefs(context).edit().putStringSet(KEY_FAIL_COUNTS, entries).apply()
}

/** 读取某条线路的连续失败次数 */
fun getFailCount(context: Context, url: String): Int = synchronized(failRecordLock) {
    ensureFailCountsLoaded(context)
    failCounts[url] ?: 0
}

/** 记录一次测速结果：成功清零，失败 +1 */
fun recordLineResult(context: Context, url: String, success: Boolean) {
    recordLineResults(context, mapOf(url to success))
}

/** 批量记录线路结果，只生成并写入一次 SharedPreferences 快照。 */
fun recordLineResults(context: Context, results: Map<String, Boolean>) {
    if (results.isEmpty()) return
    synchronized(failRecordLock) {
        ensureFailCountsLoaded(context)
        for ((url, success) in results) {
            if (success) {
                failCounts.remove(url)
            } else {
                failCounts[url] = (failCounts[url] ?: 0) + 1
            }
        }
        flushFailCounts(context)
    }
}

/** 某条线路是否已被判定为失效（连续失败达到阈值） */
fun isLineDead(context: Context, url: String): Boolean =
    getFailCount(context, url) >= FAIL_THRESHOLD

/** 读取所有失败记录（回收站数据）：(线路地址, 连续失败次数) */
fun getAllFailRecords(context: Context): List<Pair<String, Int>> = synchronized(failRecordLock) {
    ensureFailCountsLoaded(context)
    failCounts.entries.map { it.key to it.value }.sortedByDescending { it.second }
}

/** 清除全部失败记录（回收站清空，所有线路恢复） */
fun clearFailRecords(context: Context) {
    synchronized(failRecordLock) {
        ensureFailCountsLoaded(context)
        failCounts.clear()
        prefs(context).edit().remove(KEY_FAIL_COUNTS).apply()
    }
}

// ============ 老人模式与字体 ============

/** 老人模式：开（true）后界面只保留电视、收藏和必要设置，隐藏高级功能 */
fun isElderMode(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ELDER_MODE, false)

fun setElderMode(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_ELDER_MODE, enabled).apply()
}

/** 字体大小档位：0 标准 / 1 大 / 2 特大 */
fun getFontSize(context: Context): Int =
    prefs(context).getInt(KEY_FONT_SIZE, 0)

fun setFontSize(context: Context, size: Int) {
    prefs(context).edit().putInt(KEY_FONT_SIZE, size).apply()
}

/** 字体档位 → 缩放比例：标准 1.0 倍，大 1.25 倍，特大 1.5 倍 */
fun fontScaleFor(size: Int): Float = when (size) {
    1 -> 1.25f
    2 -> 1.5f
    else -> 1.0f
}

// ============ 最近观看 ============
// 用 JSON 字符串保存，因为 SharedPreferences 的字符串集合不保证顺序，
// 而"最近观看"必须保持时间顺序（最新的在最前面）。

private const val MAX_RECENT = 20

/** 读取最近观看的频道名（最新的在最前） */
fun getRecentChannels(context: Context): List<String> {
    val json = prefs(context).getString(KEY_RECENT, null) ?: return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList() // 数据损坏时当作没有记录
    }
}

/** 记录一次观看：放到最前面，去重，最多保留 20 条 */
fun addRecentChannel(context: Context, channelName: String) {
    val recent = (listOf(channelName) + getRecentChannels(context))
        .distinct()
        .take(MAX_RECENT)
    prefs(context).edit()
        .putString(KEY_RECENT, JSONArray(recent).toString())
        .apply()
}

// ============ 搜索历史 ============
// 和"最近观看"同样的 JSON 数组存法（StringSet 不保序，历史需要时间序）。

private const val MAX_SEARCH_HISTORY = 10
private const val KEY_SEARCH_HISTORY = "search_history"

/** 读取最近的搜索词（最新的在最前） */
fun getSearchHistory(context: Context): List<String> {
    val json = prefs(context).getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

/** 记录一次搜索词：放到最前面，去重，最多保留 10 条 */
fun addSearchHistory(context: Context, query: String) {
    if (query.isBlank()) return
    val history = (listOf(query.trim()) + getSearchHistory(context))
        .distinct()
        .take(MAX_SEARCH_HISTORY)
    prefs(context).edit()
        .putString(KEY_SEARCH_HISTORY, JSONArray(history).toString())
        .apply()
}
