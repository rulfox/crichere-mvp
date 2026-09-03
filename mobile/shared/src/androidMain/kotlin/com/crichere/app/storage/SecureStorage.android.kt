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
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                .withMasterKeyUri("$ANDROID_KEYSTORE_URI_PREFIX$MASTER_KEY_ALIAS")
                .build()
                .keysetHandle
            keysetHandle.getPrimitive(Aead::class.java).also { aead = it }
        }
    }

    actual override suspend fun get(key: String): String? {
        val encoded = context.secureStorageDataStore.data.first()[stringPreferencesKey(key)]
            ?: return null
        val ciphertext = Base64.decode(encoded)
        val plaintext = aead().decrypt(ciphertext, key.encodeToByteArray())
        return plaintext.decodeToString()
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
    }
}
