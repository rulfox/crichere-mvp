package com.crichere.app.storage

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Robolectric, not `commonTest`, for [SecureStorage]'s Android `actual`: it needs a real
 * `Context` (for DataStore's file-backed storage) and the Android Keystore (for Tink's
 * `AndroidKeysetManager`), neither of which `commonTest`'s plain-JVM environment provides.
 * Robolectric's shadow Keystore/Context give a real-enough Android runtime to exercise the
 * actual DataStore+Tink round trip without needing a device/emulator for this specific class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureStorageTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `set then get returns the original plaintext`() = runTest {
        val storage = SecureStorage(context)

        storage.set("access_token", "super-secret-value")

        assertEquals("super-secret-value", storage.get("access_token"))
    }

    @Test
    fun `get returns null for a key that was never set`() = runTest {
        val storage = SecureStorage(context)

        assertNull(storage.get("never_set"))
    }

    @Test
    fun `remove deletes a previously set value`() = runTest {
        val storage = SecureStorage(context)
        storage.set("refresh_token", "some-refresh-token")

        storage.remove("refresh_token")

        assertNull(storage.get("refresh_token"))
    }

    @Test
    fun `set overwrites a previous value for the same key`() = runTest {
        val storage = SecureStorage(context)
        storage.set("phone", "first-value")

        storage.set("phone", "second-value")

        assertEquals("second-value", storage.get("phone"))
    }
}
