package com.example.iptvplayer

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 系统栏安全 padding：仅手机/平板（非 TV）加状态栏+导航栏 inset 的 padding，
 * 避免内容被状态栏遮挡、顶部触摸被拦截。电视设备无系统栏，不加（防误偏移）。
 * imePadding：输入法弹出时内容上移避让，保证按钮不被键盘盖住。
 */
@Composable
fun Modifier.systemBarsPaddingCompat(): Modifier {
    val context = LocalContext.current
    if (isTvDevice(context)) return this
    return windowInsetsPadding(WindowInsets.systemBars).imePadding()
}

/**
 * 窗口尺寸类型：用于电视 / 平板 / 手机 自适应。
 * - COMPACT：手机竖屏（< 600dp），单栏布局、字号略小
 * - MEDIUM：小平板 / 手机横屏（600~840dp），双栏、中等间距
 * - EXPANDED：电视 / 大平板（> 840dp），双栏、大间距大字号
 */
enum class WindowType { COMPACT, MEDIUM, EXPANDED }

/** 根据当前屏幕宽度推断窗口类型。 */
@Composable
fun rememberWindowType(): WindowType {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < 600 -> WindowType.COMPACT
        configuration.screenWidthDp < 840 -> WindowType.MEDIUM
        else -> WindowType.EXPANDED
    }
}

/** 是否为 TV（Leanback）设备：决定默认交互方式（遥控器方向键）。 */
fun isTvDevice(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

/**
 * 设备是否有可用的系统输入法。
 * 电视上优先用系统键盘（Gboard for TV，支持语音和拼音），
 * 只有完全没有输入法的盒子（部分山寨盒子的精简固件）才回退到 App 自绘的屏上键盘。
 */
fun hasSystemIme(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
        as? android.view.inputmethod.InputMethodManager ?: return false
    return imm.enabledInputMethodList.isNotEmpty()
}

/** 品牌强调色，用于标题、高亮按钮和选中态。 */
val BrandGradient: Brush = SolidColor(UiColors.Info)

/** 柔和强调底色，用于小面积选中态和状态提示。 */
val SoftGradient: Brush = SolidColor(Color(0xFFF2F6FC))

/**
 * 白色主调 + 功能色配色：
 * - 背景与 Surface 保持纯白
 * - 单一蓝色承载普通操作，珊瑚红只用于直播与危险状态
 */
private val IptvColorScheme = lightColorScheme(
    primary = UiColors.Info,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F1FF),
    onPrimaryContainer = Color(0xFF00366F),
    secondary = UiColors.Accent,
    onSecondary = Color.White,
    tertiary = Color(0xFF8A6400),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF63636B),
    outline = Color(0xFFE5E5EA),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val IptvTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
)

private val IptvShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp)
)

/**
 * 整个软件的配色主题：纯白主调 + 克制的蓝红强调色。
 *
 * fontScale：全局字体缩放（老人模式的三级字体）。
 * 原理：Compose 里所有尺寸（sp/dp）最终都经过 Density 换算成像素，
 * 这里整体放大 Density，所有文字和按钮都按比例变大，
 * 不用逐个修改每个 Text 的 fontSize。
 * 标准 = 1.0 倍，大 = 1.25 倍，特大 = 1.5 倍。
 */
@Composable
fun IptvPlayerTheme(fontScale: Float = 1.0f, content: @Composable () -> Unit) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = IptvColorScheme,
            typography = IptvTypography,
            shapes = IptvShapes,
            content = content
        )
    }
}
