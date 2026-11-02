package compose.project.click.click.events.tickets

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** One line of an order: a tier and how many. */
data class TicketLine(
    val tierId: String,
    val quantity: Int,
)

/**
 * What the ticket picker, order poll and wallet need, so they can be tested without the network
 * (iOS `TicketingClient` in `TicketingRepository.swift`). Every refusal is thrown as a [TicketingException].
 */
interface TicketingClient {
    suspend fun eventTicketing(beaconId: String): EventTicketing?

    suspend fun offerings(beaconId: String): List<TicketOffering>

    suspend fun startCheckout(
        beaconId: String,
        items: List<TicketLine>,
    ): CheckoutStart

    suspend fun order(orderId: String): TicketOrder

    /** The buyer left checkout: free the order's held tickets. A paid order refuses and stays. */
    suspend fun releaseOrder(orderId: String)

    suspend fun eventTickets(beaconId: String): List<OwnedTicket>

    suspend fun myTickets(scope: TicketScope): List<MyTicketsGroup>
}

/**
 * Body for `POST /api/beacons/{id}/tickets/checkout`: tier ids and quantities only, never prices.
 * `client` is left out: click-web's schema accepts only `web` / `ios` (`lib/api/schemas/ticketing.ts`) and would
 * reject `android`.
 */
internal fun checkoutRequestBody(items: List<TicketLine>): JsonObject =
    buildJsonObject {
        putJsonArray("items") {
            items.filter { it.quantity > 0 }.forEach { line ->
                addJsonObject {
                    put("ticket_tier_id", line.tierId)
                    put("quantity", line.quantity)
                }
            }
        }
    }

/** click-web backed [TicketingClient] (iOS `TicketingRepository`). */
class ApiTicketingClient(
    private val api: ApiClient = ApiClient(),
) : TicketingClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val origin get() = ApiClient.clickWebAuthOrigin

    override suspend fun eventTicketing(beaconId: String): EventTicketing? =
        parseBeaconTicketing(request { api.clickWebClient.get("$origin/api/beacons/$beaconId") })

    override suspend fun offerings(beaconId: String): List<TicketOffering> =
        parseTicketOfferings(request { api.clickWebClient.get("$origin/api/beacons/$beaconId/tickets/tiers") })

    override suspend fun startCheckout(
        beaconId: String,
        items: List<TicketLine>,
    ): CheckoutStart {
        val root =
            request {
                api.clickWebClient.post("$origin/api/beacons/$beaconId/tickets/checkout") {
                    contentType(ContentType.Application.Json)
                    setBody(checkoutRequestBody(items).toString())
                }
            }
        return parseCheckoutStart(root) ?: throw TicketingException(code = null)
    }

    override suspend fun order(orderId: String): TicketOrder =
        parseTicketOrder(request { api.clickWebClient.get("$origin/api/orders/$orderId") }) ?: throw TicketingException(code = null)

    override suspend fun releaseOrder(orderId: String) {
        request { api.clickWebClient.delete("$origin/api/orders/$orderId") }
    }

    override suspend fun eventTickets(beaconId: String): List<OwnedTicket> =
        parseOwnedTickets(request { api.clickWebClient.get("$origin/api/beacons/$beaconId/tickets") })

    override suspend fun myTickets(scope: TicketScope): List<MyTicketsGroup> =
        parseMyTicketsGroups(
            request {
                api.clickWebClient.get("$origin/api/me/tickets") { parameter("scope", scope.wire) }
            },
        )

    private suspend fun request(send: suspend () -> HttpResponse): JsonElement? {
        val response =
            try {
                send()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                throw TicketingException(code = "network")
            }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        val body = text?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
        if (response.status.value !in 200..299) throw TicketingException.fromResponse(response.status.value, body)
        return body
    }
}
