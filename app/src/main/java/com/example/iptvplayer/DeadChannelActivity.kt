package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * DeadChannelActivity：失效频道管理（管理员模式子页面）。
 * 展示连续测速失败被隐藏的线路（回收站），支持：
 * - 全部重新检测：成功的线路恢复
 * - 清除失效记录：所有线路恢复
 */
class DeadChannelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IptvPlayerTheme(fontScale = fontScaleFor(getFontSize(this))) {
                DeadChannelScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun DeadChannelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var failRecords by remember { mutableStateOf(getAllFailRecords(context)) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val compact = rememberWindowType() == WindowType.COMPACT

    // 重新检测全部失效线路：成功的恢复，失败的保留
    fun runRecheck() {
        checking = true
        result = null
        scope.launch {
            val limit = Semaphore(6)
            val tests = coroutineScope {
                failRecords.map { (url, _) ->
                    async { limit.withPermit { testLine(url) } }
                }.awaitAll()
            }
            recordLineResults(context, tests.associate { it.url to it.usable })
            tests.filter { it.usable }.forEach { quality ->
                ChannelCache.restoreLine(quality.url, quality)
            }
            val recovered = tests.count { it.usable }
            failRecords = getAllFailRecords(context)
            result = "检测完成：$recovered 条线路已恢复"
            checking = false
            AppLog.log("失效线路重新检测：$recovered 条恢复")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
            .padding(
                horizontal = if (compact) 16.dp else 24.dp,
                vertical = if (compact) 14.dp else 20.dp
            )
    ) {
        PageHeader(
            title = "失效线路",
            subtitle = "连续失败 2 次后隐藏，可重新检测或恢复",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (failRecords.isEmpty()) {
                item {
                    Text("（没有失效记录）", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            items(failRecords, key = { it.first }) { (url, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (count >= 2) "失效($count 次)" else "疑似($count 次)",
                        color = if (count >= 2) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ActionBar(
            actions = listOf(
                UiAction(
                    label = "清除失效记录",
                    icon = UiIcons.Trash,
                    accentColor = UiColors.Delete,
                    role = ActionRole.DESTRUCTIVE,
                    enabled = !checking,
                    onClick = {
                        clearFailRecords(context)
                        ChannelCache.restoreAllLines()
                        failRecords = emptyList()
                        result = "已清除全部失效记录"
                        AppLog.log("清除失效记录")
                    }
                ),
                UiAction(
                    label = if (checking) "检测中…" else "全部重新检测",
                    enabled = !checking,
                    icon = UiIcons.Refresh,
                    accentColor = UiColors.Refresh,
                    role = ActionRole.PRIMARY,
                    onClick = { runRecheck() }
                )
            ),
            stackOnCompact = true
        )
        if (result != null) {
            Text(
                result!!,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
