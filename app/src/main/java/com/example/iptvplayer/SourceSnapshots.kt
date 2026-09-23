package com.example.iptvplayer

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val SNAPSHOT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
private const val SNAPSHOT_MAX_BYTES = 32 * 1024 * 1024

internal enum class SourceHealth { NORMAL, STALE, FAILED }

internal data class SourceStatus(
    val url: String,
    val health: SourceHealth,
    val itemCount: Int,
    val message: String? = null
)

internal data class LoadedSource(val text: String?, val channels: List<Channel>, val status: SourceStatus)

internal object SourceStatuses {
    @Volatile var channels: List<SourceStatus> = emptyList()
    @Volatile var epg: List<SourceStatus> = emptyList()
}

/** Store each source independently, so one failed download cannot replace another source's data. */
internal class SourceSnapshots(context: Context) {
    private val directory = File(context.noBackupFilesDir, "source_snapshots")

    private fun file(kind: String, url: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$kind:$url".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.gz")
    }

    suspend fun save(kind: String, url: String, content: String) = withContext(Dispatchers.IO) {
        if (content.toByteArray(Charsets.UTF_8).size > SNAPSHOT_MAX_BYTES) return@withContext
        directory.mkdirs()
        val atomic = AtomicFile(file(kind, url))
        val output = atomic.startWrite()
        try {
            val gzip = GZIPOutputStream(output)
            gzip.write(content.toByteArray(Charsets.UTF_8))
            gzip.finish()
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }

    suspend fun load(kind: String, url: String): String? = withContext(Dispatchers.IO) {
        val target = file(kind, url)
        if (!target.exists() || System.currentTimeMillis() - target.lastModified() > SNAPSHOT_MAX_AGE_MS) {
            return@withContext null
        }
        runCatching {
            AtomicFile(target).openRead().use { input ->
                GZIPInputStream(input).use { gzip ->
                    val bytes = gzip.readBoundedBytes(SNAPSHOT_MAX_BYTES)
                    bytes.takeIf { it.size <= SNAPSHOT_MAX_BYTES }?.toString(Charsets.UTF_8)
                }
            }
        }.getOrNull()
    }

    suspend fun retain(urls: Set<String>) = withContext(Dispatchers.IO) {
        val validNames = urls.flatMap { url -> listOf(file("m3u", url).name, file("epg", url).name) }.toSet()
        directory.listFiles()?.filter { it.name !in validNames }?.forEach { it.delete() }
    }
}

internal fun InputStream.readBoundedBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw IllegalArgumentException("文件过大")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal suspend fun loadChannelSource(
    context: Context,
    url: String,
    fetch: suspend (String) -> String = ::downloadM3u
): LoadedSource {
    val snapshots = SourceSnapshots(context)
    return try {
        val text = fetch(url)
        val channels = withContext(Dispatchers.Default) { parseM3u(text) }
        require(channels.isNotEmpty()) { "没有可用频道" }
        try {
            snapshots.save("m3u", url, text)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            AppLog.log("频道源快照保存失败")
        }
        LoadedSource(text, channels, SourceStatus(url, SourceHealth.NORMAL, channels.size))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        val cached = snapshots.load("m3u", url)
        val oldChannels = cached?.let {
            withContext(Dispatchers.Default) { runCatching { parseM3u(it) }.getOrNull() }
        }.orEmpty()
        if (oldChannels.isNotEmpty()) {
            LoadedSource(cached, oldChannels, SourceStatus(url, SourceHealth.STALE, oldChannels.size, "连接失败，使用上次数据"))
        } else {
            LoadedSource(null, emptyList(), SourceStatus(url, SourceHealth.FAILED, 0, "连接失败"))
        }
    }
}
