package com.crichere.app.auth

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ReusingPhoneAuthClientTest {

    private val fake = FakePhoneAuthClient()
    private val time = TestTimeSource()
    private val client = ReusingPhoneAuthClient(fake, timeSource = time)

    @Test
    fun sameNumberWithinWindowReusesPendingCodeWithoutCallingFirebase() = runTest {
        val first = client.sendVerificationCode("+911111111111").getOrThrow()
        time += 59.seconds

        val second = client.sendVerificationCode("+911111111111").getOrThrow()

        assertSame(first, second)
        assertEquals(1, fake.sendCallCount)
    }

    @Test
    fun sameNumberAfterWindowSendsAgain() = runTest {
        client.sendVerificationCode("+911111111111")
        time += 60.seconds

        client.sendVerificationCode("+911111111111")

        assertEquals(2, fake.sendCallCount)
    }

    @Test
    fun differentNumberSendsAgain() = runTest {
        client.sendVerificationCode("+911111111111")

        client.sendVerificationCode("+912222222222")

        assertEquals(listOf("+911111111111", "+912222222222"), fake.sentPhoneNumbers)
    }

    @Test
    fun resendWithTokenAlwaysReachesFirebase() = runTest {
        client.sendVerificationCode("+911111111111")

        client.sendVerificationCode("+911111111111", resendToken = "token")

        assertEquals(2, fake.sendCallCount)
        assertEquals("token", fake.sentResendTokens.last())
    }

    @Test
    fun failedSendIsNotReused() = runTest {
        fake.sendVerificationCodeResult = Result.failure(IllegalStateException("network"))
        client.sendVerificationCode("+911111111111")
        fake.sendVerificationCodeResult = Result.success(PhoneVerificationHandle("vid"))

        client.sendVerificationCode("+911111111111")

        assertEquals(2, fake.sendCallCount)
    }

    @Test
    fun verifiedCodeIsNotReused() = runTest {
        val handle = client.sendVerificationCode("+911111111111").getOrThrow()
        client.verifyCode(handle.verificationId, "123456")

        client.sendVerificationCode("+911111111111")

        assertEquals(2, fake.sendCallCount)
    }

    @Test
    fun hangingSendTimesOutWithRetryableError() = runTest {
        val hanging = object : PhoneAuthClient {
            override suspend fun sendVerificationCode(phoneNumber: String, resendToken: Any?): Result<PhoneVerificationHandle> =
                awaitCancellation()

            override suspend fun verifyCode(verificationId: String, code: String): Result<String> = Result.success("t")
        }

        val result = ReusingPhoneAuthClient(hanging).sendVerificationCode("+911111111111")

        assertTrue(result.isFailure)
        assertEquals("Couldn't send the code. Please try again.", result.exceptionOrNull()?.message)
    }
}
