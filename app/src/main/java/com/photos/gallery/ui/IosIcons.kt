package com.photos.gallery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun stroked(name: String, width: Float = 1.8f, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
        )
    }.build()

private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }.build()

private fun mixed(name: String, stroke: PathBuilder.() -> Unit, fill: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = stroke,
        )
        path(fill = SolidColor(Color.Black), pathBuilder = fill)
    }.build()

private fun PathBuilder.rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
    moveTo(l + rad, t)
    lineTo(r - rad, t)
    arcTo(rad, rad, 0f, false, true, r, t + rad)
    lineTo(r, b - rad)
    arcTo(rad, rad, 0f, false, true, r - rad, b)
    lineTo(l + rad, b)
    arcTo(rad, rad, 0f, false, true, l, b - rad)
    lineTo(l, t + rad)
    arcTo(rad, rad, 0f, false, true, l + rad, t)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

object IosIcons {
    val Library by lazy {
        stroked("library") {
            rrect(3f, 7f, 17.5f, 21f, 2.5f)
            moveTo(3.5f, 18f); lineTo(8f, 13.5f); lineTo(11f, 16.5f); lineTo(13f, 14.5f); lineTo(17f, 18.5f)
            circle(13f, 10.8f, 1.2f)
            moveTo(7f, 4f); lineTo(18.5f, 4f)
            arcTo(2.5f, 2.5f, 0f, false, true, 21f, 6.5f)
            lineTo(21f, 17f)
        }
    }
    val ForYou by lazy {
        mixed("foryou", stroke = { rrect(3f, 3f, 21f, 21f, 4.5f) }) {
            moveTo(12f, 17f)
            curveTo(6f, 13.2f, 7f, 7.5f, 9.6f, 7.5f)
            curveTo(10.8f, 7.5f, 11.6f, 8.2f, 12f, 9f)
            curveTo(12.4f, 8.2f, 13.2f, 7.5f, 14.4f, 7.5f)
            curveTo(17f, 7.5f, 18f, 13.2f, 12f, 17f)
            close()
        }
    }
    val Albums by lazy {
        stroked("albums") {
            rrect(3f, 8.5f, 21f, 21f, 2.5f)
            moveTo(5.5f, 5.5f); lineTo(18.5f, 5.5f)
            moveTo(8f, 2.8f); lineTo(16f, 2.8f)
        }
    }
    val Share by lazy {
        stroked("share") {
            moveTo(12f, 3f); lineTo(12f, 14.5f)
            moveTo(8.2f, 6.6f); lineTo(12f, 2.8f); lineTo(15.8f, 6.6f)
            moveTo(8.5f, 9.5f); lineTo(5.5f, 9.5f); lineTo(5.5f, 21f); lineTo(18.5f, 21f); lineTo(18.5f, 9.5f); lineTo(15.5f, 9.5f)
        }
    }
    val Trash by lazy {
        stroked("trash") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(9f, 6f); lineTo(9f, 3.8f); lineTo(15f, 3.8f); lineTo(15f, 6f)
            moveTo(6f, 6f); lineTo(7f, 20.5f); lineTo(17f, 20.5f); lineTo(18f, 6f)
            moveTo(10f, 10f); lineTo(10f, 17f)
            moveTo(14f, 10f); lineTo(14f, 17f)
        }
    }
    val Info by lazy {
        mixed("info", stroke = {
            circle(12f, 12f, 9f)
            moveTo(12f, 11f); lineTo(12f, 16.5f)
        }) { circle(12f, 7.7f, 1.1f) }
    }
    val Sliders by lazy {
        stroked("sliders") {
            moveTo(4f, 7f); lineTo(6.8f, 7f); moveTo(11.2f, 7f); lineTo(20f, 7f)
            circle(9f, 7f, 2.2f)
            moveTo(4f, 17f); lineTo(12.8f, 17f); moveTo(17.2f, 17f); lineTo(20f, 17f)
            circle(15f, 17f, 2.2f)
        }
    }
    val ChevronLeft by lazy { stroked("chevl", 2.3f) { moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f) } }
    val ChevronRight by lazy { stroked("chevr", 2.2f) { moveTo(9f, 4.5f); lineTo(16.5f, 12f); lineTo(9f, 19.5f) } }
    val Eye by lazy {
        stroked("eye") {
            moveTo(2.5f, 12f); quadTo(12f, 3f, 21.5f, 12f); quadTo(12f, 21f, 2.5f, 12f); close()
            circle(12f, 12f, 3f)
        }
    }
    val EyeSlash by lazy {
        stroked("eyeslash") {
            moveTo(2.5f, 12f); quadTo(12f, 3f, 21.5f, 12f); quadTo(12f, 21f, 2.5f, 12f); close()
            circle(12f, 12f, 3f)
            moveTo(4f, 4f); lineTo(20f, 20f)
        }
    }
    val Lock by lazy {
        mixed("lock", stroke = {
            moveTo(8.5f, 11f); lineTo(8.5f, 8f)
            arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f)
            lineTo(15.5f, 11f)
        }) { rrect(6f, 11f, 18f, 20.5f, 2f) }
    }
    val Video by lazy {
        stroked("video") {
            rrect(2.5f, 6.5f, 15.5f, 17.5f, 2.5f)
            moveTo(15.5f, 10.5f); lineTo(21f, 7.5f); lineTo(21f, 16.5f); lineTo(15.5f, 13.5f)
        }
    }
    val Selfie by lazy {
        stroked("selfie") {
            circle(12f, 9.5f, 3.5f)
            moveTo(5.5f, 20f); quadTo(12f, 12.5f, 18.5f, 20f)
            moveTo(3f, 7f); lineTo(3f, 3f); lineTo(7f, 3f)
            moveTo(17f, 3f); lineTo(21f, 3f); lineTo(21f, 7f)
        }
    }
    val Screenshot by lazy {
        stroked("screenshot") {
            rrect(6.5f, 2.5f, 17.5f, 21.5f, 2.5f)
            moveTo(9.2f, 9f); lineTo(9.2f, 6.5f); lineTo(11.5f, 6.5f)
            moveTo(14.8f, 15f); lineTo(14.8f, 17.5f); lineTo(12.5f, 17.5f)
        }
    }
    val Recording by lazy {
        mixed("recording", stroke = { circle(12f, 12f, 8.8f) }) { circle(12f, 12f, 4.2f) }
    }
    val Panorama by lazy {
        stroked("pano") {
            moveTo(2.5f, 6.5f); quadTo(12f, 4f, 21.5f, 6.5f); lineTo(21.5f, 17.5f)
            quadTo(12f, 15f, 2.5f, 17.5f); close()
        }
    }
    val Animated by lazy {
        stroked("animated") {
            rrect(3f, 9f, 15f, 21f, 2f)
            moveTo(6.5f, 6f); lineTo(16f, 6f); arcTo(2f, 2f, 0f, false, true, 18f, 8f); lineTo(18f, 17.5f)
            moveTo(10f, 3f); lineTo(19f, 3f); arcTo(2f, 2f, 0f, false, true, 21f, 5f); lineTo(21f, 14f)
        }
    }
    val Duplicate by lazy {
        stroked("dup") {
            rrect(3f, 7f, 16f, 21f, 2f)
            moveTo(7.5f, 3.5f); lineTo(18.5f, 3.5f); arcTo(2f, 2f, 0f, false, true, 20.5f, 5.5f); lineTo(20.5f, 16.5f)
        }
    }
    val Search by lazy {
        stroked("search", 2f) {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.3f, 15.3f); lineTo(20.5f, 20.5f)
        }
    }
    val Filter by lazy {
        stroked("filter", 2f) {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(7f, 12f); lineTo(17f, 12f)
            moveTo(10f, 17f); lineTo(14f, 17f)
        }
    }
    val Play by lazy { filled("play") { moveTo(7.5f, 4.5f); lineTo(19.5f, 12f); lineTo(7.5f, 19.5f); close() } }
    val Filters by lazy {
        stroked("filters", 1.6f) {
            circle(9f, 9.5f, 5f); circle(15f, 9.5f, 5f); circle(12f, 14.5f, 5f)
        }
    }
    val Crop by lazy {
        stroked("crop") {
            moveTo(6.5f, 2.5f); lineTo(6.5f, 17.5f); lineTo(21.5f, 17.5f)
            moveTo(2.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 21.5f)
        }
    }
    val Rotate by lazy {
        stroked("rotate") {
            rrect(4f, 10f, 15f, 21f, 2f)
            moveTo(10f, 3.5f); quadTo(19f, 3.5f, 19f, 11f)
            moveTo(16.5f, 8.8f); lineTo(19f, 11.3f); lineTo(21.5f, 8.8f)
        }
    }
    val Sun by lazy {
        stroked("sun") {
            circle(12f, 12f, 4f)
            moveTo(12f, 2.5f); lineTo(12f, 4.5f); moveTo(12f, 19.5f); lineTo(12f, 21.5f)
            moveTo(2.5f, 12f); lineTo(4.5f, 12f); moveTo(19.5f, 12f); lineTo(21.5f, 12f)
            moveTo(5.3f, 5.3f); lineTo(6.7f, 6.7f); moveTo(17.3f, 17.3f); lineTo(18.7f, 18.7f)
            moveTo(5.3f, 18.7f); lineTo(6.7f, 17.3f); moveTo(17.3f, 6.7f); lineTo(18.7f, 5.3f)
        }
    }
    val Contrast by lazy {
        mixed("contrast", stroke = { circle(12f, 12f, 8.5f) }) {
            moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, false, true, 12f, 20.5f); close()
        }
    }
    val Drop by lazy {
        stroked("drop") {
            moveTo(12f, 3f)
            curveTo(12f, 3f, 5.5f, 10f, 5.5f, 14.5f)
            arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 14.5f)
            curveTo(18.5f, 10f, 12f, 3f, 12f, 3f)
            close()
        }
    }
    val Thermo by lazy {
        stroked("thermo") {
            moveTo(10f, 14.5f); lineTo(10f, 5f); arcTo(2f, 2f, 0f, false, true, 14f, 5f); lineTo(14f, 14.5f)
            arcTo(3.6f, 3.6f, 0f, true, true, 10f, 14.5f)
        }
    }
    val Logo by lazy {
        filled("logo") {
            circle(16f, 7.5f, 2.3f)
            moveTo(2.5f, 19.5f); lineTo(8.8f, 10.5f); lineTo(12.6f, 15.6f); lineTo(15.4f, 12.3f); lineTo(21.5f, 19.5f); close()
        }
    }
    val Folder by lazy {
        stroked("folder") {
            moveTo(3f, 7f); lineTo(3f, 18f)
            arcTo(1.8f, 1.8f, 0f, false, false, 4.8f, 19.8f); lineTo(19.2f, 19.8f)
            arcTo(1.8f, 1.8f, 0f, false, false, 21f, 18f); lineTo(21f, 9.2f)
            arcTo(1.8f, 1.8f, 0f, false, false, 19.2f, 7.4f); lineTo(11.5f, 7.4f); lineTo(9.6f, 5f); lineTo(4.8f, 5f)
            arcTo(1.8f, 1.8f, 0f, false, false, 3f, 6.8f)
        }
    }
    val More by lazy {
        filled("more") { circle(5.5f, 12f, 1.7f); circle(12f, 12f, 1.7f); circle(18.5f, 12f, 1.7f) }
    }
    val Download by lazy {
        stroked("download") { circle(12f, 12f, 8.8f); moveTo(12f, 7.5f); lineTo(12f, 16f); moveTo(8.5f, 12.8f); lineTo(12f, 16.3f); lineTo(15.5f, 12.8f) }
    }
}
