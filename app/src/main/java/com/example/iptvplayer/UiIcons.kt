package com.example.iptvplayer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 仅包含 btv 实际使用的轻量线性图标，路径风格遵循 Lucide 的 24px 圆角线条。 */
object UiIcons {
    val Search = lineIcon("Search", "M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16", "M21 21l-4.35-4.35")
    val Refresh = lineIcon(
        "Refresh",
        "M21 12a9 9 0 0 0-15.7-6L3 8",
        "M3 3v5h5",
        "M3 12a9 9 0 0 0 15.7 6L21 16",
        "M16 16h5v5"
    )
    val Gauge = lineIcon(
        "Gauge",
        "M20.4 19a9 9 0 1 0-16.8 0",
        "M12 12l4-4",
        "M12 3v2",
        "M5.6 5.6 7 7",
        "M19 7l-1.4 1.4"
    )
    val Sliders = lineIcon(
        "Sliders",
        "M4 21v-7",
        "M4 10V3",
        "M12 21v-9",
        "M12 8V3",
        "M20 21v-5",
        "M20 12V3",
        "M1 14h6",
        "M9 8h6",
        "M17 16h6"
    )
    val Heart = lineIcon(
        "Heart",
        "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78z"
    )
    val Play = lineIcon("Play", "M6 3l14 9-14 9z")
    val Menu = lineIcon("Menu", "M4 6h16", "M4 12h16", "M4 18h16")
    val Calendar = lineIcon("Calendar", "M8 2v4", "M16 2v4", "M3 10h18", "M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2z")
    val History = lineIcon("History", "M3 12a9 9 0 1 0 2.64-6.36L3 8", "M3 3v5h5", "M12 7v5l3 2")
    val List = lineIcon("List", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")
    val ArrowLeft = lineIcon("ArrowLeft", "M19 12H5", "M12 19l-7-7 7-7")
    val ChevronLeft = lineIcon("ChevronLeft", "M15 18l-6-6 6-6")
    val ChevronRight = lineIcon("ChevronRight", "M9 18l6-6-6-6")
    val Plus = lineIcon("Plus", "M12 5v14", "M5 12h14")
    val MoreHorizontal = lineIcon("MoreHorizontal", "M5 12h.01", "M12 12h.01", "M19 12h.01")
    val Trash = lineIcon(
        "Trash",
        "M3 6h18",
        "M8 6V4h8v2",
        "M19 6l-1 14H6L5 6",
        "M10 11v5",
        "M14 11v5"
    )
    val Check = lineIcon("Check", "M20 6 9 17l-5-5")
    val Download = lineIcon("Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3")
    val Upload = lineIcon("Upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8l-5-5-5 5", "M12 3v12")
    val X = lineIcon("X", "M18 6 6 18", "M6 6l12 12")
    val Pencil = lineIcon(
        "Pencil",
        "M12 20h9",
        "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"
    )
    val Info = lineIcon("Info", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20", "M12 16v-4", "M12 8h.01")
}

object UiColors {
    val Accent = Color(0xFF0062CC)
    val Live = Color(0xFFE04444)
    val Search = Accent
    val Refresh = Accent
    val Speed = Accent
    val Settings = Accent
    val Favorite = Live
    val Edit = Accent
    val Delete = Color(0xFFC93434)
    val Info = Accent
}

private fun lineIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { pathData ->
        builder.addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}
