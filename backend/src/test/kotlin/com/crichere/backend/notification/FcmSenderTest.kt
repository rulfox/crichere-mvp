package com.crichere.backend.notification

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Unit-level coverage of [FcmSender]. [FcmSender.sendToUser] is `@Async` in production, but Spring's
 * async proxying only kicks in through a real application context -- calling it directly here (no
 * Spring context in this test) runs it synchronously, which is exactly what a unit test wants:
 * deterministic assertions with no need to await a background thread.
 */
class FcmSenderTest {

    private val deviceTokenRepository = mockk<DeviceTokenRepository>()
    private val fcmClient = mockk<FcmClient>()
    private val sender = FcmSender(deviceTokenRepository, fcmClient)

    private val userId = UUID.randomUUID()

    @Test
    fun `sends to every device token registered for the user`() {
        every { deviceTokenRepository.findByUserId(userId) } returns listOf(
            DeviceTokenEntity(userId = userId, token = "token-a", platform = "ANDROID"),
            DeviceTokenEntity(userId = userId, token = "token-b", platform = "ANDROID"),
        )
        val sent = mutableListOf<Message>()
        every { fcmClient.send(capture(sent)) } returns "message-id"

        sender.sendToUser(userId, "Title", "Body", mapOf("leagueId" to "l1"))

        assertEquals(2, sent.size)
    }

    @Test
    fun `a stale token -- UNREGISTERED -- is removed after a failed send, without affecting other tokens`() {
        every { deviceTokenRepository.findByUserId(userId) } returns listOf(
            DeviceTokenEntity(userId = userId, token = "stale-token", platform = "ANDROID"),
            DeviceTokenEntity(userId = userId, token = "good-token", platform = "ANDROID"),
        )
        val staleException = mockk<FirebaseMessagingException>()
        every { staleException.messagingErrorCode } returns MessagingErrorCode.UNREGISTERED
        // First send (the stale token, iterated first) throws; the second (the good token)
        // succeeds -- proving one bad token doesn't stop the fan-out to the rest.
        every { fcmClient.send(any()) } throws staleException andThenAnswer { "message-id" }
        every { deviceTokenRepository.deleteByToken(any()) } returns Unit

        sender.sendToUser(userId, "Title", "Body")

        verify(exactly = 1) { deviceTokenRepository.deleteByToken("stale-token") }
        verify(exactly = 2) { fcmClient.send(any()) }
    }

    @Test
    fun `a send failure for one reason other than a stale token does not delete the token`() {
        every { deviceTokenRepository.findByUserId(userId) } returns listOf(
            DeviceTokenEntity(userId = userId, token = "token-a", platform = "ANDROID"),
        )
        val quotaException = mockk<FirebaseMessagingException>()
        every { quotaException.messagingErrorCode } returns MessagingErrorCode.QUOTA_EXCEEDED
        every { fcmClient.send(any()) } throws quotaException

        sender.sendToUser(userId, "Title", "Body")

        verify(exactly = 0) { deviceTokenRepository.deleteByToken(any()) }
    }

    @Test
    fun `an unexpected failure -- e_g_ an unconfigured Firebase credential -- never propagates`() {
        every { deviceTokenRepository.findByUserId(userId) } returns listOf(
            DeviceTokenEntity(userId = userId, token = "token-a", platform = "ANDROID"),
        )
        every { fcmClient.send(any()) } throws IllegalStateException("crichere.firebase.service-account-path is not configured")

        // Doesn't throw -- that's the assertion.
        sender.sendToUser(userId, "Title", "Body")
    }

    @Test
    fun `no device tokens registered is a silent no-op`() {
        every { deviceTokenRepository.findByUserId(userId) } returns emptyList()

        sender.sendToUser(userId, "Title", "Body")

        verify(exactly = 0) { fcmClient.send(any()) }
    }
}
