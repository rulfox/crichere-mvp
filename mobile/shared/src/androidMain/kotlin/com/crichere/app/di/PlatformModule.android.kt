package com.crichere.app.di

import com.crichere.app.BuildConfig
import com.crichere.app.auth.DebugFakePhoneAuthClient
import com.crichere.app.auth.FirebasePhoneAuthClient
import com.crichere.app.auth.PhoneAuthClient
import com.crichere.app.location.DeviceLocationProvider
import com.crichere.app.location.LocationProvider
import com.crichere.app.storage.SecureStorage
import com.crichere.app.storage.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * `androidContext(application)` (called from `CricherApplication.onCreate()`) registers the
 * Android `Context` in Koin's container, so `get()` below resolves it without this module needing
 * to know how it got there.
 */
actual val platformModule: Module = module {
    single<SecureStore> { SecureStorage(get()) }
    // Debug builds substitute DebugFakePhoneAuthClient -- this environment has no real Firebase
    // project connected client-side (no google-services.json), so the real FirebasePhoneAuthClient
    // can't complete a verification round-trip here; see that class's doc and task-8-brief.md's
    // ruling. BuildConfig.DEBUG is false for a release build, so this never reaches a shipped APK.
    single<PhoneAuthClient> {
        if (BuildConfig.DEBUG) DebugFakePhoneAuthClient() else FirebasePhoneAuthClient()
    }
    single<LocationProvider> { DeviceLocationProvider(get()) }
}
