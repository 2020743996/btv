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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ActionRole { DESTRUCTIVE, SECONDARY, PRIMARY }

data class UiAction(
    val label: String,
    val icon: ImageVector? = null,
    val accentColor: Color = UiColors.Live,
    val enabled: Boolean = true,
    val role: ActionRole = ActionRole.SECONDARY,
    val onClick: () -> Unit
)

internal fun orderActions(actions: List<UiAction>): List<UiAction> =
    actions.sortedBy { it.role.ordinal }

internal fun hasValidActionHierarchy(actions: List<UiAction>): Boolean =
    actions.count { it.role == ActionRole.PRIMARY } <= 1

/** 通用操作按钮。语义位置由 ActionBar 或 FormActions 统一管理。 */
@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = UiColors.Live,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val backgroundColor: Brush = when {
        !enabled -> SolidColor(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
        highlighted -> SolidColor(accentColor)
        focused -> SolidColor(accentColor.copy(alpha = 0.14f))
        else -> SolidColor(Color.White.copy(alpha = 0.84f))
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .graphicsLayer {
                scaleX = if (focused) 1.03f else 1f
                scaleY = if (focused) 1.03f else 1f
            }
            .shadow(
                elevation = if (focused) 8.dp else 3.dp,
                shape = MaterialTheme.shapes.large,
                ambientColor = accentColor.copy(alpha = 0.16f),
                spotColor = accentColor.copy(alpha = 0.14f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accentColor else Color.White,
                shape = MaterialTheme.shapes.large
            )
            .clickable(enabled = enabled, onClick = onClick)
            .background(backgroundColor, MaterialTheme.shapes.large)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (highlighted) Color.White
        else if (focused) accentColor
        else if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) Color.White else accentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(label, color = contentColor, fontSize = 16.sp)
        }
    }
}

/**
 * 页面和区域的统一操作栏。
 * 固定顺序为危险、次要、主要，确保唯一主操作始终位于最右侧或窄屏最下方。
 */
@Composable
fun ActionBar(
    actions: List<UiAction>,
    modifier: Modifier = Modifier,
    stackOnCompact: Boolean = false
) {
    require(hasValidActionHierarchy(actions)) {
        "Each action bar can contain at most one primary action"
    }
    val ordered = orderActions(actions)
    if (stackOnCompact && rememberWindowType() == WindowType.COMPACT) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ordered.forEach { action ->
                ActionButton(
                    label = action.label,
                    modifier = Modifier.widthIn(min = 168.dp),
                    highlighted = action.role == ActionRole.PRIMARY,
                    enabled = action.enabled,
                    icon = action.icon,
                    accentColor = action.accentColor,
                    onClick = action.onClick
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ordered.forEach { action ->
                ActionButton(
                    label = action.label,
                    highlighted = action.role == ActionRole.PRIMARY,
                    enabled = action.enabled,
                    icon = action.icon,
                    accentColor = action.accentColor,
                    onClick = action.onClick
                )
            }
        }
    }
}

/** 表单底栏：次要操作在左，唯一主操作固定在最右。 */
@Composable
fun FormActions(
    primary: UiAction,
    modifier: Modifier = Modifier,
    secondary: UiAction? = null,
    destructive: UiAction? = null,
    stackOnCompact: Boolean = false
) {
    ActionBar(
        actions = listOfNotNull(
            destructive?.copy(role = ActionRole.DESTRUCTIVE),
            secondary?.copy(role = ActionRole.SECONDARY),
            primary.copy(role = ActionRole.PRIMARY)
        ),
        modifier = modifier,
        stackOnCompact = stackOnCompact
    )
}

/** 弹窗底栏与表单使用同一语义顺序，避免确认按钮在不同弹窗左右跳动。 */
@Composable
fun DialogFooter(
    primary: UiAction,
    modifier: Modifier = Modifier,
    secondary: UiAction? = null,
    destructive: UiAction? = null
) = FormActions(
    primary = primary,
    modifier = modifier,
    secondary = secondary,
    destructive = destructive,
    stackOnCompact = true
)

/** 列表或表格行操作：编辑在前，删除在后，并固定在行尾。 */
@Composable
fun TableActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconAction(UiIcons.Pencil, "编辑", UiColors.Edit, onEdit)
        CompactIconAction(UiIcons.Trash, "删除", UiColors.Delete, onDelete)
    }
}

@Composable
private fun CompactIconAction(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) accentColor else accentColor.copy(alpha = 0.16f),
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .background(accentColor.copy(alpha = if (focused) 0.14f else 0.07f), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(20.dp))
    }
}
