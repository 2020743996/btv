package com.example.iptvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.liquidglass.compose.GlassRefraction
import dev.liquidglass.compose.GlassShape
import dev.liquidglass.compose.GlassStyle
import dev.liquidglass.compose.liquidGlass
import dev.liquidglass.compose.liquidGlassProvider
import dev.liquidglass.compose.rememberLiquidGlassProviderState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

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
                    },
                    onReplayClick = { channel, programme ->
                        addRecentChannel(this, channel.name)
                        startActivity(PlayerActivity.createCatchupIntent(this, channel, programme))
                    }
                )
            }
        }
    }
}

@Composable
fun ChannelListScreen(
    reloadKey: Int,
    onReload: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onReplayClick: (Channel, Programme) -> Unit = { _, _ -> }
) {
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
    val scope = rememberCoroutineScope()
    var testJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        (context as ComponentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshConfiguredEpg(context)
                epgRevision++
                delay(60_000)
                if (!isLoading && SourceStatuses.channels.any { it.health != SourceHealth.NORMAL }) {
                    onReload()
                }
            }
        }
    }

    // 本次启动是否已自动进入直播（冷启动直接播，返回列表后不再重复跳转）。
    var autoPlayedThisLaunch by rememberSaveable { mutableStateOf(false) }

    /**
     * 频道加载完成后，若本次启动还没播过就自动进入直播：
     * 优先播「最近观看」的频道（记忆上次），否则播第一个频道。
     * 没有任何频道时（如还没配置源）保持停留在列表/引导页。
     */
    fun autoPlayIfReady() {
        if (autoPlayedThisLaunch) return
        if (!shouldAutoPlay(getStartupMode(context), isTvDevice(context))) return
        val all = groupedChannels.values.flatten()
        if (all.isEmpty()) return
        autoPlayedThisLaunch = true
        // 预建 "名字 → 频道" 映射，最近观看逐个匹配时不用每次都线性扫全表。
        val byName = all.associateBy { it.name }
        val target = recentChannels.firstNotNullOfOrNull { byName[it] }
            ?: all.firstOrNull()
        if (target != null) onChannelClick(target)
    }

    fun toggleTest() {
        if (isTesting) {
            testJob?.cancel()
            return
        }
        isTesting = true
        testSummary = null
        testProgress = null
        val expectedRevision = ChannelCache.revision
        val sources = getM3uUrls(context)
        val candidates = groupedChannels.values.flatten()
        testJob = scope.launch {
            try {
                val tested = testAllChannels(context, candidates) { done, total ->
                    testProgress = done to total
                }
                if (sources != getM3uUrls(context) || !ChannelCache.replaceIfCurrent(expectedRevision, tested)) {
                    testSummary = "频道已更新，旧测速结果已忽略"
                    return@launch
                }
                recordLineResults(context, tested.flatMap { it.lineQuality.orEmpty() }.associate { it.url to it.usable })
                groupedChannels = ChannelCache.channels.groupBy { it.group }
                val available = tested.count { channel -> channel.urls.isNotEmpty() }
                val resolutionDetected = tested.count { channel ->
                    channel.lineQuality.orEmpty().any { it.usable && (it.measuredResolution ?: it.resolution) != null }
                }
                testSummary = "测速完成：$available 个可用，$resolutionDetected 个识别清晰度"
                AppLog.log(
                    "一键测速完成：$available 可用 / ${tested.size} 频道，" +
                        "$resolutionDetected 个识别清晰度"
                )
            } catch (e: CancellationException) {
                testSummary = "测速已取消"
                throw e
            } catch (e: Exception) {
                testSummary = "测速失败：${e.message ?: "未知错误"}"
                AppLog.log("一键测速失败")
            } finally {
                isTesting = false
                testProgress = null
            }
        }
    }

    LaunchedEffect(reloadKey) {
        testJob?.cancelAndJoin()
        errorMessage = null
        sourceMissing = false
        elderMode = isElderMode(context)
        favorites = getFavorites(context)
        recentChannels = getRecentChannels(context)

        val sourceUrls = getM3uUrls(context)
        if (sourceUrls.isEmpty()) {
            ChannelCache.update(emptyList(), emptyList())
            SourceStatuses.channels = emptyList()
            EpgCache.configureSources(emptySet())
            launch(Dispatchers.IO) { SourceSnapshots(context).retain(setOfNotNull(getEpgUrl(context))) }
            groupedChannels = emptyMap()
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
            val snapshots = SourceSnapshots(context)
            // 多个频道源互不依赖，并行下载可避免慢源依次拖长等待时间。
            val loadedSources = coroutineScope {
                val limit = Semaphore(4)
                sourceUrls.map { url ->
                    async {
                        limit.withPermit { loadChannelSource(context, url) }
                    }
                }.awaitAll()
            }

            val merged = withContext(Dispatchers.Default) { mergeChannels(loadedSources.flatMap { it.channels }) }
            if (sourceUrls != getM3uUrls(context)) return@LaunchedEffect
            SourceStatuses.channels = loadedSources.map { it.status }
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
                ChannelCache.update(merged, sourceUrls, complete = loadedSources.all { it.status.health == SourceHealth.NORMAL })
                groupedChannels = withContext(Dispatchers.Default) { merged.groupBy { it.group } }
                AppLog.log("加载完成：${merged.size} 个频道（${loadedSources.count { it.status.health == SourceHealth.NORMAL }}/${sourceUrls.size} 个源正常）")

                val epgSources = (loadedSources.flatMap { it.text?.let(::extractEpgUrls).orEmpty() } + listOfNotNull(getEpgUrl(context))).toSet()
                EpgCache.configureSources(epgSources)
                launch(Dispatchers.IO) { snapshots.retain(sourceUrls.toSet() + epgSources) }
                if (epgSources.isNotEmpty()) {
                    // EPG 是增强信息，不阻塞频道列表先显示。
                    launch {
                        refreshConfiguredEpg(context)
                        epgRevision++
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("IptvPlayer", "加载频道列表失败", e)
            errorMessage = e.message ?: "未知错误"
        } finally {
            isLoading = false
        }
        autoPlayIfReady()
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
                onOpenSources = { context.startActivity(Intent(context, AddressActivity::class.java)) }
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
                onReplayClick = onReplayClick,
                onToggleFavorite = { channel ->
                    toggleFavorite(context, channel.name)
                    favorites = getFavorites(context)
                },
                onSpeedTest = { toggleTest() },
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().widthIn(max = 520.dp)
        ) {
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
fun ErrorScreen(message: String, onRetry: () -> Unit, onOpenSources: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().widthIn(max = 520.dp)
        ) {
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
                    label = "管理频道源",
                    icon = UiIcons.Pencil,
                    accentColor = UiColors.Settings,
                    onClick = onOpenSources
                ),
                modifier = Modifier.widthIn(max = 420.dp),
                stackOnCompact = true
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
    val resolution = best?.measuredResolution ?: best?.resolution
    return if (resolution != null) "${resolution.label} · $networkStatus" else networkStatus
}

private data class ChannelGroup(val key: String, val name: String, val channels: List<Channel>)

private enum class MainSection(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GUIDE("节目单", UiIcons.Calendar),
    SETTINGS("设置", UiIcons.Sliders)
}

@Composable
private fun MainNavigationPanel(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("btv", Modifier.padding(start = 12.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        listOf(MainSection.GUIDE, MainSection.SETTINGS).forEach { section ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected == section) UiColors.Info.copy(alpha = 0.10f)
                        else if (focused) UiColors.Info.copy(alpha = 0.06f) else Color.Transparent)
                    .border(if (focused) 2.dp else 0.dp,
                        if (focused) UiColors.Info else Color.Transparent, MaterialTheme.shapes.medium)
                    .clickable { onSelect(section) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(section.icon, null, Modifier.size(20.dp),
                    tint = if (selected == section) UiColors.Info else AppleUi.Secondary)
                Text(section.title, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                    fontWeight = if (selected == section) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == section) UiColors.Info else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

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
    onOpenSettings: () -> Unit,
    onReplayClick: (Channel, Programme) -> Unit = { _, _ -> }
) {
    val allChannels = remember(groupedChannels) { groupedChannels.values.flatten() }
    // 预建 "频道名 → 频道" 映射，避免最近观看逐个线性查找（O(n²)）。
    val channelByName = remember(allChannels) { allChannels.associateBy { it.name } }
    val filters = remember(allChannels, groupedChannels, favorites, recentNames) {
        buildList {
            add(ChannelGroup("all", "全部", allChannels))
            add(ChannelGroup("favorite", "收藏", allChannels.filter { it.name in favorites }))
            add(ChannelGroup("recent", "最近", recentNames.mapNotNull(channelByName::get)))
            groupedChannels.forEach { (name, channels) ->
                add(ChannelGroup("source:$name", name, channels))
            }
        }
    }
    var selectedFilterKey by rememberSaveable { mutableStateOf("all") }
    var savedSection by rememberSaveable { mutableStateOf(MainSection.GUIDE.name) }
    val selectedSection = runCatching { MainSection.valueOf(savedSection) }.getOrDefault(MainSection.GUIDE)
    LaunchedEffect(selectedSection) { savedSection = selectedSection.name }
    var selectedGuideDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selectedGroup = filters.firstOrNull { it.key == selectedFilterKey }
        ?: filters.first()
    LaunchedEffect(selectedGroup.key) { selectedFilterKey = selectedGroup.key }

    // 电视和平板保留侧栏；手机使用底部主导航。
    val isWide = useWideChannelLayout()
    val useSidebarNavigation = isTvDevice(LocalContext.current) ||
        LocalConfiguration.current.smallestScreenWidthDp >= 600
    val glassTransparency = getGlassTransparency(LocalContext.current) / 100f
    val glassState = rememberLiquidGlassProviderState()
    val glassStyle = remember(glassTransparency) {
        GlassStyle.Regular.copy(
            shape = GlassShape.RoundedRectangle(18.dp),
            blurRadius = 18.dp,
            refraction = GlassRefraction(height = 6.dp, amount = 6.dp),
            saturation = 1.16f,
            tint = Color.White.copy(alpha = (0.34f - glassTransparency * 0.5f).coerceIn(0.14f, 0.34f)),
            chromaticAberration = 0.06f,
            fallbackScrim = Color.White.copy(alpha = 0.94f)
        )
    }

    val navigate: (MainSection) -> Unit = { destination ->
        when (destination) {
            MainSection.GUIDE -> savedSection = destination.name
            MainSection.SETTINGS -> onOpenSettings()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().liquidGlassProvider(glassState)) {
            Row(Modifier.fillMaxSize()) {
                if (useSidebarNavigation) Spacer(Modifier.width(208.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .padding(bottom = if (useSidebarNavigation) 0.dp else 68.dp)
                        .padding(
                            horizontal = if (useSidebarNavigation || isWide) UiSpace.PageRegular else UiSpace.PageCompact,
                            vertical = if (isWide) 20.dp else 8.dp
                        )
                ) {
                    PageHeader(
                        title = selectedSection.title,
                        subtitle = "${allChannels.size} 个频道 · 选择节目或直接播放",
                        compactActions = !isWide,
                        actions = {
                            TopBarButtons(elderMode, isTesting, testProgress, onOpenSearch, onRefresh, onSpeedTest)
                        }
                    )

                    val statusMessage = if (isTesting && testProgress != null) {
                        "正在检测线路  ${testProgress.first}/${testProgress.second}"
                    } else testSummary
                    if (statusMessage != null) {
                        Text(statusMessage, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    val degradedSources = SourceStatuses.channels.count { it.health != SourceHealth.NORMAL }
                    if (degradedSources > 0) {
                        Text("$degradedSources 个频道源加载异常 · 到设置中查看", color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).clickable(onClick = onOpenSettings))
                    }

                    Spacer(Modifier.height(if (isWide) 14.dp else 8.dp))
                    HomeChannelFilters(
                        filters = filters,
                        selectedKey = selectedGroup.key,
                        isWide = isWide,
                        onSelect = { selectedFilterKey = it }
                    )
                    Spacer(Modifier.height(if (isWide) 12.dp else 8.dp))

                    if (EpgCache.configuredUrls.isEmpty()) {
                        Text("暂无节目单 · 在设置中添加 XMLTV 地址", color = AppleUi.Secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp).clickable(onClick = onOpenSettings))
                    } else if (SourceStatuses.epg.isNotEmpty() &&
                        SourceStatuses.epg.all { it.health == SourceHealth.FAILED }) {
                        Text("节目单加载失败 · 查看源状态", color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp).clickable(onClick = onOpenSettings))
                    }

                    ChannelListContent(
                        selectedGroup = selectedGroup,
                        favorites = favorites,
                        epgRevision = epgRevision,
                        guideDate = LocalDate.parse(selectedGuideDate),
                        onGuideDateChange = { selectedGuideDate = it.toString() },
                        onChannelClick = onChannelClick,
                        onReplayClick = onReplayClick,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.weight(1f)
                    )

                }
            }
        }

        if (useSidebarNavigation) {
            MainNavigationPanel(
                selected = selectedSection,
                onSelect = navigate,
                modifier = Modifier.align(Alignment.CenterStart).width(208.dp).fillMaxHeight()
                    .padding(start = 12.dp, top = 20.dp, bottom = 20.dp)
                    .liquidGlass(glassState, glassStyle)
            )
        } else {
            HomeBottomNavigation(
                selected = selectedSection,
                onSelect = navigate,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(68.dp)
                    .liquidGlass(glassState, glassStyle)
            )
        }
    }
}

internal fun usesTwoPaneChannelLayout(windowType: WindowType, heightDp: Int = Int.MAX_VALUE): Boolean =
    windowType != WindowType.COMPACT && heightDp >= 320

@Composable
private fun useWideChannelLayout(): Boolean = isTvDevice(LocalContext.current) ||
    usesTwoPaneChannelLayout(rememberWindowType(), LocalConfiguration.current.screenHeightDp)

/** 顶部操作按钮行（电视放标题右侧，手机窄屏放第二行）。 */
@Composable
private fun TopBarButtons(
    elderMode: Boolean,
    isTesting: Boolean,
    testProgress: Pair<Int, Int>?,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSpeedTest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    fun testLabel(): String {
        val progress = testProgress
        return if (isTesting && progress != null) "${progress.first}/${progress.second}"
        else if (isTesting) "测速中" else "测速"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolbarAction(UiIcons.Search, "搜索", onOpenSearch, showLabel = true)
        Box {
            ToolbarAction(UiIcons.MoreHorizontal, "更多", { menuOpen = !menuOpen }, showLabel = false)
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("刷新频道") }, leadingIcon = { Icon(UiIcons.Refresh, null) }, onClick = {
                    menuOpen = false; onRefresh()
                })
                if (!elderMode) DropdownMenuItem(text = { Text(if (isTesting) "取消测速" else testLabel()) },
                    leadingIcon = { Icon(if (isTesting) UiIcons.X else UiIcons.Gauge, null) }, onClick = {
                        menuOpen = false; onSpeedTest()
                    })
            }
        }
    }
}

@Composable
private fun HomeChannelFilters(
    filters: List<ChannelGroup>,
    selectedKey: String,
    isWide: Boolean,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth()
            .background(AppleUi.Chrome, AppleUi.Control)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(filters, key = { it.key }) { filter ->
            GroupChip(
                name = filter.name,
                count = filter.channels.size,
                selected = filter.key == selectedKey,
                onClick = { onSelect(filter.key) },
                compact = isWide
            )
        }
    }
}

