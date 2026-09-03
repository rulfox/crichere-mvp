package com.crichere.app.storage

/**
 * Minimal encrypted key-value store, shared across platforms via `expect`/`actual` -- this is the
 * one genuine case Task 5's Koin/Ktor/etc. components have where the platform primitives
 * (Android Keystore-backed crypto vs. iOS Keychain) can't be abstracted in `commonMain` itself.
 *
 * Not wired into anything yet in this task (no tokens exist to store until Task 6 builds real
 * auth) -- this exists purely to prove the `expect`/`actual` pattern compiles with a real
 * implementation on both sides:
 *  - `androidMain`: Preferences DataStore, values encrypted with Tink AEAD using an
 *    Android-Keystore-derived keyset (not `EncryptedSharedPreferences` -- locked architecture
 *    decision in ARCHITECTURE.md).
 *  - `iosMain`: native Keychain Services (`SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete`).
 */
expect class SecureStorage {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
}
