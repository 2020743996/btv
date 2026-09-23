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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

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
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
    }

    @Test fun homeNavigationAndChannelActions() {
        var search = false
        var openedSources = false
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
                        onOpenSources = { openedSources = true }, onOpenSettings = {})
                }
            }
        }
        compose.onNodeWithContentDescription("搜索").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertTrue(search) }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("管理频道源").performClick()
        compose.runOnIdle { assertTrue(openedSources) }
        compose.onNodeWithText(channels[0].name).performTouchInput { click() }
        compose.runOnIdle { assertEquals(channels[0].name, played) }
        compose.onAllNodesWithContentDescription("收藏")[0].performTouchInput { click() }
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.onNodeWithText("节目单").performClick()
        compose.onAllNodesWithText("暂无正在播出的节目").onFirst().assertExists()
        compose.onNodeWithText("频道").performClick()
        screenshot("home")
        if (landscape) compose.onNodeWithText("卫视").performClick()
        else compose.onNodeWithText("卫视 2").performScrollTo().performClick()
        compose.onNodeWithText(channels[0].name).assertDoesNotExist()
        compose.onNodeWithText("湖南卫视").assertIsDisplayed()
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
