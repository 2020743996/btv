package com.example.iptvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                .padding(
                    horizontal = if (compact) 16.dp else 32.dp,
                    vertical = if (compact) 16.dp else 24.dp
                )
        ) {
            PageHeader(title = "设置", subtitle = "调整观看体验与频道管理", onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 老人模式 =====
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("老人模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        Text(
                            "保留电视、收藏和必要设置",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = elderMode,
                        onCheckedChange = { elderMode = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = UiColors.Settings,
                            checkedThumbColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ===== 字体大小：标准 / 大 / 特大 =====
                Text("字体大小", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(6.dp))
                FontSizeSelector(selected = fontSize, onSelected = { fontSize = it })

                // ===== 管理员模式入口（老人模式隐藏，防止误操作） =====
                if (!elderMode) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "管理员模式：源地址、日志、失效频道管理",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionButton(
                        "进入频道管理",
                        modifier = Modifier.fillMaxWidth(),
                        icon = UiIcons.Sliders,
                        accentColor = UiColors.Settings,
                        onClick = onOpenAdmin
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            FormActions(
                primary = UiAction(
                    label = "保存",
                    icon = UiIcons.Check,
                    accentColor = UiColors.Settings,
                    onClick = {
                        setElderMode(context, elderMode)
                        setFontSize(context, fontSize)
                        onBack()
                    }
                ),
                secondary = UiAction(
                    label = "取消",
                    icon = UiIcons.X,
                    accentColor = UiColors.Info,
                    onClick = onBack
                )
            )
        }
    }
}

@Composable
private fun FontSizeSelector(selected: Int, onSelected: (Int) -> Unit) {
    val options = listOf("标准", "大", "特大")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            var focused by remember { mutableStateOf(false) }
            val active = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) UiColors.Settings else Color.Transparent,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onSelected(index) }
                    .background(
                        if (active) UiColors.Settings else Color.Transparent,
                        MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
