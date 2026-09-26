package com.example.iptvplayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

@Composable
fun standardTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = UiColors.Info,
    focusedLabelColor = UiColors.Info,
    focusedLeadingIconColor = UiColors.Info,
    cursorColor = UiColors.Info
)

/** M3U 台标。加载成功后隐藏缩写占位，避免透明台标透出底层文字。 */
@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    selected: Boolean = false
) = ChannelLogo(
    name = channel.name,
    logoUrl = channel.logoUrl,
    modifier = modifier,
    size = size,
    selected = selected
)

@Composable
fun ChannelLogo(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    selected: Boolean = false
) {
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var useDarkBackground by remember(logoUrl) { mutableStateOf(false) }
    val background = when {
        logoLoaded && useDarkBackground -> Color(0xFF626C76)
        selected && !logoLoaded -> Color(0xFF626C76)
        else -> Color.White
    }
    val foreground = if (selected) Color.White
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (!logoLoaded) {
            Text(
                text = channelLogoFallback(name),
                color = foreground,
                fontSize = if (size >= 52.dp) 14.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "${name}台标",
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    logoLoaded = true
                    useDarkBackground = needsDarkLogoBackground(state.result.drawable)
                },
                onError = {
                    logoLoaded = false
                    useDarkBackground = false
                },
                modifier = Modifier.size(size).padding(5.dp)
            )
        }
    }
}

internal fun channelLogoFallback(name: String): String {
    val compact = name.filter { it.isLetterOrDigit() }
    if (compact.isEmpty()) return "TV"
    val latinPrefix = compact.takeWhile { it.code <= 127 }
    if (latinPrefix.isNotEmpty()) return latinPrefix.take(3).uppercase()
    return compact.filter { it.code > 127 }.take(2).ifEmpty { "TV" }
}

private fun needsDarkLogoBackground(drawable: Drawable): Boolean = try {
    val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val previousBounds = Rect(drawable.bounds)
    try {
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        needsDarkLogoBackground(pixels)
    } finally {
        drawable.bounds = previousBounds
        bitmap.recycle()
    }
} catch (_: Exception) {
    false
}

internal fun needsDarkLogoBackground(pixels: IntArray): Boolean {
    var visiblePixels = 0
    var lightPixels = 0
    var luminanceTotal = 0.0
    for (pixel in pixels) {
        val alpha = pixel ushr 24 and 0xFF
        if (alpha < 48) continue
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        val luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0
        visiblePixels++
        luminanceTotal += luminance
        if (luminance >= 0.82) lightPixels++
    }
    if (visiblePixels == 0) return false
    val lightRatio = lightPixels.toDouble() / visiblePixels
    val averageLuminance = luminanceTotal / visiblePixels
    return lightRatio >= 0.55 || averageLuminance >= 0.82
}

/** 顶部工具栏操作。手机只显示熟悉图标，电视/平板显示图标和文字。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    enabled: Boolean = true,
    active: Boolean = false,
    accentColor: Color = UiColors.Info,
    grouped: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val emphasized = focused || active
    val contentColor = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
    Row(
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (!grouped) Modifier.glassSurface(
                focused = focused,
                tinted = active,
                transparency = getGlassTransparency(context) / 100f
            )
                else Modifier.clip(AppleUi.Control)
                    .background(if (emphasized) accentColor.copy(alpha = 0.1f) else Color.Transparent)
                    .border(if (focused) 2.dp else 0.dp,
                        if (focused) accentColor else Color.Transparent, AppleUi.Control))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(21.dp))
        if (showLabel) {
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
    }
}

@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    compactActions: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stackActions = actions != null && shouldStackHeaderActions(maxWidth) && !compactActions
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                if (onBack != null) {
                    ToolbarAction(
                        icon = UiIcons.ChevronLeft,
                        label = "返回",
                        onClick = onBack,
                        showLabel = false,
                        accentColor = UiColors.Info
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!stackActions && actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
            if (stackActions) {
                val stackedActions = requireNotNull(actions)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = stackedActions
                )
            }
        }
    }
}

internal fun shouldStackHeaderActions(width: Dp): Boolean = width < 720.dp
