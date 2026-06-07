package compose.project.click.click.ui.camera

data class DisposableRollFilter(
    val name: String,
)

object DisposableRollFilters {
    const val COUNT = 10

    val all: List<DisposableRollFilter> = listOf(
        DisposableRollFilter("Natural"),
        DisposableRollFilter("Warm"),
        DisposableRollFilter("Cool"),
        DisposableRollFilter("Vintage"),
        DisposableRollFilter("Dramatic"),
        DisposableRollFilter("Fade"),
        DisposableRollFilter("Noir"),
        DisposableRollFilter("Vibrant"),
        DisposableRollFilter("Golden"),
        DisposableRollFilter("Moody"),
    )

    fun clampIndex(index: Int): Int = index.coerceIn(0, COUNT - 1)

    fun nameFor(index: Int): String = all[clampIndex(index)].name
}
