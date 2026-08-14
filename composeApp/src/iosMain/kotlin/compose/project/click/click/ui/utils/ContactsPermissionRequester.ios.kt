package compose.project.click.click.ui.utils // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import kotlin.coroutines.resume

@Composable
actual fun rememberPlatformContactsPermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    remember {
        { onComplete ->
            val status =
                CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)
            when (status) {
                CNAuthorizationStatusAuthorized -> onComplete()
                CNAuthorizationStatusDenied,
                CNAuthorizationStatusRestricted,
                -> {
                    openApplicationSystemSettings()
                    onComplete()
                }
                CNAuthorizationStatusNotDetermined -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        suspendCancellableCoroutine { cont ->
                            CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { _, _ ->
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                        onComplete()
                    }
                }
                else -> onComplete()
            }
        }
    }
