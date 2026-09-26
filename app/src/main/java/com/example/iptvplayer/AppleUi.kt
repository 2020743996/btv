package com.example.iptvplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object AppleUi {
    val Control = RoundedCornerShape(12.dp)
    val CompactControl = RoundedCornerShape(9.dp)
    val Chip = RoundedCornerShape(50)
    val Panel = RoundedCornerShape(20.dp)
    val Field = RoundedCornerShape(14.dp)
    val Separator = Color(0xFFE7E8EC)
    val Secondary = Color(0xFF626873)
    val Chrome = Color(0xFFF5F6F8)
    val SubtleBlue = Color(0xFFEAF2FF)
}

internal object UiSpace {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
    val PageCompact = 16.dp
    val PageRegular = 24.dp
}

/** Lightweight glass-like chrome; no video readback or full-screen blur. */
internal fun Modifier.glassSurface(
    shape: Shape = AppleUi.Control,
    focused: Boolean = false,
    tinted: Boolean = false,
    transparency: Float = 0.18f
): Modifier {
    val clearAmount = transparency.coerceIn(0f, 0.4f)
    val baseAlpha = 1f - clearAmount
    return shadow(if (focused) 4.dp else 1.dp, shape, clip = false,
    ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.08f))
    .background(Brush.verticalGradient(listOf(
        Color.White.copy(alpha = baseAlpha),
        (if (tinted) Color(0xFFE8F1FF) else AppleUi.Chrome).copy(alpha = baseAlpha)
    )), shape)
    .drawBehind {
        val bandHeight = size.height * 0.22f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.28f * baseAlpha), Color.Transparent),
                startY = 0f,
                endY = bandHeight
            ),
            size = androidx.compose.ui.geometry.Size(size.width, bandHeight)
        )
    }
    .border(if (focused) 2.dp else 1.dp,
        if (focused) UiColors.Info else AppleUi.Separator.copy(alpha = 0.9f), shape)
    .clip(shape)
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = AppleUi.Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
}

@Composable
internal fun ListSeparator(inset: androidx.compose.ui.unit.Dp = 0.dp) {
    HorizontalDivider(Modifier.padding(start = inset), color = AppleUi.Separator, thickness = 0.5.dp)
}

@Composable
internal fun NavigationRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(AppleUi.Control)
            .background(if (focused) UiColors.Info.copy(alpha = 0.08f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp,
                if (focused) UiColors.Info else Color.Transparent, AppleUi.Control)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(UiColors.Info.copy(alpha = 0.08f), AppleUi.Control),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = UiColors.Info)
        }
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(UiIcons.ChevronRight, null, Modifier.size(18.dp), tint = AppleUi.Secondary)
    }
}
