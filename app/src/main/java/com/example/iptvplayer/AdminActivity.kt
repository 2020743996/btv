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
import androidx.compose.foundation.layout.fillMaxWidth
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
                horizontal = if (compact) UiSpace.PageCompact else UiSpace.PageRegular,
                vertical = if (compact) UiSpace.PageCompact else UiSpace.PageRegular
            )
    ) {
        PageHeader(title = "频道管理", onBack = onExit)
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("频道源")
        NavigationRow("源地址管理", UiIcons.List, onOpenAddresses)
        ListSeparator(inset = 48.dp)
        NavigationRow("失效线路管理", UiIcons.Gauge, onOpenDeadChannels)
        ListSeparator(inset = 48.dp)
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("诊断")
        NavigationRow("运行日志", UiIcons.Info, onOpenLogs)
        ListSeparator(inset = 48.dp)
    }
}
