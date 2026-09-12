package com.crichere.backend.common

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Configuration for [PhotoUploadService]. */
@ConfigurationProperties(prefix = "crichere.aws")
data class AwsProperties(
    val s3: S3Properties = S3Properties(),
) {
    data class S3Properties(
        /** Blank in dev/CI, where the `crichere-media-dev` bucket does not exist yet. */
        val bucket: String = "",
        val region: String = "",
    )
}

@Configuration
@EnableConfigurationProperties(AwsProperties::class)
class AwsConfiguration {

    /**
     * The default provider chain (environment variables, `~/.aws/credentials`, an instance/
     * container role, ...). Safe to construct with no credentials present anywhere -- like
     * [software.amazon.awssdk.services.s3.presigner.S3Presigner], it does no I/O and performs
     * no lookup until [AwsCredentialsProvider.resolveCredentials] is actually called. That call
     * happens lazily, inside [PhotoUploadService], only when a photo-upload URL is requested --
     * so a missing AWS credential, exactly like a missing Firebase service account, cannot fail
     * application context startup.
     */
    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider = DefaultCredentialsProvider.builder().build()
}

/**
 * Generates S3 presigned POST requests -- profile photo uploads (`users/{userId}/profile.jpg`),
 * and (since Phase 2) league logos/banners (`leagues/{leagueId}/logo.jpg` /
 * `leagues/{leagueId}/banner.jpg`). Originally profile-only (Phase 1); moved to `common` and
 * given league-specific methods once a second feature needed the exact same S3 presigned-POST
 * mechanism -- the SigV4 signing logic itself is untouched by that move, only its package and
 * the set of key-building methods around it changed.
 *
 * ## Why this is hand-built instead of calling one AWS SDK method
 *
 * AWS SDK for Java v2's `S3Presigner` (`software.amazon.awssdk.services.s3.presigner`, part of
 * the `s3` module already on this project's classpath) presigns `PUT`, `GET`, `HEAD`, and
 * multipart-upload requests -- but, verified against the actual `2.54.10` jar on the classpath
 * (no `PresignPostRequest`/`PresignedPostRequest` classes exist in
 * `software.amazon.awssdk.services.s3.presigner.model`), it has never shipped a presigned
 * *POST* API, unlike `boto3`'s `generate_presigned_post`. This is a long-standing, openly
 * tracked gap in the Java v2 SDK, not an oversight in this codebase.
 *
 * A presigned POST is nonetheless just a documented, publicly specified computation --
 * AWS's ["Sigv4 POST policy"](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html)
 * algorithm: build a base64-encoded JSON policy document describing what the upload is allowed
 * to be, then HMAC-SHA256-sign it with the same SigV4 key-derivation chain the SDK uses
 * internally for every other request. Every step of that is pure, local computation -- there is
 * no network call to AWS involved in *generating* the presigned fields, only in the client's
 * subsequent `POST` to S3 -- so it is exactly as unit-testable as the SDK's own presigners would
 * have been, using a fake [AwsCredentialsProvider] in place of real credentials.
 *
 * ## The security boundary
 *
 * [key] is always derived from an id the caller cannot forge -- [createUploadUrl] takes it from
 * the caller's own JWT-resolved `userId` (never a client-supplied value), so a client can never
 * obtain a presigned POST for any prefix but its own. [createLeagueLogoUploadUrl]/
 * [createLeagueBannerUploadUrl] have no such implicit self-scoping (a league's id is not the
 * caller's own id) -- **the organizer-ownership check is the caller's responsibility**
 * (`LeagueService`, before it ever calls these), not this class's; this class only knows how to
 * presign a POST for whatever key it's given.
 */
