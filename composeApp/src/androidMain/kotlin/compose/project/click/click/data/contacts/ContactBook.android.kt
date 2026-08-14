package compose.project.click.click.data.contacts // pragma: allowlist secret

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object ContactBook {
    actual suspend fun hashedContacts(): List<String> =
        withContext(Dispatchers.IO) {
            val ctx =
                contactsAppContext
                    ?: return@withContext emptyList()
            if (
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext emptyList()
            }
            val emails = ArrayList<String>()
            val phones = ArrayList<String>()
            ctx.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    while (cursor.moveToNext()) {
                        if (idx >= 0) {
                            cursor.getString(idx)?.let { emails.add(it) }
                        }
                    }
                }
            ctx.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        if (idx >= 0) {
                            cursor.getString(idx)?.let { phones.add(it) }
                        }
                    }
                }
            ContactDiscoveryHelper.hashesFromRaw(emails, phones)
        }
}

private var contactsAppContext: Context? = null

fun initContactBook(context: Context) {
    contactsAppContext = context.applicationContext
}
