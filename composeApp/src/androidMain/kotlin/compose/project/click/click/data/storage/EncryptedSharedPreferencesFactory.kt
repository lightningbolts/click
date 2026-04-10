package compose.project.click.click.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.GeneralSecurityException

/**
 * Creates [EncryptedSharedPreferences], wiping the on-disk XML once if the Keystore
 * master key or ciphertext was invalidated (OS upgrade, backup restore mismatch).
 */
fun createEncryptedSharedPreferences(context: Context, name: String): SharedPreferences {
    fun build(): SharedPreferences {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    return try {
        build()
    } catch (e: GeneralSecurityException) {
        deleteSharedPreferencesBestEffort(context.applicationContext, name)
        build()
    } catch (e: SecurityException) {
        deleteSharedPreferencesBestEffort(context.applicationContext, name)
        build()
    }
}

private fun deleteSharedPreferencesBestEffort(context: Context, name: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(name)
        } else {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            File(dir, "$name.xml").delete()
        }
    } catch (_: Throwable) {
        // Best-effort cleanup; recreate may still succeed if only ciphertext was bad.
    }
}
