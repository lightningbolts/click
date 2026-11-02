package compose.project.click.click.events.tickets

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * The ticket picker's state: what's on sale, what you picked, and the checkout it starts. Mirrors iOS
 * `Click/Features/Events/Tickets/TicketPickerModel.swift`. Totals here are display only; the server prices the order.
 */

sealed interface TicketPickerPhase {
    data object Loading : TicketPickerPhase

    data object Ready : TicketPickerPhase

    data object Submitting : TicketPickerPhase

    data class Failed(
        val message: String,
    ) : TicketPickerPhase
}

data class TicketPickerState(
    val offerings: List<TicketOffering> = emptyList(),
    /** Quantity picked per ticket type id. */
    val selection: Map<String, Int> = emptyMap(),
    val phase: TicketPickerPhase = TicketPickerPhase.Loading,
    /** Why the selection changed under you (another buyer took the last tickets, a price moved). */
    val notice: String? = null,
) {
    fun quantity(id: String): Int = selection[id] ?: 0

    val count: Int get() = selection.values.sum()

    /** Minor units. */
    val total: Int get() = offerings.sumOf { it.unitAmount * quantity(it.id) }

    val isFree: Boolean get() = count > 0 && total == 0

    val currency: String get() = offerings.firstOrNull()?.currency ?: "usd"

    /** Free and paid tickets are both on offer, so the sheet says they're separate orders. */
    val mixesKinds: Boolean get() = offerings.any { it.isFree } && offerings.any { !it.isFree }

    val ctaTitle: String
        get() =
            when {
                count == 0 -> "Get tickets"
                isFree -> if (count == 1) "Claim free ticket" else "Claim free tickets"
                else -> "Checkout · ${TicketPrice.money(total, currency)}"
            }

    fun canIncrement(id: String): Boolean {
        val offering = offerings.firstOrNull { it.id == id } ?: return false
        return offering.availability == TicketAvailability.ON_SALE &&
            quantity(id) < offering.maxQuantity &&
            phase != TicketPickerPhase.Submitting
    }

    /** The order lines to submit, in offering order. */
    val lines: List<TicketLine> get() = offerings.mapNotNull { o -> quantity(o.id).takeIf { it > 0 }?.let { TicketLine(o.id, it) } }
}

/** Keeps only what's still on sale, capped at what you may take now. */
internal fun clampSelection(
    selection: Map<String, Int>,
    offerings: List<TicketOffering>,
): Map<String, Int> =
    selection
        .mapNotNull { (id, qty) ->
            val offering = offerings.firstOrNull { it.id == id }
            if (offering == null || offering.availability != TicketAvailability.ON_SALE) return@mapNotNull null
            minOf(qty, offering.maxQuantity).takeIf { it > 0 }?.let { id to it }
        }.toMap()

/** +1 of [id]. Free and paid tickets are separate orders (the server refuses a mix): picking one kind clears the other. */
internal fun TicketPickerState.incremented(id: String): TicketPickerState {
    if (!canIncrement(id)) return this
    val offering = offerings.first { it.id == id }
    val kept = selection.filterKeys { key -> offerings.firstOrNull { it.id == key }?.isFree == offering.isFree }
    return copy(selection = kept + (id to quantity(id) + 1), notice = null)
}

internal fun TicketPickerState.decremented(id: String): TicketPickerState {
    val qty = quantity(id)
    if (qty <= 0 || phase == TicketPickerPhase.Submitting) return this
    return copy(selection = if (qty > 1) selection + (id to qty - 1) else selection - id)
}

class TicketPickerModel(
    val beaconId: String,
    private val client: TicketingClient,
) {
    private val _state = MutableStateFlow(TicketPickerState())
    val state: StateFlow<TicketPickerState> = _state.asStateFlow()

    suspend fun load() {
        if (_state.value.offerings.isEmpty()) _state.update { it.copy(phase = TicketPickerPhase.Loading) }
        try {
            refresh()
            _state.update { it.copy(phase = TicketPickerPhase.Ready) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(phase = TicketPickerPhase.Failed(e.userText())) }
        }
    }

    private suspend fun refresh() {
        val offerings = client.offerings(beaconId)
        _state.update { it.copy(offerings = offerings, selection = clampSelection(it.selection, offerings)) }
    }

    fun increment(id: String) = _state.update { it.incremented(id) }

    fun decrement(id: String) = _state.update { it.decremented(id) }

    /** Back from Stripe without an order: show what's on sale now. The selection stays for another try. */
    suspend fun refreshAfterCheckout() {
        runCatching { refresh() }
        _state.update { it.copy(phase = TicketPickerPhase.Ready) }
    }

    /** Checkout couldn't be shown; the selection stays for another try. */
    fun report(message: String) = _state.update { it.copy(phase = TicketPickerPhase.Failed(message)) }

    /**
     * Starts the order. On a refusal the picker refreshes, clamps and says why; a failure to reach Click keeps the
     * selection for another try. Ignored while one is in flight.
     */
    suspend fun submit(): CheckoutStart? {
        val current = _state.value
        if (current.phase == TicketPickerPhase.Submitting || current.count == 0) return null
        _state.update { it.copy(phase = TicketPickerPhase.Submitting, notice = null) }
        return try {
            val start = client.startCheckout(beaconId, current.lines)
            _state.update { it.copy(phase = TicketPickerPhase.Ready) }
            start
        } catch (e: CancellationException) {
            _state.update { it.copy(phase = TicketPickerPhase.Ready) }
            throw e
        } catch (e: TicketingException) {
            if (e.isRefusal) {
                _state.update { it.copy(notice = e.userMessage) }
                runCatching { refresh() }
                _state.update { it.copy(phase = TicketPickerPhase.Ready) }
            } else {
                _state.update { it.copy(phase = TicketPickerPhase.Failed(e.userMessage)) }
            }
            null
        } catch (e: Exception) {
            _state.update { it.copy(phase = TicketPickerPhase.Failed(e.userText())) }
            null
        }
    }
}

private fun Exception.userText(): String = (this as? TicketingException)?.userMessage ?: "Something went wrong. Try again."
