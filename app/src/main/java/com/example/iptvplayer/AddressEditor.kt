package com.example.iptvplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AddressStateSaver = listSaver<AddressEditorState, Any>(
    save = { listOf(ArrayList(it.savedUrls), ArrayList(it.urls), it.editingIndex, it.draft,
        it.error.orEmpty(), it.confirmation.name, it.deleteIndex) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        AddressEditorState(it[0] as List<String>, it[1] as List<String>, it[2] as Int,
            it[3] as String, (it[4] as String).ifEmpty { null },
            AddressConfirmation.valueOf(it[5] as String), it[6] as Int)
    }
)

@Composable
fun AddressEditor(initialUrls: List<String>, onSave: (List<String>) -> Unit, onBack: () -> Unit) {
    var state by rememberSaveable(stateSaver = AddressStateSaver) {
        mutableStateOf(AddressEditorState(initialUrls))
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val compact = rememberWindowType() == WindowType.COMPACT
    val useTvKeyboard = isTvDevice(context) && !hasSystemIme(context)

    fun back() {
        if (!state.editing && !state.dirty) onBack()
        else {
            state = state.requestBack()
            if (!state.editing) {
                focus.clearFocus()
                keyboard?.hide()
            }
        }
    }
    fun submit() {
        state = state.submit()
        if (!state.editing) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    BackHandler(enabled = state.editing || state.dirty) { back() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .systemBarsPaddingCompat()
    ) {
        val shortWindow = maxHeight < 220.dp
        LaunchedEffect(state.error) {
            // 短屏校验失败时收起键盘，确保错误信息完整可见。
            if (shortWindow && state.error != null) keyboard?.hide()
        }
        Column(Modifier.fillMaxSize().padding(
            horizontal = if (compact) 16.dp else 24.dp,
            vertical = if (shortWindow) 4.dp else 12.dp
        )) {
            if (!state.editing) {
                PageHeader(
                    title = "M3U 地址",
                    subtitle = "${state.urls.size} 个源 · ${if (state.dirty) "未保存" else "已保存"}",
                    onBack = { back() }
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.urls.isEmpty()) item {
                        Text("暂无频道源", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    itemsIndexed(state.urls) { index, url ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { state = state.edit(index) }
                                .padding(start = 4.dp, end = 0.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(url, modifier = Modifier.weight(1f), fontSize = 15.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            TableActions(
                                onEdit = { state = state.edit(index) },
                                onDelete = { state = state.copy(confirmation = AddressConfirmation.DELETE, deleteIndex = index) }
                            )
                        }
                        ListSeparator()
                    }
                }
                Spacer(Modifier.height(12.dp))
                FormActions(
                    primary = UiAction("保存", icon = UiIcons.Check, accentColor = UiColors.Info,
                        onClick = { onSave(state.urls) }),
                    secondary = UiAction("添加", icon = UiIcons.Plus, accentColor = UiColors.Info,
                        onClick = { state = state.edit(state.urls.size) })
                )
            } else {
                // 标题随输入区滚动，为横屏键盘上方的固定提交区优先留出空间。
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (!shortWindow) {
                        PageHeader(title = if (state.adding) "添加频道源" else "修改频道源", onBack = { back() })
                        Spacer(Modifier.height(12.dp))
                    }
                    if (useTvKeyboard) {
                        Text(state.draft.ifEmpty { "（空）" }, fontSize = 17.sp, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                        TvKeyboard(onKey = { state = state.changeDraft(state.draft + it) })
                    } else {
                        val requester = remember { FocusRequester() }
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = { state = state.changeDraft(it) },
                            label = if (shortWindow) null else { { Text("M3U 地址") } },
                            placeholder = { Text("https://example.com/list.m3u") },
                            singleLine = true,
                            shape = AppleUi.Field,
                            isError = state.error != null,
                            colors = standardTextFieldColors(),
                            supportingText = state.error?.let { error -> { Text(error) } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(requester)
                        )
                        LaunchedEffect(Unit) { requester.requestFocus() }
                    }
                }
                Spacer(Modifier.height(if (shortWindow) 4.dp else 12.dp))
                FormActions(
                    primary = UiAction(if (state.adding) "添加" else "确定", icon = UiIcons.Check,
                        accentColor = UiColors.Info, onClick = { submit() }),
                    secondary = UiAction("取消", icon = UiIcons.X,
                        accentColor = MaterialTheme.colorScheme.onSurfaceVariant, onClick = { back() }),
                    destructive = if (useTvKeyboard) UiAction("退格", icon = UiIcons.ArrowLeft,
                        accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { state = state.changeDraft(state.draft.dropLast(1)) }) else null,
                    stackOnCompact = useTvKeyboard
                )
            }
        }
    }

    if (state.confirmation != AddressConfirmation.NONE) {
        val deleting = state.confirmation == AddressConfirmation.DELETE
        fun dismiss() { state = state.copy(confirmation = AddressConfirmation.NONE) }
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text(if (deleting) "删除频道源？" else "放弃未保存的修改？") },
            text = { Text(if (deleting) state.urls.getOrNull(state.deleteIndex).orEmpty() else "修改尚未保存。") },
            confirmButton = {
                if (deleting) {
                    DialogFooter(
                        primary = UiAction("删除", icon = UiIcons.Trash, accentColor = UiColors.Delete,
                            onClick = { state = state.confirmDelete() }),
                        secondary = UiAction("取消", icon = UiIcons.X, accentColor = UiColors.Info,
                            onClick = { dismiss() })
                    )
                } else {
                    DialogFooter(
                        primary = UiAction("继续编辑", icon = UiIcons.Pencil, accentColor = UiColors.Info,
                            onClick = { dismiss() }),
                        destructive = UiAction("放弃修改", icon = UiIcons.Trash, accentColor = UiColors.Delete,
                            onClick = {
                                if (state.confirmation == AddressConfirmation.LEAVE) onBack()
                                else {
                                    state = state.closeEditor()
                                    focus.clearFocus()
                                    keyboard?.hide()
                                }
                            })
                    )
                }
            }
        )
    }
}
