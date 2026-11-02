@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.events.tickets.AfterCheckout
import compose.project.click.click.events.tickets.CheckoutLauncher
import compose.project.click.click.events.tickets.CheckoutStart
import compose.project.click.click.events.tickets.MyTicketsGroup
import compose.project.click.click.events.tickets.OrderConfirmationAction
import compose.project.click.click.events.tickets.OrderConfirmationState
import compose.project.click.click.events.tickets.OwnedTicket
import compose.project.click.click.events.tickets.TicketAvailability
import compose.project.click.click.events.tickets.TicketCheckoutReturns
import compose.project.click.click.events.tickets.TicketDisplay
import compose.project.click.click.events.tickets.TicketOffering
import compose.project.click.click.events.tickets.TicketPickerModel
import compose.project.click.click.events.tickets.TicketPickerPhase
import compose.project.click.click.events.tickets.TicketPrice
import compose.project.click.click.events.tickets.TicketScope
import compose.project.click.click.events.tickets.TicketingClient
import compose.project.click.click.events.tickets.TicketingException
import compose.project.click.click.events.tickets.isOpenableCheckoutUrl
import compose.project.click.click.events.tickets.orderConfirmationCopy
import compose.project.click.click.events.tickets.pollOrderConfirmation
import compose.project.click.click.events.tickets.resolveCheckoutReturn
import compose.project.click.click.events.tickets.sortTicketGroups
import compose.project.click.click.events.tickets.ticketButtonState
import compose.project.click.click.events.tickets.ticketDisplay
import compose.project.click.click.events.tickets.ticketGroupSummary
import compose.project.click.click.events.tickets.ticketOfferingDetail
import compose.project.click.click.platformForegroundTickFlow
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButtonVariant // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.formatWeekdayDate // pragma: allowlist secret
import compose.project.click.click.util.formatWeekdayDateTime // pragma: allowlist secret
import compose.project.click.click.utils.toImageBitmap // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import qrcode.QRCode

/** The event's primary action when it sells tickets: it replaces the RSVP button (iOS `TicketButton`). */
@Composable
internal fun EventTicketButton(
    ticketing: compose.project.click.click.events.tickets.EventTicketing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = ticketButtonState(ticketing)
    if (state.enabled) {
        ClickButton(onClick = onClick, modifier = modifier.fillMaxWidth(), variant = ClickButtonVariant.Primary) {
            Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(state.title, fontWeight = FontWeight.SemiBold)
        }
    } else {
        // A closed sale is a status, not a dead button: full-contrast text, no tap.
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private sealed interface ConfirmationRequest {
    data class Issued(
        val ticketCount: Int,
    ) : ConfirmationRequest

    data class Poll(
        val orderId: String,
    ) : ConfirmationRequest
}

/**
 * Choose tickets, claim them or pay on Stripe's page in a Custom Tab, then wait in place for Click to confirm the
 * order (iOS `TicketPickerSheet`). Prices and availability are the server's; the total is display only.
 */
@Composable
internal fun TicketPickerSheet(
    beaconId: String,
    client: TicketingClient,
    onConfirmed: () -> Unit,
    onSeeTickets: () -> Unit,
    onDismiss: () -> Unit,
    launcher: CheckoutLauncher = CheckoutLauncher.Default,
) {
    val model = remember(beaconId) { TicketPickerModel(beaconId, client) }
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
    // The order whose Stripe page is open, and the foreground tick when it opened.
    var awaitingOrderId by remember { mutableStateOf<String?>(null) }
    var launchTick by remember { mutableLongStateOf(0L) }
    val foregroundTick by platformForegroundTickFlow().collectAsState()
    val pendingReturn by TicketCheckoutReturns.pending.collectAsState()

    LaunchedEffect(beaconId) { model.load() }

    LaunchedEffect(awaitingOrderId, foregroundTick, pendingReturn) {
        val orderId = awaitingOrderId ?: return@LaunchedEffect
        var returned = TicketCheckoutReturns.consume(beaconId)
        if (returned == null && foregroundTick <= launchTick) return@LaunchedEffect
        if (returned == null) {
            // Back in the app: give an App Link that's still on its way a moment to land.
            delay(CHECKOUT_RETURN_GRACE_MS)
            returned = TicketCheckoutReturns.consume(beaconId)
        }
        awaitingOrderId = null
        scope.launch {
            when (val next = resolveCheckoutReturn(orderId, returned, client)) {
                is AfterCheckout.Confirm -> confirmation = ConfirmationRequest.Poll(next.orderId)
                AfterCheckout.BackToPicker -> model.refreshAfterCheckout()
            }
        }
    }

    fun checkout() {
        scope.launch {
            val count = state.count
            when (val start = model.submit() ?: return@launch) {
                is CheckoutStart.Fulfilled -> confirmation = ConfirmationRequest.Issued(count)
                is CheckoutStart.Checkout -> {
                    TicketCheckoutReturns.clear()
                    launchTick = foregroundTick
                    if (isOpenableCheckoutUrl(start.url) && launcher.open(start.url)) {
                        awaitingOrderId = start.orderId
                    } else {
                        runCatching { client.releaseOrder(start.orderId) }
                        model.report("Couldn’t open checkout. Try again.")
                    }
                }
            }
        }
    }

    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val current = confirmation) {
                null -> {
                    Text("Tickets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TicketPickerBody(
                        offerings = state.offerings,
                        phase = state.phase,
                        notice = state.notice,
                        mixesKinds = state.mixesKinds,
                        quantity = state::quantity,
                        canIncrement = state::canIncrement,
                        onIncrement = model::increment,
                        onDecrement = model::decrement,
                        onRetry = { scope.launch { model.load() } },
                    )
                    if (state.count > 0) {
                        TicketSummary(
                            lines =
                                state.offerings.filter { state.quantity(it.id) > 0 }.map { o ->
                                    "${state.quantity(o.id)} × ${o.name}" to
                                        TicketPrice.money(o.unitAmount * state.quantity(o.id), o.currency)
                                },
                            total = if (state.isFree) "Free" else TicketPrice.money(state.total, state.currency),
                        )
                    }
                    val submitting = state.phase == TicketPickerPhase.Submitting || awaitingOrderId != null
                    ClickButton(
                        onClick = { checkout() },
                        enabled = state.count > 0 && !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(state.ctaTitle, fontWeight = FontWeight.SemiBold)
                    }
                }
                is ConfirmationRequest.Issued ->
                    OrderConfirmationContent(
                        initial = OrderConfirmationState.Confirmed(current.ticketCount),
                        orderId = null,
                        client = client,
                        onConfirmed = onConfirmed,
                        onDone = onSeeTickets,
                        onRetry = { confirmation = null },
                    )
                is ConfirmationRequest.Poll ->
                    OrderConfirmationContent(
                        initial = OrderConfirmationState.Confirming,
                        orderId = current.orderId,
                        client = client,
                        onConfirmed = onConfirmed,
                        onDone = onSeeTickets,
                        onRetry = {
                            confirmation = null
                            scope.launch { model.refreshAfterCheckout() }
                        },
                    )
            }
        }
    }
}

