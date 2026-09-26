package com.example.iptvplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

internal enum class PlaybackMenuPage(val title: String) {
    HOME("播放选项"), LINES("播放线路"), PICTURE("画面比例"),
    AUDIO("音轨"), SUBTITLES("字幕"), SLEEP("睡眠定时"), GUIDE("节目单")
}

internal data class PlaybackChoice(
    val id: String,
    val title: String,
    val detail: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true
)

@UnstableApi
internal fun trackChoices(player: Player?, type: Int): List<PlaybackChoice> {
    val parameters = player?.trackSelectionParameters
    val disabled = parameters?.disabledTrackTypes?.contains(type) == true
    val overridden = parameters?.overrides?.values?.any { it.mediaTrackGroup.type == type } == true
    val choices = mutableListOf(PlaybackChoice("auto", "自动", selected = !disabled && !overridden))
    if (type == C.TRACK_TYPE_TEXT) choices += PlaybackChoice("off", "关闭字幕", selected = disabled)
    player?.currentTracks?.groups?.forEachIndexed { groupIndex, group ->
        if (group.type == type) repeat(group.length) { index ->
            val format = group.getTrackFormat(index)
            val language = format.language?.takeUnless { it == "und" || it.isBlank() }
                ?.let { Locale.forLanguageTag(it).getDisplayName(Locale.SIMPLIFIED_CHINESE) }
            val title = format.label?.takeIf { it.isNotBlank() } ?: language ?: "${if (type == C.TRACK_TYPE_AUDIO) "音轨" else "字幕"} ${choices.count { ':' in it.id } + 1}"
            choices += PlaybackChoice("$groupIndex:$index",
                title,
                listOfNotNull(language?.takeIf { it != title },
                    format.channelCount.takeIf { it > 0 }?.let { "$it 声道" },
                    if (!group.isTrackSupported(index)) "设备不支持" else null)
                    .distinct().joinToString(" · ").takeIf { it.isNotBlank() },
                selected = !disabled && overridden && group.isTrackSelected(index),
                enabled = group.isTrackSupported(index))
        }
    }
    return choices
}

@UnstableApi
internal fun selectPlaybackTrack(player: Player, type: Int, id: String) {
    val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        .setTrackTypeDisabled(type, id == "off")
    if (id != "auto" && id != "off") {
        val parts = id.split(':').map { it.toIntOrNull() ?: return }
        if (parts.size != 2) return
        val group = player.currentTracks.groups.getOrNull(parts[0]) ?: return
        val index = parts[1]
        if (group.type != type || index !in 0 until group.length || !group.isTrackSupported(index)) return
        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
    }
    player.trackSelectionParameters = builder.build()
}

