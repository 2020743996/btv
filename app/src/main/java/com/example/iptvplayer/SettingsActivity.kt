package com.example.iptvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SettingsActivity：普通设置页。
 *
 * 只放普通用户（老人）需要的东西：
 * - 老人模式开关
 * - 字体大小（标准/大/特大）
 * - 管理员模式入口（源地址、日志、失效管理都收在里面，防止误操作）
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IptvPlayerTheme {
                SettingsScreen(
                    onOpenAdmin = {
                        startActivity(Intent(this, AdminActivity::class.java))
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(onOpenAdmin: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var elderMode by remember { mutableStateOf(isElderMode(context)) }
    var fontSize by remember { mutableIntStateOf(getFontSize(context)) }
    val compact = rememberWindowType() == WindowType.COMPACT

    // 字体大小即时预览：选档位时整个页面立刻按新字号渲染，
    // "所见即所得"，保存前就能确认效果。
    IptvPlayerTheme(fontScale = fontScaleFor(fontSize)) {
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
        PageHeader(title = "设置", subtitle = "调整观看体验与频道管理", onBack = onBack)
        Spacer(modifier = Modifier.height(22.dp))

        // ===== 老人模式 =====
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("老人模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(
                    "保留电视、收藏和必要设置",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Switch(checked = elderMode, onCheckedChange = { elderMode = it })
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ===== 字体大小：标准 / 大 / 特大 =====
        Text("字体大小", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("标准", highlighted = fontSize == 0, onClick = { fontSize = 0 })
            ActionButton("大", highlighted = fontSize == 1, onClick = { fontSize = 1 })
            ActionButton("特大", highlighted = fontSize == 2, onClick = { fontSize = 2 })
        }

        // ===== 管理员模式入口（老人模式隐藏，防止误操作） =====
        if (!elderMode) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "管理员模式：源地址、日志、失效频道管理",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton("进入频道管理", icon = Icons.Default.Settings, onClick = onOpenAdmin)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("保存", highlighted = true, icon = Icons.Default.Check, onClick = {
                setElderMode(context, elderMode)
                setFontSize(context, fontSize)
                // 回到列表页，设置立即生效（onResume 刷新）
                val activity = context as? SettingsActivity
                activity?.finish()
            })
            ActionButton("取消", icon = Icons.Default.Close, onClick = {
                (context as? SettingsActivity)?.finish()
            })
        }
    }
    }
}
