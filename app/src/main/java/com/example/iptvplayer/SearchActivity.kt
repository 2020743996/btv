package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SearchActivity：频道搜索页。
 *
 * 双端适配：
 * - 有系统输入法的设备（手机/平板/标准 Android TV）：顶部输入框，
 *   聚焦自动弹出系统键盘（TV 上是 Gboard，支持语音和拼音）
 * - 完全没有输入法的电视盒子：左侧结果 + 右侧自绘字母键盘（配合拼音检索）
 *
 * 匹配规则见 PinyinSearch.kt：归一化名/全拼/拼音首字母三键任一命中，
 * 前缀命中排在包含命中前、包含命中排在仅拼音命中前。
 * 数据来自 ChannelCache（MainActivity 加载完频道后写入的内存缓存）。
 */
class SearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 拼音库初始化（带多音字词典）；重复调用无副作用
        ensurePinyin()
        setContent {
            IptvPlayerTheme(fontScale = fontScaleFor(getFontSize(this))) {
                SearchScreen(
                    onPlay = { channel ->
                        // 从搜索结果播放也算"看过"，记进最近观看
                        addRecentChannel(this, channel.name)
                        startActivity(PlayerActivity.createIntent(this, channel))
                    },
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    onPlay: (Channel) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(getSearchHistory(context)) }
    val allChannels = remember { ChannelCache.channels }
    val compact = rememberWindowType() == WindowType.COMPACT
    // 电视优先用系统键盘（Gboard for TV，支持语音/拼音）；没有输入法的盒子才用自绘键盘
    val useTvKeyboard = isTvDevice(context) && !hasSystemIme(context)

    // 检索键只在频道列表变化时算一次，避免每次按键全量重算拼音
    val channelsWithKeys = remember(allChannels) {
        allChannels.map { it to channelSearchKeys(it.name) }
    }
    val results = remember(query, channelsWithKeys) {
        if (query.isBlank()) {
            emptyList()
        } else {
            channelsWithKeys.mapNotNull { (channel, keys) ->
                matchTier(query, keys)?.let { tier -> Triple(channel, keys.normalized, tier) }
            }
                .sortedWith(compareBy({ it.third.rank }, { it.second }))
                .map { it.first }
        }
    }

    fun play(channel: Channel) {
        // 播放了结果才记历史：说明这次搜索词有效
        if (query.isNotBlank()) {
            addSearchHistory(context, query)
            history = getSearchHistory(context)
        }
        onPlay(channel)
    }

    // ===== 手机：上下布局（输入框在上，结果在下）；电视/平板：左右布局 =====
    val horizontalPadding = if (compact) 16.dp else 28.dp
    val verticalPadding = if (compact) 14.dp else 22.dp

    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            SearchHeader(allChannels.size)
            Spacer(modifier = Modifier.height(10.dp))
            SearchTextField(query, onQueryChange = { query = it })
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("清空", onClick = { query = "" })
                ActionButton("关闭", onClick = onClose)
            }
            Spacer(modifier = Modifier.height(10.dp))
            SearchHistoryRow(history, onPick = { query = it })
            SearchResults(
                query = query,
                results = results,
                onPlay = ::play,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SearchHeader(allChannels.size)
                Spacer(modifier = Modifier.height(14.dp))
                if (useTvKeyboard) {
                    SearchInputBox(query)
                } else {
                    SearchTextField(query, onQueryChange = { query = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (useTvKeyboard) {
                        ActionButton("删除", onClick = { query = query.dropLast(1) })
                    }
                    ActionButton("清空", onClick = { query = "" })
                    ActionButton("关闭", onClick = onClose)
                }
                Spacer(modifier = Modifier.height(10.dp))
                SearchHistoryRow(history, onPick = { query = it })
                SearchResults(
                    query = query,
                    results = results,
                    onPlay = ::play,
                    modifier = Modifier.weight(1f)
                )
            }

            if (useTvKeyboard) {
                Spacer(modifier = Modifier.width(24.dp))
                TvKeyboard(onKey = { query += it })
            }
        }
    }
}

@Composable
private fun SearchHeader(channelCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "搜索频道",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge.copy(brush = BrandGradient),
            modifier = Modifier.weight(1f)
        )
        Text(
            "$channelCount 个频道",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

/** 手机/平板：系统输入法输入框，打开页面自动聚焦弹键盘 */
@Composable
private fun SearchTextField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("输入频道名或拼音，如：hnws") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** 电视：展示当前输入内容的输入框（实际输入靠右侧屏上键盘） */
@Composable
private fun SearchInputBox(query: String) {
    Text(
        text = if (query.isEmpty()) "输入频道名称" else query,
        color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    )
}

/** 搜索历史：输入为空时显示，点选直接填入查询词 */
@Composable
private fun SearchHistoryRow(history: List<String>, onPick: (String) -> Unit) {
    if (history.isEmpty()) return
    Column {
        Text("最近搜索", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { word ->
                HistoryChip(word, onClick = { onPick(word) })
            }
        }
    }
}

@Composable
private fun HistoryChip(word: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(36.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .background(
                if (focused) BrandGradient else SolidColor(MaterialTheme.colorScheme.surfaceVariant),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            word,
            color = if (focused) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: List<Channel>,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        Text(
            if (query.isBlank()) "输入拼音可搜中文频道（hnws → 湖南卫视）" else "没有匹配的频道",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 20.dp)
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(results, key = { _, channel -> channel.name }) { index, channel ->
                var focused by remember { mutableStateOf(false) }
                // 结果行：显示当前节目（EPG）。EPG 是异步加载的，
                // 不做 remember 缓存，每次重组重查，EPG 到达后能自动刷新。
                val nowPlaying = currentProgrammeTitle(channel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .border(
                            2.dp,
                            if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                            MaterialTheme.shapes.small
                        )
                        .clickable { onPlay(channel) }
                        .background(
                            if (focused) BrandGradient
                            else SolidColor(MaterialTheme.colorScheme.surface),
                            MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = if (focused) Color.White.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.width(30.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = if (focused) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nowPlaying != null) {
                            Text(
                                text = nowPlaying,
                                color = if (focused) Color.White.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // 显示线路数量，让用户知道这个频道有几条备用线路
                    Text(
                        text = "${channel.urls.size} 线路",
                        color = if (focused) Color.White.copy(alpha = 0.9f)
                        else MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
