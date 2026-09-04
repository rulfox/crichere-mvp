package com.crichere.app.auth

import com.crichere.app.storage.SecureStore

/** In-memory [SecureStore] test double -- `commonTest` has no real DataStore+Tink/Keychain to hit. */
class FakeSecureStorage(initial: Map<String, String> = emptyMap()) : SecureStore {

    private val values = initial.toMutableMap()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun set(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    fun snapshot(): Map<String, String> = values.toMap()
}
