package compose.project.click.click.data.contacts // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactDiscoveryHelperTest {
    @Test
    fun normalizeEmail_lowercasesAndTrims() {
        assertEquals("foo@bar.com", ContactDiscoveryHelper.normalizeEmail("  Foo@Bar.COM "))
        assertNull(ContactDiscoveryHelper.normalizeEmail("not-an-email"))
    }

    @Test
    fun normalizePhone_usTenDigitToE164() {
        assertEquals("+12065550100", ContactDiscoveryHelper.normalizePhoneE164("(206) 555-0100"))
        assertEquals("+12065550100", ContactDiscoveryHelper.normalizePhoneE164("+1 206 555 0100"))
        assertNull(ContactDiscoveryHelper.normalizePhoneE164("123"))
    }

    @Test
    fun hashesFromRaw_areSha256HexAndDeduped() {
        val hashes =
            ContactDiscoveryHelper.hashesFromRaw(
                emails = listOf("foo@bar.com", "FOO@bar.com"),
                phones = listOf("(206) 555-0100", "+12065550100"),
            )
        assertEquals(2, hashes.size)
        assertTrue(hashes.all { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } })
        assertEquals(
            ContactDiscoveryHelper.hashUtf8("foo@bar.com"),
            hashes[0],
        )
    }
}
