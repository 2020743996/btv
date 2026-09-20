package com.example.iptvplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

@UnstableApi
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class PlaybackMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val landscape get() = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
    private var picture by mutableStateOf(PictureMode.FIT)
    private var remaining by mutableStateOf<Int?>(null)
    private var selectedLine = -1
    private var closed = false
    private var channelsOpened = false
    private lateinit var inputMode: androidx.compose.ui.input.InputModeManager

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

    private fun showMenu(player: Player? = null, programmes: List<Programme> = emptyList()) {
        compose.setContent {
            inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
            IptvPlayerTheme(fontScale = 1.5f) {
                PlaybackMenu(player, (0..14).map { PlaybackChoice(it.toString(), "线路 ${it + 1}", "1080p", selected = it == 0) },
                    picture, remaining, programmes,
                    onLine = { selectedLine = it }, onPicture = { picture = it },
                    onSleep = { remaining = it.takeIf { it > 0 } },
                    onChannels = { channelsOpened = true }, onDismiss = { closed = true })
            }
        }
    }

    private fun open(title: String) {
        compose.onNodeWithTag("playback-options-list").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).performScrollTo().performTouchInput { click() }
    }
    private fun back() = compose.onNodeWithContentDescription("返回").performClick()

    @Test fun pictureAndSleepApplyImmediatelyWithFixedCloseAction() {
        showMenu()
        screenshot("menu")
        open("画面比例")
        open("裁切铺满")
        compose.runOnIdle { assertEquals(PictureMode.ZOOM, picture) }
        compose.onNodeWithText("裁切铺满").assertIsSelected()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        back()
        open("睡眠定时")
        open("120 分钟")
        compose.runOnIdle { assertEquals(120, remaining) }
        compose.onNodeWithText("120 分钟").assertIsSelected().assertIsDisplayed()
        screenshot("sleep")
        open("关闭定时")
        compose.runOnIdle { assertNull(remaining) }
        compose.onNodeWithText("关闭").performTouchInput { click() }
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun longLineListScrollsAndSelectsLastLine() {
        showMenu()
        open("播放线路")
        open("线路 15")
        compose.runOnIdle { assertEquals(14, selectedLine); assertTrue(closed) }
    }

    @Test fun absentTracksShowEmptyStatesAndChannelEntryWorks() {
        showMenu()
        open("音轨")
        compose.onNodeWithText("当前源暂无可选音轨").assertIsDisplayed()
        back()
        open("字幕")
        compose.onNodeWithText("当前源未提供可选字幕").assertIsDisplayed()
        back()
        open("切换频道")
        compose.runOnIdle { assertTrue(channelsOpened) }
    }

    @Test fun guideScrollsWithoutHidingClose() {
        val now = System.currentTimeMillis()
        showMenu(programmes = (0..29).map {
            Programme("news", Date(now + it * 60_000 - 30_000), Date(now + (it + 1) * 60_000), "节目 $it")
        })
        open("节目单")
        compose.onNodeWithText("正在播").assertIsDisplayed()
        compose.onNodeWithTag("playback-options-list").performScrollToNode(hasText("节目 29"))
        compose.onNodeWithText("节目 29").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        screenshot("guide")
    }

    @Test fun remoteEnterSelectsFocusedOption() {
        showMenu()
        open("画面比例")
        compose.runOnIdle { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
        compose.onNodeWithTag("playback-options-list").performScrollToNode(hasText("拉伸铺满"))
        compose.onNodeWithText("拉伸铺满").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithText("拉伸铺满").assertIsFocused()
        compose.onNodeWithText("拉伸铺满").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(PictureMode.FILL, picture) }
    }

    @Test fun realPlayerDiscoversAndSelectsAudioTrack() {
        val file = File(compose.activity.cacheDir, "track-test.wav")
        val samples = 16_000
        val bytes = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samples).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples).array()
        file.writeBytes(bytes)
        lateinit var player: ExoPlayer
        compose.runOnIdle {
            player = ExoPlayer.Builder(compose.activity).build()
            player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            player.prepare()
        }
        try {
            compose.waitUntil(10_000) {
                var ready = false
                compose.runOnIdle { ready = player.playbackState == Player.STATE_READY }
                ready
            }
            compose.runOnIdle {
                val choice = trackChoices(player, C.TRACK_TYPE_AUDIO).first { ':' in it.id }
                assertTrue(choice.enabled)
                selectPlaybackTrack(player, C.TRACK_TYPE_AUDIO, choice.id)
                assertFalse(player.trackSelectionParameters.overrides.isEmpty())
                selectPlaybackTrack(player, C.TRACK_TYPE_AUDIO, "auto")
                assertTrue(player.trackSelectionParameters.overrides.isEmpty())
                selectPlaybackTrack(player, C.TRACK_TYPE_TEXT, "off")
                assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
                selectPlaybackTrack(player, C.TRACK_TYPE_TEXT, "auto")
                assertFalse(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
                selectPlaybackTrack(player, C.TRACK_TYPE_AUDIO, "999:999")
                assertTrue(player.trackSelectionParameters.overrides.isEmpty())
            }
        } finally {
            compose.runOnIdle { player.release() }
            file.delete()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "playback-$name-${if (landscape) "landscape" else "portrait"}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
