package compose.project.click.click.ui.components.cardstack // pragma: allowlist secret

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.ui.geometry.Offset

/**
 * Vendored from Hukumister LazyCardStack. Serializes drag mutations so 1:1 finger tracking
 * cannot race an in-flight spring.
 */
interface SwiperDraggableState {
    suspend fun drag(
        dragPriority: MutatePriority = MutatePriority.Default,
        block: suspend SwiperDragScope.() -> Unit,
    )
}

interface SwiperDragScope {
    fun dragBy(offset: Offset)
}

fun SwiperDraggableState(onDelta: (Offset) -> Unit): SwiperDraggableState = DefaultSwiperDraggableState(onDelta)

private class DefaultSwiperDraggableState(
    val onDelta: (Offset) -> Unit,
) : SwiperDraggableState {
    private val dragScope: SwiperDragScope =
        object : SwiperDragScope {
            override fun dragBy(offset: Offset) {
                onDelta(offset)
            }
        }

    private val scrollMutex = MutatorMutex()

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend SwiperDragScope.() -> Unit,
    ) {
        scrollMutex.mutateWith(dragScope, dragPriority, block)
    }
}
