package com.example.iptvplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import coil.compose.AsyncImage

/** M3U 台标。真实图片置于缩写占位之上，网络失败时占位会自然露出。 */
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
    val background = if (selected) Color.White.copy(alpha = 0.16f)
    else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (selected) Color.White
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = channelLogoFallback(name),
            color = foreground,
            fontSize = if (size >= 52.dp) 14.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "${name}台标",
                contentScale = ContentScale.Fit,
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
    active: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val emphasized = focused || active
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(enabled = enabled, onClick = onClick)
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = if (showLabel) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(21.dp))
        if (showLabel) {
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = contentColor,
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
    onBack: (() -> Unit)? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            ToolbarAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "返回",
                onClick = onBack,
                showLabel = false
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
