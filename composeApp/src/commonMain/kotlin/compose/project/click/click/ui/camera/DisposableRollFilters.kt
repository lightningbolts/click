package compose.project.click.click.ui.camera

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

data class DisposableRollFilter(
    val name: String,
    val matrix: ColorMatrix,
)

object DisposableRollFilters {
    const val COUNT = 10

    private val natural = ColorMatrix()

    private val warm = ColorMatrix(
        floatArrayOf(
            1.12f, 0.06f, 0f, 0f, 8f,
            0.04f, 1.06f, 0f, 0f, 4f,
            0f, 0.02f, 0.92f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val cool = ColorMatrix(
        floatArrayOf(
            0.92f, 0f, 0.04f, 0f, 0f,
            0f, 0.98f, 0.06f, 0f, 2f,
            0.06f, 0.08f, 1.14f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val vintage = ColorMatrix(
        floatArrayOf(
            0.92f, 0.38f, 0.22f, 0f, 10f,
            0.32f, 0.82f, 0.18f, 0f, 6f,
            0.22f, 0.18f, 0.62f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val dramatic = ColorMatrix(
        floatArrayOf(
            1.28f, 0f, 0f, 0f, -18f,
            0f, 1.28f, 0f, 0f, -18f,
            0f, 0f, 1.28f, 0f, -18f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val fade = ColorMatrix(
        floatArrayOf(
            0.88f, 0.04f, 0.04f, 0f, 24f,
            0.04f, 0.88f, 0.04f, 0f, 24f,
            0.04f, 0.04f, 0.88f, 0f, 24f,
            0f, 0f, 0f, 0.92f, 0f,
        ),
    )

    private val noir = ColorMatrix(
        floatArrayOf(
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val vibrant = ColorMatrix(
        floatArrayOf(
            1.22f, -0.08f, -0.08f, 0f, 0f,
            -0.06f, 1.22f, -0.06f, 0f, 0f,
            -0.08f, -0.06f, 1.22f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val golden = ColorMatrix(
        floatArrayOf(
            1.18f, 0.12f, 0f, 0f, 12f,
            0.08f, 1.08f, 0f, 0f, 8f,
            0f, 0.04f, 0.86f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private val moody = ColorMatrix(
        floatArrayOf(
            0.82f, 0.02f, 0.12f, 0f, -6f,
            0.02f, 0.86f, 0.14f, 0f, -4f,
            0.14f, 0.12f, 1.08f, 0f, 2f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    val all: List<DisposableRollFilter> = listOf(
        DisposableRollFilter("Natural", natural),
        DisposableRollFilter("Warm", warm),
        DisposableRollFilter("Cool", cool),
        DisposableRollFilter("Vintage", vintage),
        DisposableRollFilter("Dramatic", dramatic),
        DisposableRollFilter("Fade", fade),
        DisposableRollFilter("Noir", noir),
        DisposableRollFilter("Vibrant", vibrant),
        DisposableRollFilter("Golden", golden),
        DisposableRollFilter("Moody", moody),
    )

    fun clampIndex(index: Int): Int = index.coerceIn(0, COUNT - 1)

    fun matrixFor(index: Int): ColorMatrix = all[clampIndex(index)].matrix

    fun colorFilterFor(index: Int): ColorFilter = ColorFilter.colorMatrix(matrixFor(index))

    fun nameFor(index: Int): String = all[clampIndex(index)].name
}
