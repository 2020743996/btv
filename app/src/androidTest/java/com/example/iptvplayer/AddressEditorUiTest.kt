package com.example.iptvplayer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Before
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class AddressEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun configureOrientation() {
        val landscape = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun focusedButtonRespondsToRemoteEnter() {
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            IptvPlayerTheme { AddressEditor(emptyList(), {}, {}) }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("添加").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("添加").assertIsFocused()
        compose.onNodeWithText("添加").performKeyInput { pressKey(Key.Enter) }
        captureScreen("remote-enter")
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun touchAddThenSave_deliversTheEnteredAddress() {
        var saved: List<String>? = null
        compose.setContent { IptvPlayerTheme { AddressEditor(emptyList(), { saved = it }, {}) } }
        compose.onNodeWithText("添加").performTouchInput { click() }
        compose.onNode(hasSetTextAction()).performTextInput("https://example.com/live.m3u")
        captureScreen("address-form")
        compose.onNodeWithText("添加").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("1 个源 · 未保存").assertIsDisplayed()
        compose.onNodeWithText("保存").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("https://example.com/live.m3u"), saved) }
    }

    @Test
    fun visibleKeyboardAndLargeFont_keepSubmitUsable() {
        lateinit var view: View
        var keyboard: SoftwareKeyboardController? = null
        compose.setContent {
            view = LocalView.current
            keyboard = LocalSoftwareKeyboardController.current
            IptvPlayerTheme(fontScale = 1.5f) { AddressEditor(emptyList(), {}, {}) }
        }
        compose.onNodeWithText("添加").performTouchInput { click() }
        compose.onNode(hasSetTextAction()).performTouchInput { click() }
        compose.runOnIdle { keyboard?.show() }
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNode(hasSetTextAction()).performTextInput("https://example.com/large.m3u")
        compose.onNode(hasSetTextAction()).assertTextContains("https://example.com/large.m3u")
        captureScreen("keyboard-large-font")
        compose.onNodeWithText("添加").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("1 个源 · 未保存").assertIsDisplayed()
    }

    @Test
    fun invalidInput_showsErrorAndCanBeCorrectedWithImeDone() {
        compose.setContent { IptvPlayerTheme { AddressEditor(emptyList(), {}, {}) } }
        compose.onNodeWithText("添加").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("https://?")
        compose.onNodeWithText("添加").performClick()
        compose.onNodeWithText("请输入完整的 HTTP/HTTPS 地址，地址中不能包含空格").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("HTTP://example.com/list.m3u")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("http://example.com/list.m3u").assertIsDisplayed()
    }

    @Test
    fun recreation_preservesDraftAndUnsavedList() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { IptvPlayerTheme { AddressEditor(emptyList(), {}, {}) } }
        compose.onNodeWithText("添加").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("https://example.com/list.m3u")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains("https://example.com/list.m3u")
        compose.onNodeWithText("添加").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1 个源 · 未保存").assertIsDisplayed()
        compose.onNodeWithText("https://example.com/list.m3u").assertIsDisplayed()
    }

    @Test
    fun cancelDirtyDraft_requiresConfirmation() {
        compose.setContent { IptvPlayerTheme { AddressEditor(emptyList(), {}, {}) } }
        compose.onNodeWithText("添加").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("https://example.com/list.m3u")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("https://example.com/list.m3u")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText("暂无频道源").assertIsDisplayed()
    }

    private fun captureScreen(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Wait for the rendered frame and IME animation, not just Compose state.
        Thread.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