private const val CHECKOUT_RETURN_GRACE_MS = 400L

@Composable
private fun TicketPickerBody(
    offerings: List<TicketOffering>,
    phase: TicketPickerPhase,
    notice: String?,
    mixesKinds: Boolean,
    quantity: (String) -> Int,
    canIncrement: (String) -> Boolean,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (offerings.isEmpty()) {
        when (phase) {
            is TicketPickerPhase.Failed -> {
                Text("Tickets didn’t load", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            TicketPickerPhase.Loading ->
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            else ->
                Text(
                    "No tickets are listed for this event.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
    ) {
        offerings.forEachIndexed { index, offering ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 14.dp))
            TicketOfferingRow(
                offering = offering,
                quantity = quantity(offering.id),
                canIncrement = canIncrement(offering.id),
                onIncrement = { onIncrement(offering.id) },
                onDecrement = { onDecrement(offering.id) },
            )
        }
    }
    if (mixesKinds) {
        Text(
            "Free and paid tickets are separate orders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val message = notice ?: (phase as? TicketPickerPhase.Failed)?.message
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun TicketOfferingRow(
    offering: TicketOffering,
    quantity: Int,
    canIncrement: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val onSale = offering.availability == TicketAvailability.ON_SALE
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                offering.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (onSale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ticketOfferingDetail(offering) { formatWeekdayDate(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            offering.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onSale) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(Icons.Filled.Remove, "Fewer ${offering.name}", enabled = quantity > 0, onClick = onDecrement)
                Text(
                    "$quantity",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 24.dp).semantics { contentDescription = "$quantity ${offering.name}" },
                )
                StepButton(Icons.Filled.Add, "More ${offering.name}", enabled = canIncrement, onClick = onIncrement)
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f), CircleShape),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun TicketSummary(
    lines: List<Pair<String, String>>,
    total: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEach { (label, value) -> SummaryLine(label, value) }
        SummaryLine("Fees", "None")
        HorizontalDivider()
        SummaryLine("Total", total, emphasized = true)
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** The picker's last step: waits for Click to confirm the order, then says how it ended (iOS `OrderConfirmationView`). */
@Composable
private fun OrderConfirmationContent(
    initial: OrderConfirmationState,
    orderId: String?,
    client: TicketingClient,
    onConfirmed: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    var state by remember(orderId, initial) { mutableStateOf(initial) }
    LaunchedEffect(orderId, initial) {
        if (orderId != null && state == OrderConfirmationState.Confirming) {
            state =
                pollOrderConfirmation(
                    orderId = orderId,
                    fetch = client::order,
                    sleep = { delay(it) },
                    nowMs = { Clock.System.now().toEpochMilliseconds() },
                )
        }
    }
    LaunchedEffect(state is OrderConfirmationState.Confirmed) {
        if (state is OrderConfirmationState.Confirmed) onConfirmed()
    }
    val copy = orderConfirmationCopy(state)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state == OrderConfirmationState.Confirming) {
            CircularProgressIndicator(modifier = Modifier.size(44.dp))
        } else {
            Icon(
                if (state is OrderConfirmationState.Confirmed) Icons.Filled.CheckCircle else Icons.Filled.ConfirmationNumber,
                contentDescription = null,
                tint =
                    if (state is OrderConfirmationState.Confirmed) {
                        Color(0xFF1FA855)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(44.dp),
            )
        }
        Text(copy.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            copy.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        copy.action?.let { action ->
            ClickButton(
                onClick = if (action is OrderConfirmationAction.Retry) onRetry else onDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(action.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The pass content for a ticketed event: one card per ticket with its QR, or why it no longer admits
 * (iOS `TicketPassContent`). Shown inside the Click Pass sheet.
 */
@Composable
internal fun TicketPassList(
    tickets: List<OwnedTicket>,
    eventCancelled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        tickets.forEachIndexed { index, ticket ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tickets.size > 1) {
                    Text(
                        "${index + 1} of ${tickets.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(ticket.tierName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                when (ticketDisplay(ticket, eventCancelled)) {
                    TicketDisplay.QR -> {
                        PassQrTile(ticket.credentialUrl.orEmpty(), "Ticket QR code, ${ticket.ticketNumber}")
                        ticket.code?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    TicketDisplay.CHECKED_IN ->
                        TicketStatusLine(
                            "You’re in",
                            ticket.checkedInAtMs?.let { "Checked in at ${formatClockTime(it)}." } ?: "This ticket was checked in.",
                        )
                    TicketDisplay.REFUNDED -> TicketStatusLine("Refunded", "This ticket was refunded.")
                    TicketDisplay.CANCELLED -> TicketStatusLine("Event cancelled", "Tickets for this event no longer admit anyone.")
                    TicketDisplay.VOID -> TicketStatusLine("No longer valid", "This ticket can’t be used.")
                }
                if (ticket.ticketNumber.isNotBlank()) {
                    Text(
                        "Ticket ${ticket.ticketNumber} · Order ${ticket.orderId.take(8).uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketStatusLine(
    title: String,
    detail: String,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Black on white in every theme, with a quiet zone, so door scanners can read it. Shared with the RSVP Click Pass. */
@Composable
internal fun PassQrTile(
    content: String,
    label: String,
) {
    val qr =
        remember(content) {
            runCatching {
                QRCode
                    .ofSquares()
                    .build(content)
                    .renderToBytes()
                    .toImageBitmap()
            }.getOrNull()
        }
    Box(
        modifier =
            Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (qr != null) Image(bitmap = qr, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

/**
 * Me → Tickets: every event you hold tickets for, upcoming or past (iOS `TicketsView`). A row opens that event's
 * passes in the Click Pass sheet.
 */
@Composable
internal fun MyTicketsPage(
    client: TicketingClient,
    onOpenPass: (MyTicketsGroup) -> Unit,
) {
    var scope by remember { mutableStateOf(TicketScope.UPCOMING) }
    val groups = remember { mutableStateMapOf<TicketScope, List<MyTicketsGroup>>() }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(scope, reload) {
        loadError = null
        try {
            groups[scope] = client.myTickets(scope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TicketingException) {
            // Ticketing is off for this account: nothing to show.
            if (e.isUnavailable) {
                groups[scope] = emptyList()
            } else if (groups[scope] == null) {
                loadError = e.userMessage
            }
        } catch (_: Exception) {
            if (groups[scope] == null) loadError = "Something went wrong. Try again."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TicketScope.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = scope == option,
                    onClick = { scope = option },
                    shape = SegmentedButtonDefaults.itemShape(index, TicketScope.entries.size),
                ) {
                    Text(if (option == TicketScope.UPCOMING) "Upcoming" else "Past")
                }
            }
        }
        val items = groups[scope]
        when {
            items != null && items.isEmpty() ->
                TicketsEmpty(
                    title = if (scope == TicketScope.UPCOMING) "No tickets yet" else "No past tickets",
                    detail =
                        if (scope == TicketScope.UPCOMING) {
                            "Events you get tickets for show up here."
                        } else {
                            "Tickets for events that have ended show up here."
                        },
                )
            items != null ->
                sortTicketGroups(items, scope).forEach { group ->
                    TicketGroupRow(group = group, onClick = { onOpenPass(group) })
                    HorizontalDivider()
                }
            loadError != null -> {
                TicketsEmpty(title = "Couldn’t load your tickets", detail = loadError.orEmpty())
                TextButton(onClick = { reload++ }, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
            }
            else ->
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
        }
    }
}

@Composable
private fun TicketsEmpty(
    title: String,
    detail: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TicketGroupRow(
    group: MyTicketsGroup,
    onClick: () -> Unit,
) {
    val event = group.event
    // In the event's own time zone, like the event page.
    val zone = event.timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
    val date = event.startAtMs?.let { formatWeekdayDateTime(it, zone) }
    val whenLine =
        when {
            date != null && event.locationName != null -> "$date · ${event.locationName}"
            else -> date ?: event.locationName
        }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            whenLine?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(
                if (event.cancelled) "Cancelled · ${ticketGroupSummary(group)}" else ticketGroupSummary(group),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
