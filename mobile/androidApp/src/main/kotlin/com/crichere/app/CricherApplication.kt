package com.crichere.app

import android.app.Application
import com.crichere.app.auth.CurrentActivityTracker
import com.crichere.app.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class CricherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@CricherApplication)
        }
        // Real Firebase Phone Auth needs a foreground Activity for its reCAPTCHA fallback UI
        // (`PhoneAuthOptions.Builder.setActivity(...)`) -- see CurrentActivityTracker's doc.
        CurrentActivityTracker.register(this)
    }
}
