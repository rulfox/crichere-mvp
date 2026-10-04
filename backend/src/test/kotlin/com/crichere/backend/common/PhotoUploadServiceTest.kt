package com.crichere.backend.common

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Everything about [PhotoUploadService] this environment lets us test without live AWS
 * credentials: the whole presigned-POST construction is pure, local computation (see the
 * class doc on [PhotoUploadService] for why -- AWS SDK v2 has no presigned-POST API to call in
 * the first place), so all of it -- key namespacing, policy conditions, expiry, and the SigV4
 * signature itself -- is exercised here with a fake [AwsCredentialsProvider]. What is
 * genuinely untestable without a real bucket is whether S3 actually *accepts* the resulting
 * POST; nothing in this class asserts that, and nothing in this codebase ever makes that call.
 */
class PhotoUploadServiceTest {

    private val fixedNow: Instant = Instant.parse("2026-09-02T10:15:30.000Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val objectMapper = JsonMapper()
    private val userId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private val secretAccessKey = "fakeSecretAccessKeyFakeSecretAccessKey12"

    private fun properties(bucket: String = "crichere-media-dev", region: String = "ap-south-1") =
        AwsProperties(s3 = AwsProperties.S3Properties(bucket = bucket, region = region))

    private fun basicCredentialsProvider(): AwsCredentialsProvider {
        val provider = mockk<AwsCredentialsProvider>()
        every { provider.resolveCredentials() } returns AwsBasicCredentials.create("AKIAFAKEACCESSKEY", secretAccessKey)
        return provider
    }

    private fun service(
        properties: AwsProperties = properties(),
        credentialsProvider: AwsCredentialsProvider = basicCredentialsProvider(),
    ) = PhotoUploadService(properties, credentialsProvider, objectMapper, clock)

    // ---------------------------------------------------------------- the security boundary

    @Test
    fun `the key is namespaced under the caller's own user id`() {
        val result = service().createUploadUrl(userId)

        assertEquals("users/$userId/profile.jpg", result.key)
        assertEquals("users/$userId/profile.jpg", result.fields["key"])
    }

    @Test
    fun `two different callers get two different keys`() {
        val otherUserId = UUID.randomUUID()

        val mine = service().createUploadUrl(userId)
        val theirs = service().createUploadUrl(otherUserId)

        assertTrue(mine.key != theirs.key)
    }

    // ---------------------------------------------------------------- URL / expiry

    @Test
    fun `the upload URL targets the configured bucket and region`() {
        val result = service(properties = properties(bucket = "my-bucket", region = "us-east-1")).createUploadUrl(userId)

        assertEquals("https://my-bucket.s3.us-east-1.amazonaws.com/", result.uploadUrl)
    }

    @Test
    fun `expiry is five minutes from now`() {
        val result = service().createUploadUrl(userId)

        assertEquals(fixedNow.plusSeconds(5 * 60), result.expiresAt)
    }

    // ---------------------------------------------------------------- policy conditions

    @Test
    fun `the policy restricts content length to the configured maximum`() {
        val result = service().createUploadUrl(userId)

        val contentLengthRange = conditionList(result).first { it.firstOrNull() == "content-length-range" }
        assertEquals(PhotoUploadService.MIN_PHOTO_BYTES, (contentLengthRange[1] as Number).toLong())
        assertEquals(PhotoUploadService.MAX_PHOTO_BYTES, (contentLengthRange[2] as Number).toLong())
        assertEquals(10L * 1024 * 1024, PhotoUploadService.MAX_PHOTO_BYTES, "documented choice: 10MB")
    }

    @Test
    fun `the policy restricts content type to image mime types`() {
        val result = service().createUploadUrl(userId)

        val contentTypeCondition = conditionList(result).first { it.firstOrNull() == "starts-with" }
        assertEquals(listOf("starts-with", "\$Content-Type", "image/"), contentTypeCondition)
    }

    @Test
    fun `uploads carry a long cache header, pinned by the policy`() {
        val result = service().createUploadUrl(userId)

        assertEquals(PhotoUploadService.CACHE_CONTROL, result.fields["Cache-Control"])
        assertEquals(PhotoUploadService.CACHE_CONTROL, conditionMaps(result).first { it.containsKey("Cache-Control") }["Cache-Control"])
        assertEquals("public, max-age=31536000, immutable", PhotoUploadService.CACHE_CONTROL)
    }

    @Test
    fun `the policy pins the exact bucket and key -- no wildcard escape hatch`() {
        val result = service().createUploadUrl(userId)

        val conditionMaps = conditionMaps(result)
        assertEquals("crichere-media-dev", conditionMaps.first { it.containsKey("bucket") }["bucket"])
        assertEquals("users/$userId/profile.jpg", conditionMaps.first { it.containsKey("key") }["key"])
    }

    @Test
    fun `the policy expiration matches the returned expiry`() {
        val result = service().createUploadUrl(userId)

        val policy = decodePolicy(result.fields.getValue("policy"))
        assertEquals("2026-09-02T10:20:30.000Z", policy["expiration"])
    }

    // ---------------------------------------------------------------- the SigV4 signature

    /**
     * A fixed AWS SigV4 reference vector, computed OUTSIDE this codebase entirely -- via
     * Python's standard-library `hmac`/`hashlib` (not this class's `hmac()` helper, and not
     * [PhotoUploadService.sign]) -- so this test can catch a bug shared by both of this file's
     * Kotlin HMAC implementations (production and the older self-consistency test below), which
     * a same-algorithm-compared-to-itself test structurally cannot.
     *
     * Derivation, run once against the real POST base64 policy this fixed test setup (userId,
     * clock, bucket, region, credentials) deterministically produces:
     * ```python
     * import hmac, hashlib
     * def hmac_sha256(key, msg): return hmac.new(key, msg.encode(), hashlib.sha256).digest()
     * k_date    = hmac_sha256(("AWS4" + secret).encode(), "20260902")
     * k_region  = hmac_sha256(k_date, "ap-south-1")
     * k_service = hmac_sha256(k_region, "s3")
     * k_signing = hmac_sha256(k_service, "aws4_request")
     * hmac.new(k_signing, policy_base64.encode(), hashlib.sha256).hexdigest()
     * # -> "8ae9838d8a2cccfc0aa5b8248ec5b898ba4c0661f3e2a5e31a086bde9db8941b"
     * ```
     * with `secret = "fakeSecretAccessKeyFakeSecretAccessKey12"` (this file's [secretAccessKey])
     * and `policy_base64` equal to the exact string this test asserts `createUploadUrl` produced
     * (captured from a real run against this class's fixed clock/userId/bucket/region/credentials,
     * then independently re-derived by the Python snippet above -- the two agreed). Recomputed
     * 2026-10-04 when the policy gained its `Cache-Control` condition (docs/PHASE14.md): the new policy
     * was decoded and checked to equal the previous one plus exactly that condition, and the same
     * snippet reproduced the previous signature before producing this one.
     */
    @Test
    fun `the signature matches a fixed AWS SigV4 reference vector computed independently`() {
        val result = service().createUploadUrl(userId)

        val expectedPolicyBase64 =
            "eyJleHBpcmF0aW9uIjoiMjAyNi0wOS0wMlQxMDoyMDozMC4wMDBaIiwiY29uZGl0aW9ucyI6W3siYnVja2V0IjoiY3JpY2hlcmUtbWVkaWEtZGV2In0seyJrZXkiOiJ1c2Vycy8xMTExMTExMS0yMjIyLTMzMzMtNDQ0NC01NTU1NTU1NTU1NTUvcHJvZmlsZS5qcGcifSxbImNvbnRlbnQtbGVuZ3RoLXJhbmdlIiwxLDEwNDg1NzYwXSxbInN0YXJ0cy13aXRoIiwiJENvbnRlbnQtVHlwZSIsImltYWdlLyJdLHsiQ2FjaGUtQ29udHJvbCI6InB1YmxpYywgbWF4LWFnZT0zMTUzNjAwMCwgaW1tdXRhYmxlIn0seyJ4LWFtei1hbGdvcml0aG0iOiJBV1M0LUhNQUMtU0hBMjU2In0seyJ4LWFtei1jcmVkZW50aWFsIjoiQUtJQUZBS0VBQ0NFU1NLRVkvMjAyNjA5MDIvYXAtc291dGgtMS9zMy9hd3M0X3JlcXVlc3QifSx7IngtYW16LWRhdGUiOiIyMDI2MDkwMlQxMDE1MzBaIn1dfQ=="
        // Sanity check that this test's fixture setup hasn't silently drifted from the vector's
        // derivation -- if this fails, the reference vector above is stale and must be
        // recomputed against the new policy string, not patched around.
        assertEquals(expectedPolicyBase64, result.fields.getValue("policy"))

        val expectedSignature = "8ae9838d8a2cccfc0aa5b8248ec5b898ba4c0661f3e2a5e31a086bde9db8941b"
        assertEquals(expectedSignature, result.fields["x-amz-signature"])
    }

    @Test
    fun `the signature verifies against the SigV4 derivation computed independently`() {
        val result = service().createUploadUrl(userId)

        val expected = independentSignature(
            secretAccessKey = secretAccessKey,
            dateStamp = "20260902",
            region = "ap-south-1",
            stringToSign = result.fields.getValue("policy"),
        )
        assertEquals(expected, result.fields["x-amz-signature"])
    }

    @Test
    fun `a different secret key produces a different signature`() {
        val otherProvider = mockk<AwsCredentialsProvider>()
        every { otherProvider.resolveCredentials() } returns AwsBasicCredentials.create("AKIAOTHER", "aCompletelyDifferentSecretAccessKey!!!!")

        val first = service().createUploadUrl(userId)
        val second = service(credentialsProvider = otherProvider).createUploadUrl(userId)

        assertTrue(first.fields["x-amz-signature"] != second.fields["x-amz-signature"])
    }

    // ---------------------------------------------------------------- session credentials

    @Test
    fun `session credentials add a security token field and condition`() {
        val provider = mockk<AwsCredentialsProvider>()
        every { provider.resolveCredentials() } returns
            AwsSessionCredentials.create("AKIAFAKEACCESSKEY", secretAccessKey, "a-session-token")

        val result = service(credentialsProvider = provider).createUploadUrl(userId)

        assertEquals("a-session-token", result.fields["x-amz-security-token"])
        val tokenCondition = conditionMaps(result).first { it.containsKey("x-amz-security-token") }
        assertEquals("a-session-token", tokenCondition["x-amz-security-token"])
    }

    @Test
    fun `basic (non-session) credentials never add a security token`() {
        val result = service().createUploadUrl(userId)

        assertNull(result.fields["x-amz-security-token"])
        assertTrue(conditionMaps(result).none { it.containsKey("x-amz-security-token") })
    }

    // ---------------------------------------------------------------- graceful degradation without AWS setup

    @Test
    fun `a blank bucket is reported as photo upload being unavailable, not a crash`() {
        assertFailsWith<PhotoUploadUnavailableException> {
            service(properties = properties(bucket = "")).createUploadUrl(userId)
        }
    }

    @Test
    fun `a blank region is reported as photo upload being unavailable`() {
        assertFailsWith<PhotoUploadUnavailableException> {
            service(properties = properties(region = "")).createUploadUrl(userId)
        }
    }

    @Test
    fun `unresolvable AWS credentials are reported as photo upload being unavailable`() {
        val provider = mockk<AwsCredentialsProvider>()
        every { provider.resolveCredentials() } throws RuntimeException("no credentials anywhere")

        assertFailsWith<PhotoUploadUnavailableException> {
            service(credentialsProvider = provider).createUploadUrl(userId)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun decodePolicy(policyBase64: String): Map<String, Any?> {
        val json = String(Base64.getDecoder().decode(policyBase64), StandardCharsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
    }

    private fun conditionList(result: PhotoUploadUrlResponse): List<List<*>> =
        (decodePolicy(result.fields.getValue("policy"))["conditions"] as List<*>)
            .filterIsInstance<List<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun conditionMaps(result: PhotoUploadUrlResponse): List<Map<String, Any?>> =
        (decodePolicy(result.fields.getValue("policy"))["conditions"] as List<*>)
            .filterIsInstance<Map<*, *>>()
            .map { it as Map<String, Any?> }

    /** The same SigV4 key-derivation chain [PhotoUploadService] uses, computed independently in the test. */
    private fun independentSignature(secretAccessKey: String, dateStamp: String, region: String, stringToSign: String): String {
        val kDate = hmac(("AWS4$secretAccessKey").toByteArray(StandardCharsets.UTF_8), dateStamp)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, "s3")
        val kSigning = hmac(kService, "aws4_request")
        return hmac(kSigning, stringToSign).joinToString("") { "%02x".format(it) }
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
}
