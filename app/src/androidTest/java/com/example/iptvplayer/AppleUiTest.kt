package com.example.iptvplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

class AppleUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val landscape get() = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
    private val channels = listOf(
        Channel("CCTV-1 综合", "央视", listOf("https://example.com/1")),
        Channel("CCTV-5 体育", "央视", listOf("https://example.com/5")),
        Channel("湖南卫视", "卫视", listOf("https://example.com/hn")),
        Channel("浙江卫视", "卫视", listOf("https://example.com/zj"))
    )

    @Before fun viewport() {
        EpgCache.configureSources(emptySet())
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
    }

    @After fun clearGuideCache() {
        EpgCache.configureSources(emptySet())
    }

    @Test fun homeNavigationAndChannelActions() {
        var search = false
        var openedSettings = false
        var played = ""
        var favorite by mutableStateOf(false)
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(channels.groupBy { it.group },
                        if (favorite) setOf(channels[0].name) else emptySet(), emptyList(),
                        false, false, null, null, 0,
                        onChannelClick = { played = it.name }, onToggleFavorite = { favorite = !favorite },
                        onSpeedTest = {}, onRefresh = {}, onOpenSearch = { search = true },
                        onOpenSettings = { openedSettings = true })
                }
            }
        }
        compose.onNodeWithContentDescription("搜索").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertTrue(search) }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("刷新频道").assertIsDisplayed()
        compose.onNodeWithText("管理频道源").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText(channels[0].name).performTouchInput { click() }
        compose.runOnIdle { assertEquals(channels[0].name, played) }
        compose.onAllNodesWithContentDescription("收藏")[0].performTouchInput { click() }
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.onNodeWithText("收藏 1").performScrollTo().performClick()
        compose.onNodeWithText(channels[0].name).assertIsDisplayed()
        compose.onNodeWithText(channels[1].name).assertDoesNotExist()
        compose.onAllNodesWithText("节目单").onFirst().performClick()
        compose.onAllNodesWithText("暂无节目单").onFirst().assertExists()
        compose.onAllNodesWithText("设置").onFirst().performClick()
        compose.runOnIdle { assertTrue(openedSettings) }
        compose.onAllNodesWithText("直播").onFirst().performClick()
        screenshot("home")
        if (landscape) compose.onNodeWithText("卫视 2").performClick()
        else compose.onNodeWithText("卫视 2").performScrollTo().performClick()
        compose.onNodeWithText(channels[0].name).assertDoesNotExist()
        compose.onNodeWithText("湖南卫视").assertIsDisplayed()
    }

    @Test fun guideShowsSummaryAndExpandsOneChannelAtATime() {
        val now = System.currentTimeMillis()
        val first = Channel("测试频道一", "测试", listOf("https://example.com/a"), tvgIds = listOf("guide-a"))
        val second = Channel("测试频道二", "测试", listOf("https://example.com/b"), tvgIds = listOf("guide-b"))
        val sources = setOf("https://example.com/test.xml")
        EpgCache.configureSources(sources)
        EpgCache.update(
            mapOf(
                "guide-a" to listOf(
                    Programme("guide-a", Date(now - 5 * 60_000), Date(now + 10 * 60_000), "当前新闻"),
                    Programme("guide-a", Date(now + 10 * 60_000), Date(now + 40 * 60_000), "下一档节目"),
                    Programme("guide-a", Date(now + 40 * 60_000), Date(now + 70 * 60_000), "晚间电影")
                ),
                "guide-b" to listOf(
                    Programme("guide-b", Date(now - 5 * 60_000), Date(now + 10 * 60_000), "频道二正在播"),
                    Programme("guide-b", Date(now + 10 * 60_000), Date(now + 40 * 60_000), "频道二下一档")
                )
            ),
            sources
        )
        var played = ""
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(listOf(first, second).groupBy { it.group }, emptySet(), emptyList(),
                        false, false, null, null, 1, onChannelClick = { played = it.name },
                        onToggleFavorite = {}, onSpeedTest = {}, onRefresh = {}, onOpenSearch = {},
                        onOpenSettings = {})
                }
            }
        }
        compose.onAllNodesWithText("节目单").onLast().performClick()
        compose.onNodeWithText("当前新闻").assertIsDisplayed()
        compose.onNodeWithText("下一档节目").assertIsDisplayed()
        compose.onNodeWithText("晚间电影").assertDoesNotExist()

        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithText("晚间电影").assertIsDisplayed()
        compose.onAllNodesWithText("当前新闻").onLast().performClick()
        compose.runOnIdle { assertEquals(first.name, played) }

        compose.onNodeWithText(second.name).performClick()
        compose.onAllNodesWithText("频道二下一档").onFirst().assertIsDisplayed()
        compose.onNodeWithText("晚间电影").assertDoesNotExist()
        compose.onAllNodesWithText("频道二下一档").onLast().performClick()
        compose.onNodeWithText("此节目暂不支持回看。", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(first.name, played) }
    }

    @Test fun guideDateSwitchShowsThatDaysSummaryAndLongNamesStayReadable() {
        val zone = ZoneId.systemDefault()
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val start = tomorrow.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val longName = "非常长的频道名称用于验证节目单摘要不会挤压时间与操作"
        val longTitle = "非常长的节目标题用于验证节目单摘要在窄屏中能够正确省略且布局不溢出"
        val channel = Channel(longName, "测试", listOf("https://example.com/long"),
            tvgIds = listOf("guide-tomorrow"))
        val sources = setOf("https://example.com/guide-tomorrow.xml")
        EpgCache.configureSources(sources)
        EpgCache.update(mapOf("guide-tomorrow" to listOf(
            Programme("guide-tomorrow", Date(start), Date(start + 30 * 60_000), longTitle),
            Programme("guide-tomorrow", Date(start + 30 * 60_000), Date(start + 60 * 60_000), "午间节目")
        )), sources)

        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(mapOf(channel.group to listOf(channel)), emptySet(), emptyList(),
                        false, false, null, null, 1, onChannelClick = {}, onToggleFavorite = {},
                        onSpeedTest = {}, onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
                }
            }
        }
        compose.onAllNodesWithText("节目单").onLast().performClick()
        val tomorrowLabel = DateTimeFormatter.ofPattern("M月d日").format(tomorrow)
        compose.onNodeWithText(tomorrowLabel).performClick()
        compose.onNodeWithText(longName).assertIsDisplayed()
        compose.onNodeWithText(longTitle).assertIsDisplayed()
        compose.onNodeWithText("午间节目").assertIsDisplayed()
    }

    @Test fun recentChannelIsFeaturedWithoutRecordingActions() {
        var played = ""
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(channels.groupBy { it.group }, emptySet(), listOf(channels[2].name),
                        false, false, null, null, 0,
                        onChannelClick = { played = it.name }, onToggleFavorite = {}, onSpeedTest = {},
                        onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
                }
            }
        }
        compose.onNodeWithText("继续观看").assertIsDisplayed()
        compose.onNodeWithContentDescription("继续观看 ${channels[2].name}").performClick()
        compose.runOnIdle { assertEquals(channels[2].name, played) }
        compose.onNodeWithText("录制").assertDoesNotExist()
    }

    @Test fun staleRecentHistoryDoesNotCreateContinueCard() {
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(channels.groupBy { it.group }, emptySet(), listOf("已删除的频道"),
                        false, false, null, null, 0, onChannelClick = {}, onToggleFavorite = {},
                        onSpeedTest = {}, onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
                }
            }
        }
        compose.onNodeWithText("继续观看").assertDoesNotExist()
        compose.onNodeWithText("最近 0").performScrollTo().performClick()
        compose.onNodeWithText("暂无最近观看频道").assertIsDisplayed()
    }

    @Test fun longRecentChannelKeepsContinueActionVisible() {
        val longName = "非常长的频道名称用于验证窄屏卡片不会遮挡播放操作"
        val channel = Channel(longName, "测试分组", listOf("https://example.com/long"))
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(mapOf(channel.group to listOf(channel)), emptySet(), listOf(longName),
                        false, false, null, null, 0, onChannelClick = {}, onToggleFavorite = {},
                        onSpeedTest = {}, onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
                }
            }
        }
        compose.onNodeWithContentDescription("继续观看 $longName").assertIsDisplayed()
        compose.onNodeWithText("播放").assertIsDisplayed()
    }

    @Test fun recentCardIsHiddenWithoutHistory() {
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().systemBarsPaddingCompat()) {
                    ChannelList(channels.groupBy { it.group }, emptySet(), emptyList(), false, false,
                        null, null, 0, onChannelClick = {}, onToggleFavorite = {}, onSpeedTest = {},
                        onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
                }
            }
        }
        compose.onNodeWithText("继续观看").assertDoesNotExist()
        compose.onNodeWithText("全部频道").assertIsDisplayed()
        compose.onNodeWithText("最近 0").assertIsDisplayed()
    }

    @Test fun settingsLargeFontKeepsFormActions() {
        var closed = false
        compose.setContent { IptvPlayerTheme { SettingsScreen({}, {}, { closed = true }) } }
        compose.onNodeWithText("特大").performScrollTo().performClick()
        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        screenshot("settings-large-font")
        compose.onNodeWithText("取消").performTouchInput { click() }
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun managementUsesNavigationRows() {
        var opened = false
        compose.setContent { IptvPlayerTheme { AdminScreen({ opened = true }, {}, {}, {}) } }
        screenshot("management")
        compose.onNodeWithText("源地址管理").performTouchInput { click() }
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun playbackChannelPanelStaysUsable() {
        var selected = -1
        compose.setContent {
            IptvPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF202124))) {
                    ChannelSelectOverlay(channels, 0, 0, 0, { selected = it }, {})
                }
            }
        }
        screenshot("channel-panel")
        compose.onNodeWithText("湖南卫视").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, selected) }
    }

    @Test fun touchAndSystemBackClosePlaybackChannelPanel() {
        var panel by mutableStateOf(false)
        compose.setContent {
            IptvPlayerTheme {
                PlayerScreen(
                    player = null, playerState = PlayerUiState.PLAYING, errorMessage = null,
                    channelName = channels[0].name, channelPosition = "1/4", channelTvgIds = emptyList(),
                    channelLogoUrl = null, loadingMessage = "", linePosition = "线路 1/1",
                    playbackQuality = null, channelInfoVisible = false, channels = channels,
                    channelListVisible = panel, channelListSelection = 0, playingIndex = 0,
                    onChannelSelected = {}, onOpenChannels = { panel = true },
                    onCloseChannels = { panel = false }, onRetry = {}, onBack = {}
                )
            }
        }
        screenshot("playback-actions")
        compose.onNodeWithText("频道").performClick()
        screenshot("playback-panel")
        compose.onNodeWithContentDescription("关闭频道列表").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(!panel) }
        compose.onNodeWithText("频道").performClick()
        compose.onNodeWithContentDescription("关闭频道列表").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue(!panel) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val orientation = if (landscape) "landscape" else "portrait"
        File(instrumentation.targetContext.getExternalFilesDir(null), "apple-$name-$orientation.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
