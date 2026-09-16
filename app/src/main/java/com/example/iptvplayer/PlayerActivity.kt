package com.example.iptvplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PlayerUiState { LOADING, PLAYING, ERROR }

class PlayerActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var playerState by mutableStateOf(PlayerUiState.LOADING)
    private var errorMessage by mutableStateOf<String?>(null)
    private var loadingMessage by mutableStateOf("正在连接直播源…")
    private var channelInfoVisible by mutableStateOf(true)
    private var playbackQuality by mutableStateOf<String?>(null)

    private var urls: List<String> = emptyList()
    private var currentLineIndex = 0
    private var channelName by mutableStateOf("")
    private var channelIndex = -1
    private var totalChannels = 0
    private var channelTvgIds: List<String> = emptyList()
    private var channelLogoUrl by mutableStateOf<String?>(null)
    private var currentAttemptUrl: String? = null
    private var currentAttemptLastResult: Boolean? = null
    private var playbackStartedForAttempt = false
    private var lineTransitionInProgress = false

    // 左侧悬浮频道选择面板：DPAD_LEFT 呼出，面板内上下移动、OK 播放、BACK/LEFT 关闭。
    private var channelListVisible by mutableStateOf(false)
    private var channelListSelection by mutableIntStateOf(0)

    private val playbackTimeout = Runnable {
        if (playerState == PlayerUiState.LOADING) {
            tryNextLine(if (playbackStartedForAttempt) "缓冲超时" else "连接超时")
        }
    }

    private val markPlaybackStable = Runnable {
        if (playerState == PlayerUiState.PLAYING && player?.isPlaying == true) {
            recordCurrentAttempt(success = true)
        }
    }

    private val hideChannelInfo = Runnable {
        channelInfoVisible = false
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 播放页沉浸式全屏：隐藏系统栏（状态栏/导航栏），滑动可临时呼出。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: emptyList()
        channelName = intent.getStringExtra(EXTRA_NAME) ?: ""

        val allChannels = ChannelCache.channels
        totalChannels = allChannels.size
        channelIndex = allChannels.indexOfFirst { it.name == channelName }
        val initialChannel = allChannels.getOrNull(channelIndex)
        channelTvgIds = initialChannel?.tvgIds ?: emptyList()
        channelLogoUrl = initialChannel?.logoUrl

        val playbackHttpClient = sharedHttpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            // 直播媒体连接可以持续数小时，仅清单/测速使用总请求时限。
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val httpFactory = OkHttpDataSource.Factory(playbackHttpClient)
            .setUserAgent(APP_USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                PlaybackTuning.MIN_BUFFER_MS,
                PlaybackTuning.MAX_BUFFER_MS,
                PlaybackTuning.START_BUFFER_MS,
                PlaybackTuning.REBUFFER_MS
            )
            .setBackBuffer(PlaybackTuning.BACK_BUFFER_MS, true)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(PlaybackTuning.LOAD_RETRY_COUNT)
            )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .also { exoPlayer ->
                exoPlayer.setHandleAudioBecomingNoisy(true)
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                cancelPlaybackTimeout()
                                lineTransitionInProgress = false
                                playbackStartedForAttempt = true
                                exoPlayer.videoFormat?.let { format ->
                                    updatePlaybackResolution(format.width, format.height)
                                }
                                playerState = PlayerUiState.PLAYING
                                loadingMessage = ""
                                scheduleStablePlaybackMark()
                                showChannelInfoBriefly()
                            }
                            Player.STATE_BUFFERING -> if (playerState != PlayerUiState.ERROR) {
                                mainHandler.removeCallbacks(markPlaybackStable)
                                playerState = PlayerUiState.LOADING
                                loadingMessage = if (playbackStartedForAttempt) {
                                    "网络波动，正在补充缓冲…"
                                } else {
                                    "正在连接直播源…"
                                }
                                schedulePlaybackTimeout()
                            }
                            Player.STATE_ENDED -> tryNextLine("直播已中断")
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        AppLog.log("$channelName 线路 ${currentLineIndex + 1} 播放错误：${error.errorCodeName}")
                        tryNextLine("线路不可用")
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        updatePlaybackResolution(videoSize.width, videoSize.height)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            scheduleStablePlaybackMark()
                        } else {
                            mainHandler.removeCallbacks(markPlaybackStable)
                        }
                    }
                })
            }

        setContent {
            IptvPlayerTheme(fontScale = fontScaleFor(getFontSize(this))) {
                PlayerScreen(
                    player = player,
                    playerState = playerState,
                    errorMessage = errorMessage,
                    channelName = channelName,
                    channelPosition = if (channelIndex >= 0) "${channelIndex + 1}/$totalChannels" else "",
                    channelTvgIds = channelTvgIds,
                    channelLogoUrl = channelLogoUrl,
                    loadingMessage = loadingMessage,
                    linePosition = if (urls.isEmpty()) "" else "线路 ${currentLineIndex + 1}/${urls.size}",
                    playbackQuality = playbackQuality,
                    channelInfoVisible = channelInfoVisible,
                    channels = allChannels,
                    channelListVisible = channelListVisible,
                    channelListSelection = channelListSelection,
                    playingIndex = channelIndex,
                    onChannelSelected = { index -> playChannelAt(index) },
                    onRetry = { startFromFirst() },
                    onBack = { finish() }
                )
            }
        }

        startFromFirst()
    }

    /** 换台/选台的公共入口：更新当前频道状态、记录最近观看并从头开始播放。 */
    private fun startChannel(channel: Channel, index: Int, total: Int, action: String) {
        channelListVisible = false
        channelIndex = index
        channelName = channel.name
        channelTvgIds = channel.tvgIds
        channelLogoUrl = channel.logoUrl
        urls = channel.urls
        totalChannels = total
        addRecentChannel(this, channel.name)
        AppLog.log("$action：$channelName（${index + 1}/$total）")
        startFromFirst()
    }

    private fun switchChannel(delta: Int) {
        val allChannels = ChannelCache.channels
        if (allChannels.isEmpty()) return

        val current = if (channelIndex in allChannels.indices) channelIndex else 0
        val newIndex = ((current + delta) % allChannels.size + allChannels.size) % allChannels.size
        startChannel(allChannels[newIndex], newIndex, allChannels.size, "换台")
    }

    // PlayerView 会优先消费方向键，因此需要在 Activity 最外层拦截换台按键。
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        // 悬浮频道面板打开时：方向键只移动选择，OK 播放，BACK/LEFT 关闭。
        if (channelListVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.repeatCount == 0) channelListSelection = (channelListSelection - 1).coerceAtLeast(0)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.repeatCount == 0 && channelListSelection < ChannelCache.channels.lastIndex) {
                        channelListSelection++
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    playChannelAt(channelListSelection)
                    return true
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    channelListVisible = false
                    return true
                }
                else -> return true // 面板打开时其余按键一律吃掉，避免误触
            }
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.repeatCount == 0) openChannelList()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.repeatCount == 0) switchChannel(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.repeatCount == 0) switchChannel(1)
                return true
            }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> {
                showChannelInfoBriefly()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** 呼出左侧悬浮频道面板，选中项定位到当前播放的频道。 */
    private fun openChannelList() {
        val allChannels = ChannelCache.channels
        if (allChannels.isEmpty()) return
        channelListSelection = channelIndex.coerceIn(0, allChannels.lastIndex)
        channelListVisible = true
    }

    /** 播放悬浮面板中选中的频道，并关闭面板。 */
    private fun playChannelAt(index: Int) {
        val allChannels = ChannelCache.channels
        if (index !in allChannels.indices) return
        startChannel(allChannels[index], index, allChannels.size, "选台")
        // 切换后短暂显示新频道信息，让用户确认已换台。
        showChannelInfoBriefly()
    }

    private fun startFromFirst() {
        lineTransitionInProgress = false
        urls = prioritizePlaybackUrls(urls) { url -> getFailCount(this, url) }
        currentLineIndex = 0
        if (urls.isEmpty()) {
            showPlaybackError("这个频道暂时没有可用线路，请稍后再试")
            return
        }
        startPlayback(urls.first(), "正在连接直播源…")
    }

    private fun tryNextLine(reason: String) {
        if (lineTransitionInProgress) return
        lineTransitionInProgress = true
        cancelPlaybackTimeout()
        recordCurrentAttempt(success = false)
        if (currentLineIndex + 1 < urls.size) {
            currentLineIndex++
            AppLog.log("$channelName $reason，切换到线路 ${currentLineIndex + 1}/${urls.size}")
            startPlayback(
                urls[currentLineIndex],
                "$reason，正在尝试备用线路…"
            )
        } else {
            showPlaybackError("当前频道的 ${urls.size} 条线路均无法播放，请稍后再试")
        }
        mainHandler.post { lineTransitionInProgress = false }
    }

    private fun startPlayback(url: String, message: String) {
        val exoPlayer = player ?: return
        cancelPlaybackTimeout()
        playerState = PlayerUiState.LOADING
        errorMessage = null
        loadingMessage = message
        channelInfoVisible = true
        currentAttemptUrl = url
        currentAttemptLastResult = null
        playbackStartedForAttempt = false
        playbackQuality = null
        mainHandler.removeCallbacks(markPlaybackStable)

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(PlaybackTuning.LIVE_TARGET_OFFSET_MS)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.0f)
                    .build()
            )
            .build()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        schedulePlaybackTimeout()
    }

    private fun updatePlaybackResolution(width: Int, height: Int) {
        if (!playbackStartedForAttempt) return
        if (width !in 160..8192 || height !in 120..4320) return
        val resolution = StreamResolution(width, height)
        playbackQuality = resolution.label
        currentAttemptUrl?.let { url ->
            ChannelCache.updateLineResolution(channelName, url, resolution)
        }
    }

    /** 每次播放尝试只记一次结果，避免错误回调和超时回调重复累计。 */
    private fun recordCurrentAttempt(success: Boolean) {
        val url = currentAttemptUrl ?: return
        if (currentAttemptLastResult == success) return
        currentAttemptLastResult = success
        recordLineResult(this, url, success)
        AppLog.log("$channelName 线路 ${currentLineIndex + 1}${if (success) "播放成功" else "播放失败，已记录"}")
    }

    private fun showPlaybackError(message: String) {
        cancelPlaybackTimeout()
        mainHandler.removeCallbacks(markPlaybackStable)
        player?.stop()
        errorMessage = message
        playerState = PlayerUiState.ERROR
    }

    private fun showChannelInfoBriefly() {
        channelInfoVisible = true
        mainHandler.removeCallbacks(hideChannelInfo)
        mainHandler.postDelayed(hideChannelInfo, CHANNEL_INFO_DURATION_MS)
    }

    private fun cancelPlaybackTimeout() {
        mainHandler.removeCallbacks(playbackTimeout)
    }

    private fun schedulePlaybackTimeout() {
        mainHandler.removeCallbacks(playbackTimeout)
        mainHandler.postDelayed(
            playbackTimeout,
            PlaybackTuning.timeoutMs(playbackStartedForAttempt)
        )
    }

    private fun scheduleStablePlaybackMark() {
        mainHandler.removeCallbacks(markPlaybackStable)
        if (currentAttemptLastResult != true && player?.isPlaying == true) {
            mainHandler.postDelayed(markPlaybackStable, PlaybackTuning.STABLE_PLAYBACK_MS)
        }
    }

    override fun onStart() {
        super.onStart()
        if (playerState != PlayerUiState.ERROR) {
            player?.play()
            if (playerState == PlayerUiState.LOADING) {
                schedulePlaybackTimeout()
            } else if (playerState == PlayerUiState.PLAYING) {
                scheduleStablePlaybackMark()
            }
        }
    }

    override fun onStop() {
        cancelPlaybackTimeout()
        mainHandler.removeCallbacks(markPlaybackStable)
        mainHandler.removeCallbacks(hideChannelInfo)
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URLS = "channel_urls"
        private const val EXTRA_NAME = "channel_name"
        private const val CHANNEL_INFO_DURATION_MS = 4_000L

        fun createIntent(context: Context, channel: Channel): Intent =
            Intent(context, PlayerActivity::class.java)
                .putStringArrayListExtra(EXTRA_URLS, ArrayList(channel.urls))
                .putExtra(EXTRA_NAME, channel.name)
    }
}

