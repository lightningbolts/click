package compose.project.click.click.viewmodel

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android identity implementation calls android.util.Base64. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubE2eeV2SessionTest : HubE2eeV2SessionContract()
