package com.crichere.app.storage

/**
 * The contract [SecureStorage] fulfills, split out purely so `commonTest` can substitute an
 * in-memory fake for it (Task 6's `AuthRepository`/`AuthTokenProvider` tests need to assert real
 * tokens get persisted/read without touching a real DataStore+Tink/Keychain -- see
 * `AuthRepositoryTest`/`AuthTokenProviderTest`). `SecureStorage` itself stays the concrete
 * `expect`/`actual` class real code depends on; this interface exists only for testability.
 */
interface SecureStore {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
}
