package com.crichere.app.notification

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crichere.app.CricherApplication
import com.crichere.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Receives Firebase Cloud Messaging pushes (docs/PHASE8.md). `by inject()` (Koin's Android
 * extension on any `ComponentCallbacks`, which `Service` already is) resolves
 * [DeviceTokenRepository] the same way any other Koin dependency would -- a `FirebaseMessagingService`
 * is instantiated by the OS, not by this app's own DI graph, so it can't take constructor params
 * the way every other repository consumer in this codebase does.
 *
 * Tapping a shown notification reuses the exact same `crichere://leagues/{id}` deep link Phase 3's
 * Share action already produces -- see `AndroidManifest.xml`'s existing intent-filter on
 * `MainActivity`, not a second mechanism invented for this.
 */
class CrichereFirebaseMessagingService : FirebaseMessagingService() {

    private val deviceTokenRepository: DeviceTokenRepository by inject()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Fire-and-forget, best-effort -- see docs/PHASE8.md. There's no request/response cycle
        // here to hang a coroutine scope off of, unlike a ViewModel's viewModelScope.
        CoroutineScope(Dispatchers.IO).launch {
            deviceTokenRepository.register(token, "ANDROID")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: return
        val body = message.notification?.body
        val leagueId = message.data["leagueId"]

        val pendingIntent = leagueId?.let {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("crichere://leagues/$it"))
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CricherApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
