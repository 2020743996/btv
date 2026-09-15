package com.example.iptvplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

private val speedTestClient: OkHttpClient by lazy {
    sharedHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
}

/**
 * 线路质量检测器（测速）。
 *
 * 对每条线路做真实的网络请求，结合"本次实测 + 历史失败次数"打分：
 * - 先按分辨率、再按网络得分排序，兼顾清晰度与连接质量
 * - 首次失败的线路标记"疑似失效"（还能用，排后面）
 * - 连续失败 2 次的线路判定"失效"，自动隐藏
 * - 每次测速成功会清零失败计数——线路恢复了就能重新用
 *
 * 为什么用"下载清单文件"而不是更复杂的首帧测试：
 * IPTV 直播流绝大多数是 HLS（m3u8 清单 + 视频分片），
 * 能快速拿到清单说明线路"活着且快"；真正播放时的首帧由
 * 播放器的故障切换兜底。这对新手友好：逻辑简单、可靠。
 */

/** 一条线路的检测结果 */
data class LineQuality(
    val url: String,          // 这条线路的地址
    val usable: Boolean,      // true = 本次测速可用
    val latencyMs: Long?,     // 延迟毫秒数（不可用时为 null）
    val score: Int,           // 网络分 0-100：>=70 流畅，30-69 一般，<30 慢/不可用
    val resolution: StreamResolution? = null,
    val throughputKbps: Int? = null,
    val measuredResolution: StreamResolution? = null
)

/**
 * 检测一个频道的所有线路，返回整理后的频道。
 *
 * 整理规则（"一键优化"的核心逻辑）：
 * 1. 可用线路优先按分辨率从高到低排序，同分辨率再按网络分排序
 * 2. 保留全部可用线路，低清晰度源仍可作为播放回退
 * 3. 本次失败但历史失败 <2 次的：疑似失效，排到最后但还保留
 * 4. 连续失败达到 2 次的：判定失效，从列表剔除（自动隐藏）
 */
suspend fun testChannel(
    context: Context,
    channel: Channel,
    requestLimit: Semaphore = Semaphore(8)
): Channel {
    // 并发检测所有线路
    val results = coroutineScope {
        channel.allUrls.distinct().map { url ->
            async {
                requestLimit.withPermit {
                    testLine(url, channel.urlQualityHints[url])
                }
            }
        }.awaitAll()
    }
    return applyLineTestResults(channel, results) { getFailCount(context, it) }
}

internal fun applyLineTestResults(
    channel: Channel,
    results: List<LineQuality>,
    failureCount: (String) -> Int
): Channel {
    val known = channel.lineQuality.orEmpty().associateBy { it.url }
    val measured = results.map { it.copy(measuredResolution = known[it.url]?.measuredResolution) }
    val usable = sortUsableLines(measured)
    // 一次遍历把不可用线路分成"失效"和"疑似失效"两组（各保持原有顺序），
    // 而不是对每条线路分别再跑一遍 isLineDead（那会重复读失败计数）。
    val (dead, suspected) = measured.filter { !it.usable }
        .partition { failureCount(it.url) + 1 >= 2 }

    // 结果成功提交到当前缓存版本后，调用方再统一写失败计数。
    val kept = (usable + suspected).map { it.url }

    return channel.copy(
        urls = kept,
        lineQuality = usable + suspected + dead // 完整检测信息留给界面显示状态
    )
}

/**
 * 检测所有频道（一键测速用）。
 * 分批并发（每批 20 个）：既快又不会一次性发起上千个请求压垮网络。
 * onProgress 回调用于界面显示进度。
 */
