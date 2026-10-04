package com.crichere.backend.common

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration
import java.util.UUID

/**
 * Turns a stored payment-screenshot URL into one the caller can actually open (docs/OPEN-ITEMS.md,
 * security audit 2026-10-04). Payment screenshots live under `leagues/{leagueId}/payments/`, which
 * the bucket policy keeps private -- unlike profile photos and logos, they carry the payer's name,
 * UPI id and transaction details, and both ids in the key are public in `GET /leagues/{id}`. So the
 * stored URL alone opens nothing; the response mapping (which already decides *who* may see it)
 * hands back a short-lived presigned GET instead.
 *
 * A `fun interface` so the mapping tests can pass a lambda instead of real AWS credentials.
 */
fun interface PaymentScreenshotUrlSigner {
    /**
     * @param storedUrl the row's `payment_screenshot_url`, as the client submitted it.
     * @param leagueId the row's league.
     * @param payerUserId the row's own user (player) or owner (franchise).
     * @return a presigned GET for an object in our bucket, `null` for an object in our bucket that is
     *   not this payer's own screenshot, or [storedUrl] unchanged for anything outside our bucket.
     */
    fun readUrl(storedUrl: String?, leagueId: UUID, payerUserId: UUID): String?
}

/**
 * The real [PaymentScreenshotUrlSigner]. Presigning is local computation (no network call), so
 * doing it while mapping every visible roster row is cheap.
 *
 * **The key is bound to the row, not read from the URL.** The URL is client-supplied: a player
 * could submit `.../leagues/{other}/payments/{victim}.jpg` as their own proof and, being allowed to
 * see their own row, get a signed link to someone else's screenshot. So only the one key this
 * payer's upload can have produced ([PhotoUploadService.createPaymentScreenshotUploadUrl]) is ever
 * signed; any other key in our bucket comes back `null`.
 *
 * Fails soft: no bucket configured (dev/CI) or no credentials returns the stored URL unchanged, the
 * same as before this class existed, so a missing AWS setup never breaks a league read.
 */
@Component
class S3PaymentScreenshotUrlSigner(
    private val properties: AwsProperties,
    private val credentialsProvider: AwsCredentialsProvider,
) : PaymentScreenshotUrlSigner {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Built on first use, like the credentials it reads: no I/O, and no failure at startup. */
    private val presigner: S3Presigner by lazy {
        S3Presigner.builder()
            .region(Region.of(properties.s3.region))
            .credentialsProvider(credentialsProvider)
            .build()
    }

    override fun readUrl(storedUrl: String?, leagueId: UUID, payerUserId: UUID): String? {
        if (storedUrl == null) return null
        val bucket = properties.s3.bucket
        val region = properties.s3.region
        if (bucket.isBlank() || region.isBlank()) return storedUrl

        val bucketPrefix = "https://$bucket.s3.$region.amazonaws.com/"
        if (!storedUrl.startsWith(bucketPrefix)) return storedUrl

        val key = storedUrl.removePrefix(bucketPrefix).substringBefore('?').substringBefore('#')
        if (key != PhotoUploadService.paymentScreenshotKey(leagueId, payerUserId)) return null

        return try {
            presigner.presignGetObject { request ->
                request.signatureDuration(TTL).getObjectRequest { it.bucket(bucket).key(key) }
            }.url().toString()
        } catch (e: Exception) {
            // Never the URL or key in the log: the key carries the user id.
            log.error("Couldn't presign a payment screenshot read: {}", e.javaClass.simpleName)
            storedUrl
        }
    }

    companion object {
        /** Long enough for an organizer to keep the league screen open while checking proofs. */
        val TTL: Duration = Duration.ofHours(1)
    }
}
