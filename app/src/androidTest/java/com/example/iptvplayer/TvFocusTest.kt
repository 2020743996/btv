package com.example.iptvplayer

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class TvFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun onlyTv() { assumeTrue(isTvDevice(compose.activity)) }

    @After fun clearGuideCache() { EpgCache.configureSources(emptySet()) }

    @Test fun remoteEnterOpensAddSourceForm() {
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            IptvPlayerTheme { AddressEditor(emptyList(), {}, {}) }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("添加").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("添加").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("添加频道源").assertIsDisplayed()
    }

    @Test fun remoteCanSelectGroupAndChannel() {
        lateinit var inputMode: InputModeManager
        var played = ""
        val channels = listOf(
            Channel("CCTV-1", "央视", listOf("https://example.com/cctv1.ts")),
            Channel("湖南卫视", "卫视", listOf("https://example.com/hunan.ts"))
        )
        compose.setContent {
            inputMode = LocalInputModeManager.current
            IptvPlayerTheme {
                ChannelList(channels.groupBy { it.group }, emptySet(), emptyList(), false, false,
                    null, null, 0, onChannelClick = { played = it.name }, onToggleFavorite = {},
                    onSpeedTest = {}, onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("卫视 1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("卫视 1").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("湖南卫视").assertIsDisplayed()
        compose.onNodeWithText("湖南卫视").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("湖南卫视").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("湖南卫视", played) }
    }

    @Test fun remoteCanExpandAndCollapseProgrammeGuide() {
        val now = System.currentTimeMillis()
        val channel = Channel("遥控器测试台", "测试", listOf("https://example.com/tv"),
            tvgIds = listOf("guide-tv"))
        val sources = setOf("https://example.com/tv-guide.xml")
        EpgCache.configureSources(sources)
        EpgCache.update(mapOf("guide-tv" to listOf(
            Programme("guide-tv", Date(now - 5 * 60_000), Date(now + 10 * 60_000), "遥控器当前节目"),
            Programme("guide-tv", Date(now + 10 * 60_000), Date(now + 40 * 60_000), "遥控器未来节目"),
            Programme("guide-tv", Date(now + 40 * 60_000), Date(now + 70 * 60_000), "遥控器隐藏节目")
        )), sources)

        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            IptvPlayerTheme {
                ChannelList(mapOf(channel.group to listOf(channel)), emptySet(), emptyList(), false,
                    false, null, null, 1, onChannelClick = {}, onToggleFavorite = {}, onSpeedTest = {},
                    onRefresh = {}, onOpenSearch = {}, onOpenSettings = {})
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onAllNodesWithText("节目单").onLast()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onAllNodesWithText("节目单").onLast().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("遥控器当前节目").assertIsDisplayed()
        compose.onNodeWithText("遥控器隐藏节目").assertDoesNotExist()

        compose.onNodeWithText(channel.name).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText(channel.name).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("遥控器隐藏节目").assertIsDisplayed()
        compose.onNodeWithText(channel.name).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText(channel.name).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("遥控器隐藏节目").assertDoesNotExist()
    }
}