@UnstableApi
@Composable
internal fun PlaybackMenu(
    player: Player?,
    lines: List<PlaybackChoice>,
    pictureMode: PictureMode,
    remainingMinutes: Int?,
    programmes: List<Programme>,
    onLine: (Int) -> Unit,
    onPicture: (PictureMode) -> Unit,
    onSleep: (Int) -> Unit,
    onChannels: () -> Unit,
    onDismiss: () -> Unit,
    timerMinutes: Int = remainingMinutes ?: 0
) {
    var page by rememberSaveable { mutableStateOf(PlaybackMenuPage.HOME) }
    var trackRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { trackRevision++ }
            override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) {
                trackRevision++
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val tracks = remember(player, page, trackRevision) {
        trackChoices(player, if (page == PlaybackMenuPage.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT)
    }
    val back = { if (page == PlaybackMenuPage.HOME) onDismiss() else page = PlaybackMenuPage.HOME }
    PlaybackOptionsDialog(page.title, onBack = back, onDismiss = onDismiss) {
        when (page) {
            PlaybackMenuPage.HOME -> {
                item { PlaybackOptionRow(PlaybackChoice("channels", "切换频道"), navigation = true, onClick = onChannels) }
                items(PlaybackMenuPage.entries.filter { it != PlaybackMenuPage.HOME }) { destination ->
                    val detail = when (destination) {
                        PlaybackMenuPage.LINES -> lines.firstOrNull { it.selected }?.title ?: "暂无线路"
                        PlaybackMenuPage.PICTURE -> pictureMode.label
                        PlaybackMenuPage.SLEEP -> remainingMinutes?.let { "剩余 $it 分钟" } ?: "关闭"
                        PlaybackMenuPage.GUIDE -> "未来 24 小时"
                        else -> null
                    }
                    PlaybackOptionRow(PlaybackChoice(destination.name, destination.title, detail), navigation = true) { page = destination }
                }
            }
            PlaybackMenuPage.LINES -> {
                if (lines.isEmpty()) item { MenuMessage("这个频道暂无可用线路") }
                items(lines, key = { it.id }) { choice ->
                    PlaybackOptionRow(choice) { onLine(choice.id.toInt()); onDismiss() }
                }
            }
            PlaybackMenuPage.PICTURE -> items(PictureMode.entries) { mode ->
                PlaybackOptionRow(PlaybackChoice(mode.name, mode.label, selected = mode == pictureMode)) { onPicture(mode) }
            }
            PlaybackMenuPage.SLEEP -> {
                remainingMinutes?.let { item(key = "remaining") { MenuMessage("剩余 $it 分钟后停止播放") } }
                items(listOf(0, 15, 30, 60, 90, 120), key = { it }) { minutes ->
                    PlaybackOptionRow(PlaybackChoice(minutes.toString(), if (minutes == 0) "关闭定时" else "$minutes 分钟",
                        selected = minutes == timerMinutes)) {
                        onSleep(minutes)
                    }
                }
            }
            PlaybackMenuPage.AUDIO, PlaybackMenuPage.SUBTITLES -> {
                if (tracks.none { ':' in it.id }) item {
                    MenuMessage(if (page == PlaybackMenuPage.AUDIO) "当前源暂无可选音轨" else "当前源未提供可选字幕")
                }
                items(tracks, key = { it.id }) { choice ->
                    PlaybackOptionRow(choice) {
                        player?.let { selectPlaybackTrack(it, if (page == PlaybackMenuPage.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT, choice.id) }
                    }
                }
            }
            PlaybackMenuPage.GUIDE -> {
                if (programmes.isEmpty()) item { MenuMessage("暂无节目单，直播源未提供匹配数据或尚未加载完成") }
                val now = Date()
                val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
                items(programmes) { programme ->
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
                        .background(if (focused) UiColors.Accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("${formatter.format(programme.start.toInstant())} – ${formatter.format(programme.end.toInstant())}",
                            style = MaterialTheme.typography.labelMedium, color = AppleUi.Secondary)
                        Text(programme.title, style = MaterialTheme.typography.bodyLarge)
                        if (!now.before(programme.start) && now.before(programme.end)) {
                            Text("正在播", color = UiColors.Live, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    ListSeparator()
                }
            }
        }
    }
}

@Composable
internal fun PlaybackOptionsDialog(
    title: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
            .height(minOf(600f, LocalConfiguration.current.screenHeightDp * 0.86f).dp)
            .glassSurface(shape = AppleUi.Panel, transparency = getGlassTransparency(context) / 100f).padding(12.dp)) {
            PageHeader(title = title, onBack = onBack)
            key(title) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("playback-options-list"), content = content)
            }
            Spacer(Modifier.height(8.dp))
            DialogFooter(primary = UiAction("关闭", icon = UiIcons.X, onClick = onDismiss))
        }
    }
}

@Composable
private fun MenuMessage(message: String) {
    Text(message, Modifier.padding(16.dp), color = AppleUi.Secondary, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun PlaybackOptionRow(choice: PlaybackChoice, navigation: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .onFocusChanged { focused = it.isFocused }
        .clip(AppleUi.Control)
        .background(if (focused) UiColors.Accent.copy(alpha = 0.08f)
            else if (choice.selected) AppleUi.SubtleBlue else MaterialTheme.colorScheme.surface)
        .border(if (focused) 2.dp else 0.dp,
            if (focused) UiColors.Accent else androidx.compose.ui.graphics.Color.Transparent, AppleUi.Control)
        .selectable(choice.selected, enabled = choice.enabled, role = if (navigation) Role.Button else Role.RadioButton, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(choice.title, style = MaterialTheme.typography.bodyLarge,
                color = if (choice.enabled) MaterialTheme.colorScheme.onSurface else AppleUi.Secondary)
            choice.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppleUi.Secondary) }
        }
        Spacer(Modifier.width(12.dp))
        if (navigation) Icon(UiIcons.ChevronRight, null, tint = AppleUi.Secondary, modifier = Modifier.size(18.dp))
        else RadioButton(selected = choice.selected, onClick = null, enabled = choice.enabled)
    }
    ListSeparator()
}
