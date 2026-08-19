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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 公共的 TV 软键盘组件。
 * 电视上没有物理键盘，用屏幕上的字母表键盘输入文字：
 * 方向键移动焦点选字母，OK 键输入。
 * 设置页（输地址）和搜索页（输频道名）共用这套组件。
 */

/**
 * 键盘布局：6 行，每行 8 个键。字母 + 数字 + URL 常用符号。
 * （仅在没有系统输入法的电视盒子上作为回退使用）
 */
val KEY_ROWS = listOf(
    "ABCDEFGH",
    "IJKLMNOP",
    "QRSTUVWX",
    "YZ012345",
    "6789:/.-",
    "_?=&%+#"
)

/** 整块键盘：搜索页、地址输入页共用。手机窄屏按键宽度按容器自适应，避免溢出。 */
@Composable
fun TvKeyboard(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val compact = rememberWindowType() == WindowType.COMPACT
    if (compact) {
        BoxWithConstraints(modifier = modifier) {
            val spacing = 4.dp
            // 8 列键平分可用宽度（减去 7 个间距）。
            val keyWidth = (maxWidth - spacing * 7) / 8
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                for (row in KEY_ROWS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        for (char in row) {
                            KeyButton(
                                label = char.toString(),
                                onPress = { onKey(char.toString()) },
                                width = keyWidth,
                                height = 34.dp,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in KEY_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (char in row) {
                        KeyButton(label = char.toString(), onPress = { onKey(char.toString()) })
                    }
                }
            }
        }
    }
}

/** 单个字母/符号键。默认电视尺寸，手机可传入自适应宽高。 */
@Composable
fun KeyButton(
    label: String,
    onPress: () -> Unit,
    width: Dp = 64.dp,
    height: Dp = 48.dp,
    fontSize: TextUnit = 18.sp
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            }
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onPress)
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize,
            textAlign = TextAlign.Center
        )
    }
}

/** 操作按钮（添加 / 保存 / 删除 / 确定 等） */
@Composable
fun ActionButton(
    label: String,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val backgroundColor: Brush = when {
        !enabled -> SolidColor(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
        focused || highlighted -> BrandGradient
        else -> SolidColor(MaterialTheme.colorScheme.surfaceVariant)
    }
    Box(
        modifier = Modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .graphicsLayer {
                scaleX = if (focused) 1.03f else 1f
                scaleY = if (focused) 1.03f else 1f
            }
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(enabled = enabled, onClick = onClick)
            .background(backgroundColor, MaterialTheme.shapes.small)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (focused || highlighted) Color.White
        else if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            }
            Text(label, color = contentColor, fontSize = 16.sp)
        }
    }
}
