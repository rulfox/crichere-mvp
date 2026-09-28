package com.crichere.app.notification

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Android [DeviceTokenProvider]: the real FCM registration token (see docs/PHASE8.md). */
class FcmDeviceTokenProvider : DeviceTokenProvider {

    override suspend fun currentToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
}
