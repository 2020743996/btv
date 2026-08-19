package com.example.iptvplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 源地址编辑器：查看/添加/修改/删除 M3U 地址列表。
 * 放在"管理员模式"里使用（源地址属于管理员操作，普通用户不接触）。
 * 交互：点“添加”用软键盘输入；点地址行修改；点行尾删除图标移除。
 */
@Composable
fun AddressEditor(
    initialUrls: List<String>,
    onSave: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    var urls by remember { mutableStateOf(initialUrls) }
    // 编辑模式状态：null = 浏览列表；数字 = 正在修改第几个地址
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val compact = rememberWindowType() == WindowType.COMPACT
    val pagePadding = if (compact) 16.dp else 32.dp

    if (editingIndex == null) {
        // ===== 浏览模式 =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
                .padding(horizontal = pagePadding, vertical = if (compact) 16.dp else 24.dp)
        ) {
            PageHeader(
                title = "M3U 地址",
                subtitle = "多个源会合并同名频道并保留备用线路",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (urls.isEmpty()) {
                    Text(
                        "（还没有地址，请使用底部添加按钮）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
                urls.forEachIndexed { index, url ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable()
                            .clickable {
                                draft = url
                                editingIndex = index
                            }
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = url,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TableActions(
                            onEdit = {
                                draft = url
                                editingIndex = index
                            },
                            onDelete = { urls = urls.filterIndexed { i, _ -> i != index } }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 保存时过滤空白地址，避免把空串存成无效源。
            FormActions(
                primary = UiAction(
                    label = "保存",
                    icon = UiIcons.Check,
                    accentColor = UiColors.Info,
                    onClick = { onSave(urls.filter { it.isNotBlank() }) }
                ),
                secondary = UiAction(
                    label = "添加",
                    icon = UiIcons.Plus,
                    accentColor = UiColors.Info,
                    onClick = {
                        draft = ""
                        editingIndex = urls.size
                    }
                )
            )
        }
    } else {
        // ===== 编辑模式：输入一个地址 =====
        val isAdding = editingIndex == urls.size
        // 电视优先用系统键盘（Gboard）；没有输入法的盒子才用自绘键盘
        val useTvKeyboard = isTvDevice(context) && !hasSystemIme(context)
        val normalizedDraft = draft.trim()
        val canSubmit = normalizedDraft.startsWith("http://") || normalizedDraft.startsWith("https://")

        fun submitDraft() {
            if (!canSubmit) return
            val i = editingIndex!!
            urls = if (i < urls.size) {
                urls.mapIndexed { j, url -> if (j == i) normalizedDraft else url }
            } else {
                urls + normalizedDraft
            }
            editingIndex = null
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
                .padding(horizontal = pagePadding, vertical = if (compact) 16.dp else 24.dp)
        ) {
            PageHeader(
                title = if (isAdding) "添加频道源" else "修改频道源",
                subtitle = "输入以 http:// 或 https:// 开头的 M3U 地址",
                onBack = { editingIndex = null }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (useTvKeyboard) {
                    Text(
                        text = if (draft.isEmpty()) "（空）" else draft,
                        color = if (draft.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .padding(14.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TvKeyboard(onKey = { draft += it })
                } else {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text("https://example.com/list.m3u") },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Uri
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitDraft() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    // 打开即聚焦弹键盘
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FormActions(
                primary = UiAction(
                    label = if (isAdding) "添加" else "确定",
                    icon = UiIcons.Check,
                    accentColor = UiColors.Info,
                    enabled = canSubmit,
                    onClick = { submitDraft() }
                ),
                secondary = UiAction(
                    label = "取消",
                    icon = UiIcons.X,
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { editingIndex = null }
                ),
                destructive = if (useTvKeyboard) {
                    UiAction(
                        label = "退格",
                        icon = UiIcons.ArrowLeft,
                        accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { draft = draft.dropLast(1) }
                    )
                } else null,
                stackOnCompact = useTvKeyboard
            )
        }
    }
}