@Composable
private fun HomeBottomNavigation(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.82f)))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            listOf(MainSection.GUIDE, MainSection.SETTINGS).forEach { section ->
                val active = selected == section
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp, vertical = 3.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(AppleUi.Control)
                        .background(if (active) UiColors.Info.copy(alpha = 0.08f)
                            else if (focused) UiColors.Info.copy(alpha = 0.04f) else Color.Transparent)
                        .border(if (focused) 2.dp else 0.dp,
                            if (focused) UiColors.Info else Color.Transparent, AppleUi.Control)
                        .clickable { onSelect(section) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(section.icon, contentDescription = section.title,
                        tint = if (active) UiColors.Info else AppleUi.Secondary,
                        modifier = Modifier.size(21.dp))
                    Text(section.title, color = if (active) UiColors.Info else AppleUi.Secondary,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1)
                }
            }
        }
    }
}

/** 频道列表主体（分组名 + 频道行），电视两栏与手机单栏共用。 */
@Composable
private fun ChannelListContent(
    selectedGroup: ChannelGroup,
    favorites: Set<String>,
    epgRevision: Int,
    guideDate: LocalDate,
    onGuideDateChange: (LocalDate) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onReplayClick: (Channel, Programme) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selectedGroup.key == "all") "全部频道" else selectedGroup.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        if (selectedGroup.channels.isEmpty()) {
            Text(
                when (selectedGroup.key) {
                    "favorite" -> "暂无收藏频道"
                    "recent" -> "暂无最近观看频道"
                    else -> "此分组暂无频道"
                },
                color = AppleUi.Secondary,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        } else {
            GuideScheduleContent(
                channels = selectedGroup.channels,
                favorites = favorites,
                selectedDate = guideDate,
                onDateChange = onGuideDateChange,
                epgRevision = epgRevision,
                onChannelClick = onChannelClick,
                onReplayClick = onReplayClick,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GuideScheduleContent(
    channels: List<Channel>,
    favorites: Set<String>,
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    epgRevision: Int,
    onChannelClick: (Channel) -> Unit,
    onReplayClick: (Channel, Programme) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(selectedDate, zone) { selectedDate.atStartOfDay(zone).toInstant().toEpochMilli() }
    val end = remember(selectedDate, zone) { selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    var selectedProgramme by remember { mutableStateOf<Programme?>(null) }
    var selectedProgrammeChannel by remember { mutableStateOf<Channel?>(null) }
    val now = System.currentTimeMillis()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日") }
    var expandedChannelName by rememberSaveable(selectedDate.toString()) { mutableStateOf<String?>(null) }
    val replayDays = remember(channels) { maxCatchupHistoryDays(channels) }
    val dateListState = rememberLazyListState(initialFirstVisibleItemIndex = replayDays)
    LaunchedEffect(replayDays) { dateListState.scrollToItem(replayDays) }
    LaunchedEffect(replayDays, selectedDate) {
        val earliestDate = LocalDate.now(zone).minusDays(replayDays.toLong())
        if (selectedDate.isBefore(earliestDate)) onDateChange(earliestDate)
    }

    LazyRow(state = dateListState, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 10.dp)) {
        items(replayDays + 7) { index ->
            val offset = index - replayDays
            val date = LocalDate.now(zone).plusDays(offset.toLong())
            val label = when (offset) {
                0 -> "今天"
                -1 -> "昨天"
                else -> dateFormatter.format(date)
            }
            GuideDateChip(label, date == selectedDate) {
                onDateChange(date)
            }
        }
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(channels, key = { it.name }) { channel ->
            val programmes = remember(channel.tvgIds, selectedDate, epgRevision) {
                EpgCache.guide(channel.tvgIds, Date(start), Date(end))
            }
            val activeIndex = programmes.indexOfFirst { now >= it.start.time && now < it.end.time }
            val preview = when {
                selectedDate.isBefore(LocalDate.now(zone)) -> programmes.take(2)
                activeIndex >= 0 -> programmes.drop(activeIndex).take(2)
                else -> programmes.filter { it.end.time > now }.take(2)
            }
            val expanded = expandedChannelName == channel.name
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(AppleUi.Control)
                            .background(if (expanded) UiColors.Info.copy(alpha = 0.05f) else Color.Transparent)
                            .clickable {
                                expandedChannelName = if (expanded) null else channel.name
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelLogo(channel, size = 42.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (preview.isEmpty()) {
                                Text("暂无节目单", color = AppleUi.Secondary, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                preview.forEachIndexed { index, programme ->
                                    val active = now >= programme.start.time && now < programme.end.time
                                    val canReplay = !active && programme.end.time <= now &&
                                        catchupPlaybackUrls(channel, programme, now).isNotEmpty()
                                    val label = when {
                                        active -> "正在播"
                                        programme.end.time <= now -> if (canReplay) "可回看" else "已播出"
                                        index == 0 && activeIndex < 0 -> "即将播出"
                                        else -> "接下来"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = when {
                                            active -> UiColors.Live
                                            canReplay -> UiColors.Info
                                            else -> AppleUi.Secondary
                                        },
                                            fontSize = 11.sp, maxLines = 1)
                                        Text(guideTimeFormat.format(programme.start.toInstant().atZone(zone)),
                                            Modifier.padding(start = 5.dp), color = AppleUi.Secondary, fontSize = 11.sp)
                                        Text(programme.title, Modifier.weight(1f).padding(start = 7.dp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Icon(UiIcons.ChevronRight,
                            contentDescription = if (expanded) "收起 ${channel.name} 节目单" else "展开 ${channel.name} 节目单",
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                            tint = if (expanded) UiColors.Info else AppleUi.Secondary)
                    }
                    ToolbarAction(
                        UiIcons.Heart,
                        if (channel.name in favorites) "取消收藏 ${channel.name}" else "收藏 ${channel.name}",
                        { onToggleFavorite(channel) }, showLabel = false, active = channel.name in favorites
                    )
                    ToolbarAction(UiIcons.Play, "播放 ${channel.name}", { onChannelClick(channel) }, showLabel = false)
                }
                if (expanded) {
                    if (programmes.isEmpty()) {
                        Text("所选日期暂无节目", Modifier.padding(start = 62.dp, top = 8.dp, bottom = 8.dp),
                            color = AppleUi.Secondary, fontSize = 13.sp)
                    } else {
                        programmes.forEach { programme ->
                            val active = now >= programme.start.time && now < programme.end.time
                            GuideProgrammeRow(
                                programme = programme,
                                active = active,
                                replayAvailable = !active && programme.end.time <= now &&
                                    catchupPlaybackUrls(channel, programme, now).isNotEmpty(),
                                zone = zone,
                                onClick = {
                                    when {
                                        active -> onChannelClick(channel)
                                        programme.end.time <= now && catchupPlaybackUrls(channel, programme, now).isNotEmpty() ->
                                            onReplayClick(channel, programme)
                                        else -> {
                                            selectedProgrammeChannel = channel
                                            selectedProgramme = programme
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                ListSeparator(inset = 62.dp)
            }
        }
    }
    selectedProgramme?.let { programme ->
        AlertDialog(
            onDismissRequest = { selectedProgramme = null },
            title = { Text(programme.title) },
            text = {
                Text("${DateTimeFormatter.ofPattern("M月d日 HH:mm").format(programme.start.toInstant().atZone(zone))} - " +
                    "${DateTimeFormatter.ofPattern("HH:mm").format(programme.end.toInstant().atZone(zone))}\n" +
                    if (programme.start.time > now) "节目尚未开始。" else "${selectedProgrammeChannel?.name ?: "此频道"} 未声明有效的 M3U 回看配置。")
            },
            confirmButton = { TextButton(onClick = { selectedProgramme = null }) { Text("关闭") } }
        )
    }
}

@Composable
private fun GuideProgrammeRow(
    programme: Programme,
    active: Boolean,
    replayAvailable: Boolean,
    zone: ZoneId,
    onClick: () -> Unit
) {
    val startTime = guideTimeFormat.format(programme.start.toInstant().atZone(zone))
    val endTime = guideTimeFormat.format(programme.end.toInstant().atZone(zone))
    Row(
        Modifier.fillMaxWidth().padding(start = 62.dp, top = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (active) UiColors.Live.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$startTime–$endTime", Modifier.width(92.dp),
            color = if (active) UiColors.Live else AppleUi.Secondary, fontSize = 12.sp)
        Text(programme.title, Modifier.weight(1f).padding(start = 8.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        when {
            active -> Text("正在播", Modifier.padding(start = 8.dp), color = UiColors.Live,
                fontSize = 11.sp, maxLines = 1)
            replayAvailable -> Text("回看", Modifier.padding(start = 8.dp), color = UiColors.Info,
                fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun GuideDateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val transparency = getGlassTransparency(LocalContext.current) / 100f
    Text(label, Modifier.height(44.dp).onFocusChanged { focused = it.isFocused }
        .glassSurface(shape = AppleUi.Chip, focused = focused, tinted = selected, transparency = transparency)
        .border(if (focused) 2.dp else 1.dp, if (focused) UiColors.Info else AppleUi.Separator, AppleUi.Chip)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        color = if (selected) UiColors.Info else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
}

/** 手机模式用的横向分组切换。 */
@Composable
private fun GroupChip(name: String, count: Int, selected: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(AppleUi.Chip)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) UiColors.Info else Color.Transparent,
                AppleUi.Chip
            )
            .clickable(onClick = onClick)
            .background(
                if (selected) SolidColor(Color.White)
                else SolidColor(Color.Transparent),
                AppleUi.Chip
            )
            .padding(horizontal = if (compact) 18.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$name $count",
            color = if (selected) UiColors.Info else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    var focused by remember { mutableStateOf(false) }
    val wide = useWideChannelLayout()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(AppleUi.Control)
            .background(if (focused) UiColors.Info.copy(alpha = 0.06f) else Color.White)
            .border(if (focused) 2.dp else 0.dp,
                if (focused) UiColors.Info else Color.Transparent, AppleUi.Control)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (wide) {
            Text(index.toString().padStart(3, '0'), color = AppleUi.Secondary,
                fontSize = 12.sp, modifier = Modifier.width(36.dp))
        }
        ChannelLogo(channel, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(listOfNotNull(nowPlaying ?: "${channel.urls.size} 条线路", status).joinToString(" · "),
                color = if (status == "不可用") MaterialTheme.colorScheme.error else AppleUi.Secondary,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ToolbarAction(UiIcons.Heart, if (isFavorite) "取消收藏" else "收藏", onToggleFavorite,
            showLabel = false, grouped = true, active = isFavorite,
            accentColor = if (isFavorite) UiColors.Favorite else AppleUi.Secondary)
    }
}

private val guideTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
