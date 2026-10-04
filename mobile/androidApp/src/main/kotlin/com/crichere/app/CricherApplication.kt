package com.crichere.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.crichere.app.auth.CurrentActivityTracker
import com.crichere.app.di.initKoin
import com.crichere.app.network.configureBackendBaseUrl
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class CricherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureBackendBaseUrl(BuildConfig.BACKEND_BASE_URL)
        initKoin {
            androidLogger()
            androidContext(this@CricherApplication)
        }
        // Real Firebase Phone Auth needs a foreground Activity for its reCAPTCHA fallback UI
        // (`PhoneAuthOptions.Builder.setActivity(...)`) -- see CurrentActivityTracker's doc.
        CurrentActivityTracker.register(this)
        createNotificationChannel()
    }

    /** Push notifications (docs/PHASE8.md) -- required on Android 8+ (API 26) before any notification can be shown; see CrichereFirebaseMessagingService. */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "League updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Auction starts, sales, leave requests, and co-organizer changes for your leagues."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "league_updates"
    }
}
