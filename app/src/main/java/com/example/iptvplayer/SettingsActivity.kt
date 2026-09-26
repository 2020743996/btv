package com.example.iptvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
                    onOpenSources = {
                        startActivity(Intent(this, AddressActivity::class.java))
                    },
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
fun SettingsScreen(onOpenSources: () -> Unit, onOpenAdmin: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var elderMode by remember { mutableStateOf(isElderMode(context)) }
    var fontSize by remember { mutableIntStateOf(getFontSize(context)) }
    var glassTransparency by remember { mutableIntStateOf(getGlassTransparency(context)) }
    var startupMode by remember { mutableStateOf(getStartupMode(context)) }
    var epgUrl by remember { mutableStateOf(getEpgUrl(context).orEmpty()) }
    var epgError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingBackup by remember { mutableStateOf<SettingsBackup?>(null) }
    var confirmExport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var epgRevision by remember { mutableIntStateOf(0) }
    var epgLoading by remember { mutableStateOf(false) }
    fun retryEpg() {
        if (epgLoading) return
        epgLoading = true
        scope.launch {
            try {
                EpgCache.invalidate()
                refreshConfiguredEpg(context)
                epgRevision++
            } finally {
                epgLoading = false
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(encodeBackup(readBackup(context))) }
                    ?: error("无法写入备份文件")
                "备份已导出"
            }.getOrElse { "备份失败：${it.message ?: "无法写入文件"}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(1_000_000) }
                    ?: error("无法读取备份文件")
                decodeBackup(bytes.toString(Charsets.UTF_8))
            }.onSuccess { pendingBackup = it }
                .onFailure { message = "导入失败：${it.message ?: "文件格式错误"}" }
        }
    }
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
            PageHeader(title = "设置", onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 老人模式 =====
                SectionLabel("观看")
                Text("启动时", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(StartupMode.entries.map { it.label }, StartupMode.entries.indexOf(startupMode)) {
                    startupMode = StartupMode.entries[it]
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("老人模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                    Switch(
                        checked = elderMode,
                        onCheckedChange = { elderMode = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF248A3D),
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE2E3E8),
                            uncheckedThumbColor = Color.White,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                ListSeparator()

                // ===== 字体大小：标准 / 大 / 特大 =====
                SectionLabel("字体大小")
                Spacer(modifier = Modifier.height(6.dp))
                FontSizeSelector(selected = fontSize, onSelected = { fontSize = it })

                Spacer(modifier = Modifier.height(22.dp))
                SectionLabel("界面材质")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("液态玻璃透明度", Modifier.weight(1f), fontSize = 16.sp)
                    Text("$glassTransparency%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                Slider(
                    value = glassTransparency.toFloat(),
                    onValueChange = { glassTransparency = it.toInt().coerceIn(0, 40) },
                    valueRange = 0f..40f,
                    steps = 39
                )

                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("频道源与节目单")
                NavigationRow("管理频道源", UiIcons.Pencil, onOpenSources)
                ListSeparator(inset = 48.dp)
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it; epgError = null },
                    label = { Text("独立 XMLTV 地址（可选）") },
                    singleLine = true,
                    isError = epgError != null,
                    supportingText = epgError?.let { error -> { Text(error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                val epgStatuses = remember(epgRevision) { SourceStatuses.epg }
                epgStatuses.forEach { source ->
                    val host = runCatching { android.net.Uri.parse(source.url).host }.getOrNull() ?: "节目单源"
                    val status = when (source.health) {
                        SourceHealth.NORMAL -> "正常 · ${source.itemCount} 条节目"
                        SourceHealth.STALE -> "使用上次数据 · 点此重试"
                        SourceHealth.FAILED -> "加载失败 · 点此重试"
                    }
                    Text("$host · $status", color = if (source.health == SourceHealth.FAILED)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = if (source.health != SourceHealth.NORMAL) Modifier.clickable { retryEpg() } else Modifier)
                }
                if (epgLoading) Text("正在重新加载节目单…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("数据")
                NavigationRow("导出配置备份", UiIcons.Download, onClick = { confirmExport = true })
                ListSeparator(inset = 48.dp)
                NavigationRow("从备份恢复", UiIcons.Upload, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                ListSeparator(inset = 48.dp)
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

                // ===== 管理员模式入口（老人模式隐藏，防止误操作） =====
                if (!elderMode) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionLabel("高级")
                    NavigationRow("诊断与维护", UiIcons.Sliders, onOpenAdmin)
                    ListSeparator(inset = 48.dp)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            epgError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            FormActions(
                primary = UiAction(
                    label = "保存",
                    icon = UiIcons.Check,
                    accentColor = UiColors.Settings,
                    onClick = {
                        if (epgUrl.isNotBlank() && !isSupportedM3uUrl(epgUrl)) {
                            epgError = "请输入完整的 HTTP/HTTPS 地址"
                            return@UiAction
                        }
                        setElderMode(context, elderMode)
                        setFontSize(context, fontSize)
                        setGlassTransparency(context, glassTransparency)
                        setStartupMode(context, startupMode)
                        if (epgUrl.trim() != getEpgUrl(context).orEmpty()) setEpgUrl(context, epgUrl)
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
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("导出配置备份？") },
            text = { Text("备份文件包含频道源地址，可能含有访问凭据。请妥善保管。") },
            confirmButton = {
                DialogFooter(
                    primary = UiAction("导出", icon = UiIcons.Check, onClick = {
                        confirmExport = false
                        exportLauncher.launch("btv-backup.json")
                    }),
                    secondary = UiAction("取消", icon = UiIcons.X, onClick = { confirmExport = false })
                )
            }
        )
    }
    pendingBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            title = { Text("恢复配置备份？") },
            text = { Text("将用备份中的 ${backup.m3uUrls.size} 个频道源和设置替换当前配置。") },
            confirmButton = {
                DialogFooter(
                    primary = UiAction("恢复", icon = UiIcons.Check, onClick = {
                        if (restoreBackup(context, backup)) onBack() else message = "恢复写入失败，请重试"
                        pendingBackup = null
                    }),
                    secondary = UiAction("取消", icon = UiIcons.X, onClick = { pendingBackup = null })
                )
            }
        )
    }
}

@Composable
private fun FontSizeSelector(selected: Int, onSelected: (Int) -> Unit) {
    SegmentedControl(listOf("标准", "大", "特大"), selected, onSelected)
}

@Composable
internal fun SegmentedControl(options: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, AppleUi.Control)
            .padding(3.dp),
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
                    .clip(AppleUi.Control)
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) UiColors.Settings else Color.Transparent,
                        AppleUi.Control
                    )
                    .clickable { onSelected(index) }
                    .background(
                        if (active) Color.White else Color.Transparent,
                        AppleUi.Control
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) UiColors.Info else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