@Service
class PhotoUploadService(
    private val properties: AwsProperties,
    private val credentialsProvider: AwsCredentialsProvider,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Builds a presigned POST for `users/{userId}/profile.jpg`.
     *
     * @throws PhotoUploadUnavailableException the bucket/region are not configured, or no AWS
     *   credentials could be resolved -- both expected in this environment until AWS setup
     *   happens (see [AwsConfiguration]).
     */
    fun createUploadUrl(userId: UUID): PhotoUploadUrlResponse = presign("users/$userId/profile.jpg")

    /** Builds a presigned POST for `leagues/{leagueId}/logo.jpg`. See the class doc's security-boundary note. */
    fun createLeagueLogoUploadUrl(leagueId: UUID): PhotoUploadUrlResponse = presign("leagues/$leagueId/logo.jpg")

    /** Builds a presigned POST for `leagues/{leagueId}/banner.jpg`. See the class doc's security-boundary note. */
    fun createLeagueBannerUploadUrl(leagueId: UUID): PhotoUploadUrlResponse = presign("leagues/$leagueId/banner.jpg")

    /** Builds a presigned POST for `franchises/{franchiseId}/logo.jpg`. See the class doc's security-boundary note -- the ownership check is `FranchiseService`'s responsibility. */
    fun createFranchiseLogoUploadUrl(franchiseId: UUID): PhotoUploadUrlResponse = presign("franchises/$franchiseId/logo.jpg")

    /**
     * Builds a presigned POST for a not-yet-claimed franchise's logo, before a claim (and
     * therefore a franchise id) exists -- keyed on `leagues/{leagueId}/franchise-logos/
     * {userId}-{random}.jpg`. The random suffix (not just `userId`) matters because one user can
     * claim more than one franchise in the same league (see docs/PHASE3.md's Decisions Made) --
     * without it, a second claim's logo upload would silently overwrite the first claim's S3
     * object and both franchises would end up pointing at the same evolving image. Always
     * self-scoped (the caller's own id), same implicit safety as [createUploadUrl].
     */
    fun createPendingFranchiseLogoUploadUrl(leagueId: UUID, userId: UUID): PhotoUploadUrlResponse =
        presign("leagues/$leagueId/franchise-logos/$userId-${UUID.randomUUID()}.jpg")

    /**
     * Builds a presigned POST for `leagues/{leagueId}/payments/{userId}.jpg` -- a payment-proof
     * screenshot for a player join or franchise claim. Keyed on the *caller's own* [userId], not a
     * player/franchise row id, because the screenshot is uploaded before that row exists (proof
     * is part of the join/claim request body) -- see docs/PHASE3.md's implementation plan,
     * decision 7. Always self-scoped (the caller's own JWT-resolved id), same implicit safety as
     * [createUploadUrl] -- no separate ownership check needed by the caller.
     */
    fun createPaymentScreenshotUploadUrl(leagueId: UUID, userId: UUID): PhotoUploadUrlResponse =
        presign("leagues/$leagueId/payments/$userId.jpg")

    private fun presign(key: String): PhotoUploadUrlResponse {
        val bucket = properties.s3.bucket
        val region = properties.s3.region
        if (bucket.isBlank() || region.isBlank()) {
            log.error("crichere.aws.s3.bucket/region is not configured; cannot presign a photo upload")
            throw PhotoUploadUnavailableException()
        }

        val credentials =
            try {
                credentialsProvider.resolveCredentials()
            } catch (e: Exception) {
                log.error("No AWS credentials are resolvable; cannot presign a photo upload", e)
                throw PhotoUploadUnavailableException()
            }

        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val expiresAt = now.plus(EXPIRY)
        val dateStamp = DATE_STAMP_FORMAT.format(now.atZone(ZoneOffset.UTC))
        val amzDate = AMZ_DATE_FORMAT.format(now.atZone(ZoneOffset.UTC))
        val credentialScope = "$dateStamp/$region/s3/aws4_request"
        val amzCredential = "${credentials.accessKeyId()}/$credentialScope"
        val sessionToken = (credentials as? AwsSessionCredentials)?.sessionToken()

        val conditions = buildList<Any> {
            add(mapOf("bucket" to bucket))
            add(mapOf("key" to key))
            add(listOf("content-length-range", MIN_PHOTO_BYTES, MAX_PHOTO_BYTES))
            add(listOf("starts-with", "\$Content-Type", "image/"))
            add(mapOf("x-amz-algorithm" to ALGORITHM))
            add(mapOf("x-amz-credential" to amzCredential))
            add(mapOf("x-amz-date" to amzDate))
            sessionToken?.let { add(mapOf("x-amz-security-token" to it)) }
        }
        val policyDocument = mapOf(
            "expiration" to ISO_INSTANT_MILLIS.format(expiresAt.atZone(ZoneOffset.UTC)),
            "conditions" to conditions,
        )
        val policyBase64 = Base64.getEncoder().encodeToString(
            objectMapper.writeValueAsBytes(policyDocument),
        )
        val signature = sign(credentials.secretAccessKey(), dateStamp, region, policyBase64)

        val fields = buildMap {
            put("key", key)
            put("x-amz-algorithm", ALGORITHM)
            put("x-amz-credential", amzCredential)
            put("x-amz-date", amzDate)
            sessionToken?.let { put("x-amz-security-token", it) }
            put("policy", policyBase64)
            put("x-amz-signature", signature)
        }

        return PhotoUploadUrlResponse(
            uploadUrl = "https://$bucket.s3.$region.amazonaws.com/",
            fields = fields,
            key = key,
            expiresAt = expiresAt,
        )
    }

    /** The SigV4 signing-key derivation chain, applied to the base64 policy document. */
    private fun sign(secretAccessKey: String, dateStamp: String, region: String, stringToSign: String): String {
        val kDate = hmacSha256(("AWS4$secretAccessKey").toByteArray(StandardCharsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, "s3")
        val kSigning = hmacSha256(kService, "aws4_request")
        return hmacSha256(kSigning, stringToSign).toHex()
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            out.append(HEX[(b.toInt() shr 4) and 0xF])
            out.append(HEX[b.toInt() and 0xF])
        }
        return out.toString()
    }

    companion object {
        const val ALGORITHM = "AWS4-HMAC-SHA256"

        /**
         * Upper bound on an uploaded image: generous enough for an unedited phone-camera JPEG
         * (a modern phone's default JPEG is typically 2-8MB) while still bounding the cost of
         * an abusive upload -- an object this feature will resize/serve as an avatar/logo/banner
         * has no legitimate need to be larger. Enforced by S3 itself via the policy's
         * `content-length-range` condition, not by this server (which never sees the file).
         */
        const val MAX_PHOTO_BYTES: Long = 10L * 1024 * 1024
        const val MIN_PHOTO_BYTES: Long = 1L

        /** How long the presigned fields remain usable. Short enough to limit a leaked URL's blast radius. */
        val EXPIRY: Duration = Duration.ofMinutes(5)

        private val DATE_STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
        private val AMZ_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        private val ISO_INSTANT_MILLIS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
