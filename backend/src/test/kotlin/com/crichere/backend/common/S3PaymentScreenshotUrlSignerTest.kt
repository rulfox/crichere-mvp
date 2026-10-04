package com.crichere.backend.common

import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [S3PaymentScreenshotUrlSigner]: presigning is local computation, so all of it runs here with fake
 * credentials. Whether S3 then honours the link is the live check in docs/OPEN-ITEMS.md.
 */
class S3PaymentScreenshotUrlSignerTest {

    private val leagueId: UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
    private val payerId: UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002")
    private val bucketPrefix = "https://crichere-media-prod.s3.ap-south-1.amazonaws.com/"
    private val ownKey = "leagues/$leagueId/payments/$payerId.jpg"

    private fun signer(bucket: String = "crichere-media-prod", region: String = "ap-south-1"): S3PaymentScreenshotUrlSigner {
        // A real static provider, not a mock: the presigner resolves identity through the SDK's newer API.
        val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIAFAKEACCESSKEY", "fakeSecretAccessKeyFakeSecretAccessKey12"))
        return S3PaymentScreenshotUrlSigner(AwsProperties(AwsProperties.S3Properties(bucket = bucket, region = region)), credentials)
    }

    @Test
    fun `the payer's own screenshot comes back as a one-hour presigned GET for that exact key`() {
        val signed = signer().readUrl("$bucketPrefix$ownKey?v=abc123", leagueId, payerId)!!

        assertTrue(signed.startsWith("$bucketPrefix$ownKey?"), signed)
        assertTrue("X-Amz-Signature=" in signed, signed)
        assertTrue("X-Amz-Expires=3600" in signed, signed)
        // The app's cache-busting `?v=` is not part of the object key and must not leak into the signed request.
        assertTrue("v=abc123" !in signed, signed)
    }

    @Test
    fun `another payer's key in our bucket is never signed`() {
        val victim = UUID.randomUUID()

        assertNull(signer().readUrl("${bucketPrefix}leagues/$leagueId/payments/$victim.jpg", leagueId, payerId))
    }

    @Test
    fun `the same payer's key from another league is never signed`() {
        assertNull(signer().readUrl("${bucketPrefix}leagues/${UUID.randomUUID()}/payments/$payerId.jpg", leagueId, payerId))
    }

    @Test
    fun `a non-payment key in our bucket is never signed`() {
        assertNull(signer().readUrl("${bucketPrefix}users/$payerId/profile.jpg", leagueId, payerId))
    }

    @Test
    fun `a URL outside our bucket is returned unchanged`() {
        assertEquals("https://example.com/proof.jpg", signer().readUrl("https://example.com/proof.jpg", leagueId, payerId))
    }

    @Test
    fun `a look-alike host is treated as outside our bucket`() {
        val lookalike = "https://crichere-media-prod.s3.ap-south-1.amazonaws.com.evil.test/$ownKey"

        assertEquals(lookalike, signer().readUrl(lookalike, leagueId, payerId))
    }

    @Test
    fun `no bucket configured returns the stored URL unchanged`() {
        val stored = "$bucketPrefix$ownKey"

        assertEquals(stored, signer(bucket = "", region = "").readUrl(stored, leagueId, payerId))
    }

    @Test
    fun `no screenshot stays null`() {
        assertNull(signer().readUrl(null, leagueId, payerId))
    }
}
