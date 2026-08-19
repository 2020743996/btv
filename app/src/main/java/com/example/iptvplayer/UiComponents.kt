package com.example.iptvplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

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
    val background = if (selected) Color.White.copy(alpha = 0.16f)
    else Color.White
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
                onSuccess = { logoLoaded = true },
                onError = { logoLoaded = false },
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

/** 顶部工具栏操作。手机只显示熟悉图标，电视/平板显示图标和文字。 */
@Composable
fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    enabled: Boolean = true,
    active: Boolean = false,
    accentColor: Color = UiColors.Live
) {
    var focused by remember { mutableStateOf(false) }
    val emphasized = focused || active
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.55f)
        emphasized -> accentColor.copy(alpha = 0.13f)
        else -> Color.White.copy(alpha = 0.82f)
    }
    val contentColor = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .shadow(
                elevation = if (focused) 8.dp else 3.dp,
                shape = MaterialTheme.shapes.large,
                ambientColor = accentColor.copy(alpha = 0.18f),
                spotColor = accentColor.copy(alpha = 0.16f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accentColor else Color.White,
                shape = MaterialTheme.shapes.large
            )
            .clickable(enabled = enabled, onClick = onClick)
            .background(background, MaterialTheme.shapes.large)
            .padding(horizontal = if (showLabel) 12.dp else 10.dp),
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

@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stackActions = actions != null && shouldStackHeaderActions(maxWidth)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    ToolbarAction(
                        icon = UiIcons.ArrowLeft,
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