@UnstableApi
@Composable
fun PlayerScreen(
    player: ExoPlayer?,
    playerState: PlayerUiState,
    errorMessage: String?,
    channelName: String,
    channelPosition: String,
    channelTvgIds: List<String>,
    channelLogoUrl: String?,
    loadingMessage: String,
    linePosition: String,
    playbackQuality: String?,
    channelInfoVisible: Boolean,
    channels: List<Channel>,
    channelListVisible: Boolean,
    channelListSelection: Int,
    playingIndex: Int,
    onChannelSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    var epgRefreshTick by remember { mutableIntStateOf(0) }
    val lifecycle = (LocalContext.current as ComponentActivity).lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshConfiguredEpg()
                epgRefreshTick++
                delay(60_000)
            }
        }
    }
    LaunchedEffect(channelTvgIds, channelInfoVisible, channelListVisible) {
        if (!channelInfoVisible && !channelListVisible) return@LaunchedEffect
        // EPG 可能在进入播放页后才下载完成，先快速刷新两次，之后按节目单粒度刷新。
        delay(1_000)
        epgRefreshTick++
        delay(2_000)
        epgRefreshTick++
        while (true) {
            delay(30_000)
            epgRefreshTick++
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val lineDetails = listOfNotNull(
            linePosition.takeIf { it.isNotBlank() },
            playbackQuality
        ).joinToString(" · ")
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controllerShowTimeoutMs = 3_000
                    controllerAutoShow = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { view ->
                view.player = player
                view.useController = playerState == PlayerUiState.PLAYING
            }
        )

        when (playerState) {
            PlayerUiState.LOADING -> Box(
                modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(36.dp).height(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        channelName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(loadingMessage, color = Color(0xFFD2D6DA), fontSize = 14.sp)
                    if (linePosition.isNotEmpty()) {
                        Text(linePosition, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }

            PlayerUiState.ERROR -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂时无法播放", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        errorMessage ?: "未知错误",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(channelName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(22.dp))
                    FormActions(
                        primary = UiAction(
                            label = "重新尝试",
                            icon = UiIcons.Refresh,
                            accentColor = UiColors.Refresh,
                            onClick = onRetry
                        ),
                        secondary = UiAction(
                            label = "返回频道",
                            icon = UiIcons.ArrowLeft,
                            accentColor = UiColors.Info,
                            onClick = onBack
                        ),
                        modifier = Modifier.widthIn(max = 420.dp),
                        stackOnCompact = true
                    )
                }
            }

            PlayerUiState.PLAYING -> if (channelInfoVisible) {
                val schedule = remember(channelTvgIds, epgRefreshTick) {
                    EpgCache.schedule(channelTvgIds)
                }
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.9f)
                        .glassSurface(shape = AppleUi.Panel)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(
                            name = channelName,
                            logoUrl = channelLogoUrl,
                            size = 52.dp,
                            selected = false
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                channelName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                schedule.current?.let { "正在播  ${it.title}" } ?: lineDetails,
                                color = AppleUi.Secondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            schedule.next?.let { next ->
                                Text(
                                    "接下来  ${formatProgrammeTime(next)}  ${next.title}",
                                    color = UiColors.Info,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(listOf(channelPosition, lineDetails).filter { it.isNotBlank() }.joinToString(" · "),
                        color = AppleUi.Secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // 左侧悬浮频道列表：覆盖在画面上选台，不用返回列表页。
        if (channelListVisible) {
            ChannelSelectOverlay(
                channels = channels,
                selectedIndex = channelListSelection,
                playingIndex = playingIndex,
                epgRevision = epgRefreshTick,
                onChannelSelected = onChannelSelected
            )
        }
    }
}

/**
 * 播放页左侧悬浮的频道选择面板。
 * 选择由 Activity 的 dispatchKeyEvent 驱动（方向键移动、OK 播放、BACK/LEFT 关闭），
 * 这里只负责渲染：当前播放频道带播放图标，选中项高亮。
 */
@Composable
fun ChannelSelectOverlay(
    channels: List<Channel>,
    selectedIndex: Int,
    playingIndex: Int,
    epgRevision: Int,
    onChannelSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    // 选中项变化时滚动到可见位置（居中附近）。用无动画的 scrollToItem，
    // 避免逐帧动画在低端/模拟器环境造成主线程卡顿。
    LaunchedEffect(selectedIndex) {
        listState.scrollToItem((selectedIndex - 4).coerceAtLeast(0))
    }

    // 手机窄屏按屏幕比例取宽，电视/平板用固定宽度（窄一点，少遮挡视频）。
    val panelWidthModifier = if (rememberWindowType() == WindowType.COMPACT) {
        Modifier.fillMaxWidth(0.88f)
    } else {
        Modifier.width(320.dp)
    }
    Column(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxHeight()
            .then(panelWidthModifier)
            .glassSurface(shape = AppleUi.Panel)
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "频道列表",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${channels.size} 个频道",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 频道列表在播放过程中是静态的，用索引做 key 即可，避免重名频道引发冲突。
            itemsIndexed(channels) { index, channel ->
                val selected = index == selectedIndex
                val playing = index == playingIndex
                // EPG 节目名：面板打开时显示"正在播什么"。
                val nowPlaying = remember(channel, epgRevision) { currentProgrammeTitle(channel) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onChannelSelected(index) }
                        .background(
                            if (selected) SolidColor(UiColors.Info.copy(alpha = 0.08f))
                            else SolidColor(Color.Transparent),
                            MaterialTheme.shapes.small
                        )
                        .border(
                            2.dp,
                            if (selected) UiColors.Info else Color.Transparent,
                            MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (index + 1).toString().padStart(3, '0'),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.width(30.dp)
                    )
                    ChannelLogo(channel, size = 40.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nowPlaying != null) {
                            Text(
                                nowPlaying,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (playing) {
                        Icon(
                            UiIcons.Play,
                            contentDescription = "正在播放",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private val programmeTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private fun formatProgrammeTime(programme: Programme): String =
    programmeTimeFormat.format(programme.start.toInstant().atZone(ZoneId.systemDefault()))
