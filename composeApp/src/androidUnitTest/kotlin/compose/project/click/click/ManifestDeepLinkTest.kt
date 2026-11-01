package compose.project.click.click

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the merged manifest's deep-link routing:
 * - Every declared activity class exists. A missing one crashes the app when an intent picks it
 *   (this happened with `.instant.InstantProfileActivity`).
 * - Click links open [MainActivity] and only MainActivity.
 * - Web-only pages that share a prefix (`/careers`, `/events`, `/connections`) stay in the browser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestDeepLinkTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun handlersFor(url: String): List<String> {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(context.packageName)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.name }
    }

    @Test
    fun everyDeclaredActivityClassExists() {
        @Suppress("DEPRECATION")
        val activities =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
                .activities
                .orEmpty()
        assertTrue(activities.isNotEmpty())
        for (info in activities) {
            try {
                Class.forName(info.name)
            } catch (_: ClassNotFoundException) {
                fail("Manifest declares ${info.name}, but that class does not exist")
            }
        }
    }

    @Test
    fun clickLinksOpenOnlyMainActivity() {
        val main = MainActivity::class.java.name
        listOf(
            "https://joinclick.co/c/7b0e0c2e-0000-4000-8000-000000000001",
            "https://www.joinclick.co/c/7b0e0c2e-0000-4000-8000-000000000001",
            "https://joinclick.co/connect/7b0e0c2e-0000-4000-8000-000000000001",
            "https://click-us.vercel.app/connect/7b0e0c2e-0000-4000-8000-000000000001",
            "https://joinclick.co/e/beacon-123",
            "https://joinclick.co/hub/hub-1",
            "click://connect/7b0e0c2e-0000-4000-8000-000000000001",
            "click://e/beacon-123",
        ).forEach { url ->
            assertEquals(listOf(main), handlersFor(url), url)
        }
    }

    @Test
    fun webOnlyPagesWithSharedPrefixesStayInBrowser() {
        listOf(
            "https://joinclick.co/careers",
            "https://joinclick.co/careers/marketing-intern",
            "https://joinclick.co/connections",
            "https://joinclick.co/chat",
            "https://joinclick.co/events",
            "https://joinclick.co/explore",
        ).forEach { url ->
            assertEquals(emptyList(), handlersFor(url), url)
        }
    }
}
