package com.example.iptvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AdminActivity：管理员模式首页（菜单）。
 * 三个子页面：源地址管理、失效频道管理、运行日志。
 * 每个子页面都是独立页面（避免嵌套滚动导致崩溃）。
 */
class AdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IptvPlayerTheme(fontScale = fontScaleFor(getFontSize(this))) {
                AdminScreen(
                    onOpenAddresses = {
                        startActivity(Intent(this, AddressActivity::class.java))
                    },
                    onOpenDeadChannels = {
                        startActivity(Intent(this, DeadChannelActivity::class.java))
                    },
                    onOpenLogs = {
                        startActivity(Intent(this, LogActivity::class.java))
                    },
                    onExit = { finish() }
                )
            }
        }
    }
}

@Composable
fun AdminScreen(
    onOpenAddresses: () -> Unit,
    onOpenDeadChannels: () -> Unit,
    onOpenLogs: () -> Unit,
    onExit: () -> Unit
) {
    val compact = rememberWindowType() == WindowType.COMPACT
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (compact) 16.dp else 32.dp,
                vertical = if (compact) 16.dp else 24.dp
            )
    ) {
        Text("管理员模式", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "高级功能：改频道源、处理失效频道、看运行日志。普通使用不需要进来。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        ActionButton("一、源地址管理", highlighted = true, onClick = onOpenAddresses)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "添加/修改/删除 M3U 频道源地址",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        ActionButton("二、失效频道管理", highlighted = true, onClick = onOpenDeadChannels)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "查看被隐藏的失效线路，重新检测或清除记录",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        ActionButton("三、运行日志", highlighted = true, onClick = onOpenLogs)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "最近 100 条运行记录，帮助排查问题",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        ActionButton("退出管理员模式", onClick = onExit)
        Spacer(modifier = Modifier.height(20.dp))
    }
}
