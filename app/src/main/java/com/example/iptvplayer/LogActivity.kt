package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LogActivity：运行日志（管理员模式子页面）。
 * 展示 AppLog 里记录的最近 100 条运行事件。
 */
class LogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IptvPlayerTheme {
                LogScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    val logs = remember { AppLog.all() }
    val compact = rememberWindowType() == WindowType.COMPACT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
            .padding(
                horizontal = if (compact) 16.dp else 24.dp,
                vertical = if (compact) 14.dp else 20.dp
            )
    ) {
        Text("运行日志", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "最近 ${logs.size} 条运行记录（下载、测速、播放切换等）。重启软件后清空。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Text("（暂无日志）", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs) { line ->
                    Text(
                        text = line,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ActionButton("返回", onClick = onBack)
    }
}
