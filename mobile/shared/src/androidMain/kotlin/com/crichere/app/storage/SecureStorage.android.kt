package com.crichere.app.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.GeneralSecurityException
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val Context.secureStorageDataStore by preferencesDataStore(name = "crichere_secure_storage")

/**
 * Android `actual`: Preferences DataStore holding Tink-AEAD-encrypted values, keyed by an
 * Android-Keystore-derived keyset (per ARCHITECTURE.md's locked decision -- not
 * `EncryptedSharedPreferences`, which Google has been steering apps away from). The keyset
 * itself never leaves the device and is protected by hardware-backed Keystore material; DataStore
 * only ever sees ciphertext.
 */
@OptIn(ExperimentalEncodingApi::class)
actual class SecureStorage(private val context: Context) : SecureStore {

    private val initLock = Mutex()
    private var aead: Aead? = null

    private suspend fun aead(): Aead = initLock.withLock {
        aead ?: run {
            AeadConfig.register()
            val keysetHandle = try {
                buildKeysetHandle()
            } catch (e: Exception) {
                // The stored keyset can't be unwrapped: its Keystore master key is gone (uninstall,
                // or a backup/device-transfer restore that brought the prefs but not the key).
                // Nothing encrypted under it is recoverable, so start clean -- the user just signs
                // in again -- instead of failing every read/write forever.
                resetUnrecoverableState()
                buildKeysetHandle()
            }
            keysetHandle.getPrimitive(Aead::class.java).also { aead = it }
        }
    }

    private fun buildKeysetHandle() = AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
        .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
        .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
        .build()
        .keysetHandle

    private suspend fun resetUnrecoverableState() {
        context.deleteSharedPreferences(PREF_FILE_NAME)
        context.secureStorageDataStore.edit { it.clear() }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    actual override suspend fun get(key: String): String? {
        val encoded = context.secureStorageDataStore.data.first()[stringPreferencesKey(key)]
            ?: return null
        val aead = aead()
        return try {
            aead.decrypt(Base64.decode(encoded), key.encodeToByteArray()).decodeToString()
        } catch (e: GeneralSecurityException) {
            // Written under a keyset that no longer exists: unreadable, so treat it as absent.
            remove(key)
            null
        }
    }

    actual override suspend fun set(key: String, value: String) {
        val ciphertext = aead().encrypt(value.encodeToByteArray(), key.encodeToByteArray())
        val encoded = Base64.encode(ciphertext)
        context.secureStorageDataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = encoded
        }
    }

    actual override suspend fun remove(key: String) {
        context.secureStorageDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(key))
        }
    }

    private companion object {
        const val PREF_FILE_NAME = "crichere_secure_storage_keyset_prefs"
        const val KEYSET_NAME = "crichere_secure_storage_keyset"
        const val MASTER_KEY_ALIAS = "crichere_secure_storage_master_key"
        const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
