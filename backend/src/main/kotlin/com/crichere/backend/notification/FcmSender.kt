package com.crichere.backend.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Thin wrapper around [FirebaseMessaging.send] -- exists purely so [FcmSender] is unit-testable
 * without a real Firebase connection, same reasoning almost every other external collaborator in
 * this codebase is injected behind an interface for.
 */
interface FcmClient {
    fun send(message: Message): String
}

@Component
class FirebaseFcmClient(private val firebaseAppProvider: FirebaseAppProvider) : FcmClient {
    override fun send(message: Message): String =
        FirebaseMessaging.getInstance(firebaseAppProvider.app).send(message)
}

/**
 * Push notifications (docs/PHASE8.md). [sendToUser] is the only entry point every call site in
 * this codebase uses -- `@Async` and every failure swallowed here, deliberately: a push send must
 * never fail or slow down the real business transaction it's attached to (approving a leave
 * request must succeed even if FCM is unreachable). A missing/unconfigured Firebase credential
 * (the normal dev/CI state -- see [FirebaseAppProvider]) is just one more failure this already
 * catches, not a special case.
 */
@Component
class FcmSender(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val fcmClient: FcmClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendToUser(userId: UUID, title: String, body: String, data: Map<String, String> = emptyMap()) {
        val tokens =
            try {
                deviceTokenRepository.findByUserId(userId)
            } catch (e: Exception) {
                log.error("Couldn't look up device tokens for user {}", userId, e)
                return
            }
        tokens.forEach { sendToToken(it.token, title, body, data) }
    }

    private fun sendToToken(token: String, title: String, body: String, data: Map<String, String>) {
        val message =
            Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build()
        try {
            fcmClient.send(message)
        } catch (e: FirebaseMessagingException) {
            // UNREGISTERED: the app was uninstalled, or this token was superseded by a newer one
            // on the same device. INVALID_ARGUMENT: a malformed/corrupted token. Either way the
            // token is dead -- remove it so this doesn't retry forever.
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED || e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                deviceTokenRepository.deleteByToken(token)
                log.info("Removed a stale device token after a failed send ({})", e.messagingErrorCode)
            } else {
                log.warn("Push notification send failed: {}", e.messagingErrorCode)
            }
        } catch (e: Exception) {
            // Includes an unconfigured/missing Firebase credential (see FirebaseAppProvider) --
            // the normal state in dev/CI, not an error worth surfacing beyond this log line.
            log.warn("Push notification send failed", e)
        }
    }
}
