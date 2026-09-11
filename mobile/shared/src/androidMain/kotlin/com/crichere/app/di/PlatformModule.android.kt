package com.crichere.app.di

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
    single<PhoneAuthClient> { FirebasePhoneAuthClient() }
    single<LocationProvider> { DeviceLocationProvider(get()) }
}
