@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import qrcode.raw.ErrorCorrectionLevel
import qrcode.raw.QRCodeProcessor

/**
 * The QR's dark modules, row by row. Click Passes and flyers use level **M** (sheet 08 G3-15):
 * sparse enough to scan off a dim screen, unlike the builder's fixed level H.
 */
fun qrModules(
    data: String,
    level: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
): List<BooleanArray>? =
    runCatching {
        QRCodeProcessor(data, level).encode().map { row -> BooleanArray(row.size) { row[it].dark } }
    }.getOrNull()

/** Draws [modules] as crisp squares filling the canvas, with a [quietZone] in modules around them. */
fun DrawScope.drawQrModules(
    modules: List<BooleanArray>,
    color: Color,
    topLeft: Offset = Offset.Zero,
    side: Float = size.minDimension,
    quietZone: Int = 0,
) {
    val count = modules.size
    if (count == 0) return
    val cell = side / (count + quietZone * 2)
    val origin = Offset(topLeft.x + cell * quietZone, topLeft.y + cell * quietZone)
    // A hair of overlap so adjacent modules don't show seams when the cell size isn't whole.
    val cellSize = Size(cell + 0.5f, cell + 0.5f)
    modules.forEachIndexed { r, row ->
        row.forEachIndexed { c, dark ->
            if (dark) drawRect(color, Offset(origin.x + c * cell, origin.y + r * cell), cellSize)
        }
    }
}

/** A QR of [data] at error-correction level M, drawn on a canvas (no bitmap round trip). */
@Composable
fun QrMatrixCode(
    data: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    contentDescription: String? = null,
) {
    val modules = remember(data) { qrModules(data) }
    Canvas(
        modifier =
            modifier.semantics {
                contentDescription?.let { this.contentDescription = it }
            },
    ) {
        modules?.let { drawQrModules(it, color) }
    }
}
