package com.crichere.app.di

import com.crichere.app.auth.FirebasePhoneAuthClient
import com.crichere.app.auth.PhoneAuthClient
import com.crichere.app.location.DeviceLocationProvider
import com.crichere.app.location.LocationProvider
import com.crichere.app.storage.SecureStorage
import com.crichere.app.storage.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<SecureStore> { SecureStorage() }
    single<PhoneAuthClient> { FirebasePhoneAuthClient() }
    single<LocationProvider> { DeviceLocationProvider() }
}
