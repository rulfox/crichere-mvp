package com.crichere.app.di

import com.crichere.app.auth.FirebasePhoneAuthClient
import com.crichere.app.storage.SecureStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { SecureStorage() }
    single { FirebasePhoneAuthClient() }
}