suspend fun testAllChannels(
    context: Context,
    channels: List<Channel>,
    onProgress: (done: Int, total: Int) -> Unit
): List<Channel> {
    val result = mutableListOf<Channel>()
    val batchSize = 16
    // 所有频道共用一个并发闸门，避免某个多线路频道瞬间占满盒子的网络连接。
    val requestLimit = Semaphore(16)
    for (start in channels.indices step batchSize) {
        val batch = channels.subList(start, minOf(start + batchSize, channels.size))
        val tested = coroutineScope {
            batch.map { channel ->
                async { testChannel(context, channel, requestLimit) }
            }.awaitAll()
        }
        result += tested
        onProgress(result.size, channels.size)
    }
    return result
}

/** 检测一条线路：读取有限清单内容，结合线路自己的 M3U 提示、实测和历史记录得出质量。 */
suspend fun testLine(
    url: String,
    resolutionHint: String? = null
): LineQuality = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()
    try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", APP_USER_AGENT)
            .build()
        speedTestClient.newCall(request).readCancellable { response ->
            val latency = System.currentTimeMillis() - startTime
            // 2xx / 3xx 都算可用（重定向也是正常的）
            if (response.isSuccessful) {
                // 最多读取 64KB：足以覆盖大多数 HLS 主清单，同时避免下载完整媒体。
                val transferStart = System.currentTimeMillis()
                val probe = response.body?.byteStream()?.use { stream ->
                    stream.readUpTo(RESOLUTION_PROBE_BYTES)
                } ?: byteArrayOf()
                val transferMs = (System.currentTimeMillis() - transferStart).coerceAtLeast(1L)
                if (probe.isNotEmpty() && isLikelyStreamResponse(probe, response.header("Content-Type"))) {
                    val networkQuality = calculateNetworkQuality(latency, probe.size, transferMs)
                    val resolution = detectStreamResolution(
                        probe = probe,
                        finalUrl = response.request.url.toString(),
                        hints = listOfNotNull(resolutionHint)
                    )
                    LineQuality(
                        url = url,
                        usable = true,
                        latencyMs = latency,
                        score = networkQuality.score,
                        resolution = resolution,
                        throughputKbps = networkQuality.throughputKbps
                    )
                } else {
                    // 有响应但没内容：也算失败
                    LineQuality(url, false, latency, 0)
                }
            } else {
                LineQuality(url, false, null, 0)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 超时、拒绝连接、DNS 失败等都算失败
        LineQuality(url, false, null, 0)
    }
}

private const val RESOLUTION_PROBE_BYTES = 64 * 1024
private const val THROUGHPUT_SAMPLE_MIN_BYTES = 16 * 1024

internal data class ProbeNetworkQuality(val score: Int, val throughputKbps: Int?)

internal fun calculateNetworkQuality(
    latencyMs: Long,
    bytesRead: Int,
    transferMs: Long
): ProbeNetworkQuality {
    val latencyScore = (100 - (latencyMs / 12).toInt()).coerceIn(20, 100)
    if (bytesRead < THROUGHPUT_SAMPLE_MIN_BYTES) {
        return ProbeNetworkQuality(latencyScore, null)
    }
    val throughputKbps = ((bytesRead.toLong() * 8) / transferMs.coerceAtLeast(1L))
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    val throughputScore = when {
        throughputKbps >= 8_000 -> 100
        throughputKbps >= 4_000 -> 90
        throughputKbps >= 2_000 -> 75
        throughputKbps >= 1_000 -> 60
        throughputKbps >= 500 -> 40
        else -> 20
    }
    return ProbeNetworkQuality(
        score = ((latencyScore * 45 + throughputScore * 55) / 100).coerceIn(0, 100),
        throughputKbps = throughputKbps
    )
}

internal fun isLikelyStreamResponse(probe: ByteArray, contentType: String?): Boolean {
    if (contentType?.contains("text/html", ignoreCase = true) == true) return false
    val prefix = String(probe, 0, minOf(probe.size, 512), Charsets.UTF_8).trimStart().lowercase()
    return !prefix.startsWith("<!doctype html") && !prefix.startsWith("<html")
}

private fun InputStream.readUpTo(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 8 * 1024))
    val buffer = ByteArray(4 * 1024)
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read <= 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    return output.toByteArray()
}
