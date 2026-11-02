package compose.project.click.click.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DropViewsBatchesTest {
    @Test
    fun idsAreTrimmedDedupedSortedAndCutIntoHundreds() {
        assertEquals(listOf(listOf("a", "b")), dropViewsBatches(listOf(" b", "a", "b", "")))
        val many = (0 until 250).map { "id-${it.toString().padStart(3, '0')}" }
        val batches = dropViewsBatches(many.reversed())
        assertEquals(listOf(100, 100, 50), batches.map { it.size })
        assertEquals("id-000", batches.first().first())
        assertEquals(emptyList(), dropViewsBatches(emptyList()))
    }

    @Test
    fun parsesTheViewsEnvelope() {
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString(DropViewsEnvelope.serializer(), """{"developed":{"a":"2026-10-01T12:00:00Z"}}""")
        assertEquals(mapOf("a" to "2026-10-01T12:00:00Z"), parsed.developed)
        assertEquals(emptyMap(), json.decodeFromString(DropViewsEnvelope.serializer(), "{}").developed)
    }
}
