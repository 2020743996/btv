package com.example.iptvplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val speedTestClient: OkHttpClient by lazy {
    sharedHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
}

/**
 * 线路质量检测器（测速）。
 *
 * 对每条线路做真实的网络请求，结合"本次实测 + 历史失败次数"打分：
 * - 得分高的线路质量好（快、稳），播放时优先用
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
    val score: Int            // 质量分 0-100：>=70 流畅，30-69 一般，<30 慢/不可用
)

/**
 * 检测一个频道的所有线路，返回整理后的频道。
 *
 * 整理规则（"一键优化"的核心逻辑）：
 * 1. 可用线路按分数从高到低排序（最快的在前）
 * 2. 只保留前 3 条可用线路（1 条主力 + 2 条备用），避免线路过多
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
        channel.urls.map { url ->
            async { requestLimit.withPermit { testLine(context, url) } }
        }.awaitAll()
    }

    val usable = results.filter { it.usable }.sortedByDescending { it.score }
    val suspected = results.filter { !it.usable && !isLineDead(context, it.url) }
    val dead = results.filter { !it.usable && isLineDead(context, it.url) }

    // 保留 3 条好线路 + 疑似失效的（供手动尝试），失效的直接剔除
    val kept = (usable.take(3) + suspected).map { it.url }

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

/** 检测一条线路：GET 请求清单，结合实测和历史记录得出分数 */
suspend fun testLine(context: Context, url: String): LineQuality = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", APP_USER_AGENT)
        .build()

    val startTime = System.currentTimeMillis()
    try {
        speedTestClient.newCall(request).execute().use { response ->
            val latency = System.currentTimeMillis() - startTime
            // 2xx / 3xx 都算可用（重定向也是正常的）
            if (response.isSuccessful) {
                // 读一小部分内容，确认真的能拿到数据（而不是空响应）
                val firstByteCount = response.body?.byteStream()?.use { stream ->
                    stream.read(ByteArray(2048))
                } ?: -1
                if (firstByteCount > 0) {
                    // 测速成功：失败计数清零（线路恢复了）
                    recordLineResult(context, url, success = true)
                    // 打分：满分 100，延迟越高扣越多（每 10ms 扣 1 分，最多扣 80）
                    val score = (100 - (latency / 10).toInt()).coerceIn(0, 100)
                    LineQuality(url, true, latency, score)
                } else {
                    // 有响应但没内容：也算失败
                    recordLineResult(context, url, success = false)
                    LineQuality(url, false, latency, 0)
                }
            } else {
                recordLineResult(context, url, success = false)
                LineQuality(url, false, null, 0)
            }
        }
    } catch (e: Exception) {
        // 超时、拒绝连接、DNS 失败等都算失败
        recordLineResult(context, url, success = false)
        LineQuality(url, false, null, 0)
    }
}
