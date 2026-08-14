package compose.project.click.click.data.contacts // pragma: allowlist secret

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNContact
import platform.Contacts.CNContactEmailAddressesKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.Foundation.NSString

@OptIn(ExperimentalForeignApi::class)
actual object ContactBook {
    actual suspend fun hashedContacts(): List<String> =
        withContext(Dispatchers.Default) {
            val store = CNContactStore()
            val status = CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)
            if (status != CNAuthorizationStatusAuthorized) {
                return@withContext emptyList()
            }
            val emails = ArrayList<String>()
            val phones = ArrayList<String>()
            val request =
                CNContactFetchRequest(
                    keysToFetch =
                        listOf(
                            CNContactEmailAddressesKey,
                            CNContactPhoneNumbersKey,
                        ),
                )
            runCatching {
                store.enumerateContactsWithFetchRequest(request, error = null) { contact, _ ->
                    collectContact(contact, emails, phones)
                    true
                }
            }
            ContactDiscoveryHelper.hashesFromRaw(emails, phones)
        }
}

private fun collectContact(
    contact: CNContact?,
    emails: MutableList<String>,
    phones: MutableList<String>,
) {
    if (contact == null) return
    val emailValues = contact.emailAddresses
    for (i in 0 until emailValues.size.toInt()) {
        val labeled = emailValues[i] as? CNLabeledValue ?: continue
        val raw = labeled.value
        val text = (raw as? NSString)?.toString() ?: raw?.toString()
        if (!text.isNullOrBlank()) emails.add(text)
    }
    val phoneValues = contact.phoneNumbers
    for (i in 0 until phoneValues.size.toInt()) {
        val labeled = phoneValues[i] as? CNLabeledValue ?: continue
        val text = (labeled.value as? CNPhoneNumber)?.stringValue
        if (!text.isNullOrBlank()) phones.add(text)
    }
}
