package com.example.iptvplayer

import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var reloadKey by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        // 返回首页时刷新收藏和设置。频道数据有十分钟缓存，不会重复下载。
        reloadKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IptvPlayerTheme(fontScale = fontScaleFor(getFontSize(this))) {
                ChannelListScreen(
                    reloadKey = reloadKey,
                    onReload = {
                        ChannelCache.invalidate()
                        reloadKey++
                    },
                    onChannelClick = { channel ->
                        addRecentChannel(this, channel.name)
                        startActivity(PlayerActivity.createIntent(this, channel))
                    }
                )
            }
        }
    }
}

private data class LoadedSource(val text: String, val channels: List<Channel>)

@Composable
fun ChannelListScreen(reloadKey: Int, onReload: () -> Unit, onChannelClick: (Channel) -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sourceMissing by remember { mutableStateOf(false) }
    var groupedChannels by remember { mutableStateOf<Map<String, List<Channel>>>(emptyMap()) }
    var favorites by remember { mutableStateOf(getFavorites(context)) }
    var elderMode by remember { mutableStateOf(isElderMode(context)) }
    var recentChannels by remember { mutableStateOf(getRecentChannels(context)) }
    var epgRevision by remember { mutableIntStateOf(0) }

    var isTesting by remember { mutableStateOf(false) }
    var testProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var testSummary by remember { mutableStateOf<String?>(null) }
    var testKey by remember { mutableIntStateOf(0) }

    // 本次启动是否已自动进入直播（冷启动直接播，返回列表后不再重复跳转）。
    var autoPlayedThisLaunch by rememberSaveable { mutableStateOf(false) }

    /**
     * 频道加载完成后，若本次启动还没播过就自动进入直播：
     * 优先播「最近观看」的频道（记忆上次），否则播第一个频道。
     * 没有任何频道时（如还没配置源）保持停留在列表/引导页。
     */
    fun autoPlayIfReady() {
        if (autoPlayedThisLaunch) return
        val all = groupedChannels.values.flatten()
        if (all.isEmpty()) return
        autoPlayedThisLaunch = true
        val target = recentChannels.firstNotNullOfOrNull { name -> all.firstOrNull { it.name == name } }
            ?: all.firstOrNull()
        if (target != null) onChannelClick(target)
    }

    LaunchedEffect(testKey) {
        if (testKey == 0) return@LaunchedEffect
        isTesting = true
        testSummary = null
        testProgress = null
        try {
            val tested = testAllChannels(context, groupedChannels.values.flatten()) { done, total ->
                testProgress = done to total
            }
            groupedChannels = tested.groupBy { it.group }
            ChannelCache.replaceChannels(tested)
            val available = tested.count { channel -> channel.urls.isNotEmpty() }
            val resolutionDetected = tested.count { channel ->
                channel.lineQuality.orEmpty().any { it.usable && it.resolution != null }
            }
            testSummary = "测速完成：$available 个可用，$resolutionDetected 个识别清晰度"
            AppLog.log(
                "一键测速完成：$available 可用 / ${tested.size} 频道，" +
                    "$resolutionDetected 个识别清晰度"
            )
        } catch (e: Exception) {
            testSummary = "测速失败：${e.message ?: "未知错误"}"
            AppLog.log("一键测速失败")
        } finally {
            isTesting = false
            testProgress = null
        }
    }

    LaunchedEffect(reloadKey) {
        errorMessage = null
        sourceMissing = false
        elderMode = isElderMode(context)
        favorites = getFavorites(context)
        recentChannels = getRecentChannels(context)

        val sourceUrls = getM3uUrls(context)
        if (sourceUrls.isEmpty()) {
            sourceMissing = true
            isLoading = false
            return@LaunchedEffect
        }
        val cached = ChannelCache.freshChannels(sourceUrls)
        if (cached != null) {
            groupedChannels = cached.groupBy { it.group }
            isLoading = false
            autoPlayIfReady()
            return@LaunchedEffect
        }

        isLoading = true
        try {
            // 多个频道源互不依赖，并行下载可避免慢源依次拖长等待时间。
            val loadedSources = coroutineScope {
                sourceUrls.map { url ->
                    async {
                        try {
                            val text = downloadM3u(url)
                            LoadedSource(text, parseM3u(text))
                        } catch (e: Exception) {
                            android.util.Log.w("IptvPlayer", "源下载失败（跳过）：$url", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            val merged = mergeChannels(loadedSources.flatMap { it.channels })
            if (merged.isEmpty()) {
                val stale = ChannelCache.staleChannels(sourceUrls)
                if (stale != null) {
                    groupedChannels = stale.groupBy { it.group }
                    testSummary = "网络暂时不可用，已显示上次加载的频道"
                    AppLog.log("源加载失败，使用旧缓存：${stale.size} 个频道")
                } else {
                    errorMessage = "所有频道源都加载失败，请检查地址或网络"
                    AppLog.log("加载失败：所有源都不可用")
                }
            } else {
                ChannelCache.update(merged, sourceUrls)
                groupedChannels = merged.groupBy { it.group }
                AppLog.log("加载成功：${merged.size} 个频道（${loadedSources.size} 个源）")

                val epgSource = loadedSources.firstOrNull { extractEpgUrl(it.text) != null }?.text
                if (epgSource != null) {
                    // EPG 是增强信息，不阻塞频道列表先显示。
                    launch {
                        loadEpg(context, epgSource)
                        epgRevision++
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("IptvPlayer", "加载频道列表失败", e)
            errorMessage = e.message ?: "未知错误"
        } finally {
            isLoading = false
            // 下载成功或回退到旧缓存后，自动进入直播（首个加载周期内只触发一次）。
            autoPlayIfReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background).systemBarsPaddingCompat()
            
    ) {
        when {
            isLoading -> LoadingScreen("正在整理频道…")
            sourceMissing -> FirstRunScreen(
                onAddSource = { context.startActivity(Intent(context, AddressActivity::class.java)) }
            )
            errorMessage != null -> ErrorScreen(
                errorMessage!!,
                onRetry = onReload,
                onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            )
            else -> ChannelList(
                groupedChannels = groupedChannels,
                favorites = favorites,
                recentNames = recentChannels,
                elderMode = elderMode,
                isTesting = isTesting,
                testProgress = testProgress,
                testSummary = testSummary,
                epgRevision = epgRevision,
                onChannelClick = onChannelClick,
                onToggleFavorite = { channel ->
                    toggleFavorite(context, channel.name)
                    favorites = getFavorites(context)
                },
                onSpeedTest = { testKey++ },
                onRefresh = onReload,
                onOpenSearch = { context.startActivity(Intent(context, SearchActivity::class.java)) },
                onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            )
        }
    }
}

@Composable
fun FirstRunScreen(onAddSource: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Text(
                "开始使用 btv",
                style = MaterialTheme.typography.headlineLarge.copy(brush = BrandGradient)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "添加一个 M3U 频道源，即可加载频道并开始播放。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            ActionButton("添加频道源", highlighted = true, icon = UiIcons.Plus, onClick = onAddSource)
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("频道加载失败", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(22.dp))
            FormActions(
                primary = UiAction(
                    label = "重新加载",
                    icon = UiIcons.Refresh,
                    accentColor = UiColors.Refresh,
                    onClick = onRetry
                ),
                secondary = UiAction(
                    label = "去设置",
                    icon = UiIcons.Sliders,
                    accentColor = UiColors.Settings,
                    onClick = onOpenSettings
                ),
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
    }
}

fun channelStatusText(channel: Channel): String? {
    val quality = channel.lineQuality ?: return null
    val best = sortUsableLines(quality).firstOrNull()
    val networkStatus = when {
        best == null -> "不可用"
        best.score >= 70 -> "流畅"
        else -> "一般"
    }
    return if (best?.resolution != null) "${best.resolution.label} · $networkStatus" else networkStatus
}

private data class ChannelGroup(val key: String, val name: String, val channels: List<Channel>)

@Composable
fun ChannelList(
    groupedChannels: Map<String, List<Channel>>,
    favorites: Set<String>,
    recentNames: List<String>,
    elderMode: Boolean,
    isTesting: Boolean,
    testProgress: Pair<Int, Int>?,
    testSummary: String?,
    epgRevision: Int,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onSpeedTest: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val allChannels = remember(groupedChannels) { groupedChannels.values.flatten() }
    // 预建 "频道名 → 频道" 映射，避免最近观看逐个线性查找（O(n²)）。
    val channelByName = remember(allChannels) { allChannels.associateBy { it.name } }
    val groups = remember(allChannels, channelByName, favorites, recentNames) {
        buildList {
            add(ChannelGroup("all", "全部频道", allChannels))
            val recent = recentNames.mapNotNull { name -> channelByName[name] }
            if (recent.isNotEmpty()) add(ChannelGroup("recent", "最近观看", recent))
            val favoriteChannels = allChannels.filter { it.name in favorites }
            if (favoriteChannels.isNotEmpty()) add(ChannelGroup("favorite", "我的收藏", favoriteChannels))
            groupedChannels.forEach { (name, channels) ->
                add(ChannelGroup("source:$name", name, channels))
            }
        }
    }
    var selectedGroupKey by remember { mutableStateOf("all") }
    val selectedGroup = groups.firstOrNull { it.key == selectedGroupKey } ?: groups.first()

    // 自适应：电视/平板用"左分组 + 右频道"两栏，手机用"顶部横向分组 + 下方列表"单栏。
    // 手机横屏仍使用单栏，避免 600~840dp 宽度被固定侧栏挤压。
    val isWide = usesTwoPaneChannelLayout(rememberWindowType())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isWide) 28.dp else 14.dp,
                    vertical = if (isWide) 20.dp else 12.dp
                )
        ) {
            PageHeader(
                title = "btv",
                subtitle = "直播频道  ·  ${allChannels.size} 个频道",
                actions = {
                    TopBarButtons(
                        elderMode,
                        isTesting,
                        testProgress,
                        onOpenSearch,
                        onRefresh,
                        onSpeedTest,
                        onOpenSettings
                    )
                }
            )

            val statusMessage = if (isTesting && testProgress != null) {
                "正在检测线路  ${testProgress.first}/${testProgress.second}"
            } else testSummary
            if (statusMessage != null) {
                Text(
                    statusMessage,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(if (isWide) 18.dp else 12.dp))

            if (isWide) {
                // ===== 电视/平板：左分组 + 右频道 两栏 =====
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .shadow(8.dp, MaterialTheme.shapes.large)
                            .border(1.dp, Color.White, MaterialTheme.shapes.large)
                            .background(Color.White.copy(alpha = 0.84f), MaterialTheme.shapes.large)
                            .padding(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(groups, key = { it.key }) { group ->
                            GroupRow(
                                name = group.name,
                                count = group.channels.size,
                                selected = group.key == selectedGroup.key,
                                onClick = { selectedGroupKey = group.key }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    ChannelListContent(
                        selectedGroup = selectedGroup,
                        favorites = favorites,
                        epgRevision = epgRevision,
                        onChannelClick = onChannelClick,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // ===== 手机：顶部横向分组 chips + 下方频道列表 =====
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(groups, key = { it.key }) { group ->
                        GroupChip(
                            name = group.name,
                            count = group.channels.size,
                            selected = group.key == selectedGroup.key,
                            onClick = { selectedGroupKey = group.key }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                ChannelListContent(
                    selectedGroup = selectedGroup,
                    favorites = favorites,
                    epgRevision = epgRevision,
                    onChannelClick = onChannelClick,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

internal fun usesTwoPaneChannelLayout(windowType: WindowType): Boolean =
    windowType == WindowType.EXPANDED

/** 顶部操作按钮行（电视放标题右侧，手机窄屏放第二行）。 */
@Composable
private fun TopBarButtons(
    elderMode: Boolean,
    isTesting: Boolean,
    testProgress: Pair<Int, Int>?,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSpeedTest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val compact = rememberWindowType() == WindowType.COMPACT
    fun testLabel(): String {
        val progress = testProgress
        return if (isTesting && progress != null) "${progress.first}/${progress.second}"
        else if (isTesting) "测速中" else "测速"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        ToolbarAction(UiIcons.Search, "搜索", onOpenSearch, showLabel = !compact, accentColor = UiColors.Search)
        ToolbarAction(UiIcons.Refresh, "刷新", onRefresh, showLabel = !compact, accentColor = UiColors.Refresh)
        if (!elderMode) {
            ToolbarAction(
                icon = UiIcons.Gauge,
                label = testLabel(),
                enabled = !isTesting,
                onClick = onSpeedTest,
                showLabel = !compact,
                accentColor = UiColors.Speed
            )
        }
        ToolbarAction(
            UiIcons.Sliders,
            "设置",
            onOpenSettings,
            showLabel = !compact,
            accentColor = UiColors.Settings
        )
    }
}

/** 频道列表主体（分组名 + 频道行），电视两栏与手机单栏共用。 */
@Composable
private fun ChannelListContent(
    selectedGroup: ChannelGroup,
    favorites: Set<String>,
    epgRevision: Int,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                selectedGroup.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${selectedGroup.channels.size} 个频道",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(selectedGroup.channels, key = { _, channel -> channel.name }) { index, channel ->
                // epgRevision 变化时重新读取当前节目（remember 以它为键触发重算）。
                val nowPlaying = remember(epgRevision, channel) { currentProgrammeTitle(channel) }
                ChannelRow(
                    channel = channel,
                    index = index + 1,
                    status = channelStatusText(channel),
                    nowPlaying = nowPlaying,
                    isFavorite = channel.name in favorites,
                    onClick = { onChannelClick(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) }
                )
            }
        }
    }
}

/** 手机模式用的横向分组切换。 */
@Composable
private fun GroupChip(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .shadow(
                elevation = if (focused) 7.dp else 2.dp,
                shape = MaterialTheme.shapes.large,
                ambientColor = UiColors.Live.copy(alpha = 0.14f),
                spotColor = UiColors.Live.copy(alpha = 0.12f)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) UiColors.Live else Color.White,
                MaterialTheme.shapes.large
            )
            .clickable(onClick = onClick)
            .background(
                if (selected) SolidColor(UiColors.Live.copy(alpha = 0.12f))
                else SolidColor(Color.White.copy(alpha = 0.82f)),
                MaterialTheme.shapes.large
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$name $count",
            color = if (selected) UiColors.Live else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) UiColors.Live else Color.White,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .background(
                if (active) SolidColor(UiColors.Live.copy(alpha = 0.08f))
                else SolidColor(Color.Transparent),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) BrandGradient else SolidColor(Color.Transparent))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            name,
            color = if (selected) UiColors.Live else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    index: Int,
    status: String?,
    nowPlaying: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var favoriteFocused by remember { mutableStateOf(false) }
    val nameColor = if (status == "不可用") {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    } else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) UiColors.Live else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .background(
                SolidColor(MaterialTheme.colorScheme.surface),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            index.toString().padStart(3, '0'),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp)
        )
        ChannelLogo(channel = channel, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = nameColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                nowPlaying ?: "${channel.urls.size} 条可选线路",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (status != null) {
            val statusColor = when {
                status.endsWith("流畅") -> MaterialTheme.colorScheme.secondary
                status.endsWith("一般") -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusColor))
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                status,
                color = statusColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .onFocusChanged { favoriteFocused = it.isFocused }
                .focusable()
                .border(
                    2.dp,
                    if (favoriteFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    MaterialTheme.shapes.small
                )
                .clickable(onClick = onToggleFavorite)
                .background(
                    if (isFavorite) UiColors.Favorite.copy(alpha = 0.12f)
                    else Color.Transparent,
                    MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = UiIcons.Heart,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) UiColors.Favorite
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
        }
        Icon(
            imageVector = UiIcons.Play,
            contentDescription = "播放",
            tint = if (isFocused) UiColors.Live
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
