package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Featherweight custom Swiss geometric vector icons.
 * Eliminates the 6MB+ material-icons-extended dependency while delivering crisp 1.8dp stroke clarity.
 */
object PenIcons {

    val Scan: ImageVector by lazy {
        strokeIcon("PenScan") {
            // 4 corner scanner brackets + center scan line
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 9f); lineTo(4f, 5f); lineTo(9f, 5f)
                moveTo(15f, 5f); lineTo(20f, 5f); lineTo(20f, 9f)
                moveTo(20f, 15f); lineTo(20f, 19f); lineTo(15f, 19f)
                moveTo(9f, 19f); lineTo(4f, 19f); lineTo(4f, 15f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }

    val SparkleAi: ImageVector by lazy {
        strokeIcon("PenSparkleAi") {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(12f, 2.5f)
                lineTo(14.2f, 9.2f)
                lineTo(21f, 11.5f)
                lineTo(14.2f, 13.8f)
                lineTo(12f, 20.5f)
                lineTo(9.8f, 13.8f)
                lineTo(3f, 11.5f)
                lineTo(9.8f, 9.2f)
                close()
            }
        }
    }

    val VocabBook: ImageVector by lazy {
        strokeIcon("PenVocabBook") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5f, 4.5f); lineTo(11f, 4.5f); lineTo(12f, 6f); lineTo(13f, 4.5f); lineTo(19f, 4.5f)
                lineTo(19f, 18.5f); lineTo(13f, 18.5f); lineTo(12f, 20f); lineTo(11f, 18.5f); lineTo(5f, 18.5f); close()
                moveTo(12f, 6f); lineTo(12f, 19.5f)
            }
        }
    }

    val FocusTimer: ImageVector by lazy {
        strokeIcon("PenFocusTimer") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Circle approximated with 2 arcs + clock hands
                moveTo(12f, 4f)
                arcTo(8f, 8f, 0f, true, true, 11.99f, 4f)
                moveTo(12f, 8f); lineTo(12f, 12.2f); lineTo(15f, 14f)
            }
        }
    }

    val AppsGrid: ImageVector by lazy {
        strokeIcon("PenAppsGrid") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
        }
    }

    val ThemeContrast: ImageVector by lazy {
        strokeIcon("PenThemeContrast") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, false, true, 12f, 20.5f)
                close()
            }
        }
    }

    val McpPlug: ImageVector by lazy {
        strokeIcon("PenMcpPlug") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 3f); lineTo(9f, 8f)
                moveTo(15f, 3f); lineTo(15f, 8f)
                moveTo(6.5f, 8f); lineTo(17.5f, 8f); lineTo(17.5f, 12f)
                arcTo(5.5f, 5.5f, 0f, false, true, 6.5f, 12f)
                close()
                moveTo(12f, 17.5f); lineTo(12f, 21f)
            }
        }
    }

    val ShieldLock: ImageVector by lazy {
        strokeIcon("PenShieldLock") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 12f)
                curveTo(19f, 16.5f, 15.8f, 19.6f, 12f, 21f)
                curveTo(8.2f, 19.6f, 5f, 16.5f, 5f, 12f)
                lineTo(5f, 6f); close()
                moveTo(9.5f, 12f); lineTo(11.3f, 13.8f); lineTo(14.8f, 10.3f)
            }
        }
    }

    val Wallpaper: ImageVector by lazy {
        strokeIcon("PenWallpaper") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(4f, 15.5f); lineTo(9.5f, 11f); lineTo(13.5f, 14.5f); lineTo(16.5f, 12f); lineTo(20f, 15.5f)
            }
        }
    }

    private inline fun strokeIcon(name: String, builder: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(builder).build()
    }
}
