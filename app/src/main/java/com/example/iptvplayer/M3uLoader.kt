package com.example.iptvplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** 全 App 统一的 User-Agent，避免不同模块各写各的版本号。 */
val APP_USER_AGENT: String = "btv/${BuildConfig.VERSION_NAME}"

/**
 * 全 App 共用一个网络客户端。
 *
 * OkHttpClient 内部维护连接池和 DNS 缓存，重复创建会浪费连接与线程。
 * M3U、EPG 和测速共用客户端后，多个源加载会更快，也更省盒子内存。
 * readTimeout 给 60s：EPG 源（如 fanmingming e.xml）可能 8MB 以上，慢网络下
 * 30s 读超时会失败；测速走独立的短超时客户端，不受影响。
 */
val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * 下载 M3U 文件的内容，返回文件文本。
 *
 * suspend 表示"挂起函数"：它可以在协程里执行而不会卡住界面线程。
 * 协程是 Kotlin 里"在后台做耗时事情"的标准方式，后面讲代码时会细说。
 *
 * 网络请求必须放到 Dispatchers.IO（IO 线程池），
 * 因为下载是"等待网络"的慢操作，不能占用界面线程。
 */
suspend fun downloadM3u(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", APP_USER_AGENT)
        .header("Accept", "application/x-mpegURL, application/xml, text/plain, */*")
        .build()

    sharedHttpClient.newCall(request).readCancellable { response ->
        // 2xx 之外的响应都视为失败（比如 404 文件不存在、503 服务器繁忙）
        if (!response.isSuccessful) {
            throw IOException("下载失败：HTTP ${response.code}")
        }
        val body = response.body ?: throw IOException("返回内容为空")
        if (body.contentLength() > MAX_TEXT_RESPONSE_BYTES) {
            throw IOException("返回内容过大，已拒绝读取")
        }
        val bytes = body.byteStream().use { it.readLimited(MAX_TEXT_RESPONSE_BYTES) }
        if (bytes.isEmpty()) throw IOException("返回内容为空")
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        String(bytes, charset)
    }
}

private const val MAX_TEXT_RESPONSE_BYTES = 32 * 1024 * 1024

private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw IOException("返回内容超过 32 MB")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
