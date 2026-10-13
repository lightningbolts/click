package compose.project.click.click.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Sheet 10 §A6: click-web treats " on " and "Web browser" as a browser label and skips the approval
 * request for browsers, so Android's `device_label` must never look like one.
 */
class DeviceLabelBrowserTest {
    private fun assertNotABrowser(label: String) {
        assertFalse(label.contains(" on "), "\"$label\" contains \" on \"")
        assertNotEquals("web browser", label.lowercase(), "\"$label\" is \"Web browser\"")
        assertFalse(label.isBlank())
    }

    @Test
    fun everyMappedLabelIsAnAppLabel() {
        SAMSUNG_FAMILIES.forEach { (code, name) ->
            assertNotABrowser(name)
            assertNotABrowser(friendlyDeviceLabel("samsung", "${code}U"))
        }
        BRAND_NAMES.keys.forEach { brand ->
            assertNotABrowser(friendlyDeviceLabel(brand, "Model 1"))
            assertNotABrowser(friendlyDeviceLabel(brand, ""))
            assertNotABrowser(friendlyDeviceLabel(brand, null))
        }
        assertNotABrowser(friendlyDeviceLabel(null, null))
        assertNotABrowser(friendlyDeviceLabel("Google", "Pixel 9"))
    }

    @Test
    fun oddManufacturerStringsNeverPassThroughAsBrowsers() {
        assertEquals("Android phone", friendlyDeviceLabel(null, "Web browser"))
        assertEquals("Android phone", friendlyDeviceLabel("Web", "browser"))
        assertEquals("Acme Chrome Mac", friendlyDeviceLabel("Acme", "Chrome on Mac"))
        assertNotABrowser(friendlyDeviceLabel("Acme on Tour", "X1"))
        assertNotABrowser(friendlyDeviceLabel(null, " on  on on "))
    }
}
