package com.crichere.app.profile

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `commonTest` (`kotlin.test`) coverage for [KtorProfileRepository] against a Ktor `MockEngine`,
 * per Task 5/6's established pattern -- real request shapes asserted: the `PUT` sends a full
 * snapshot, the multipart upload includes `fields` in order with `file` last.
 */
class ProfileRepositoryTest {

    private fun mockHttpClient(handler: suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("http://localhost/") }
            engine { addHandler(handler) }
        }

    private fun mockUploadClient(handler: suspend MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            engine { addHandler(handler) }
        }

    private fun requestBodyText(request: HttpRequestData): String =
        (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private suspend fun requestBodyBytes(request: HttpRequestData): ByteArray =
        when (val body = request.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes()
            is OutgoingContent.WriteChannelContent -> {
                val channel = ByteChannel()
                CoroutineScope(Dispatchers.Unconfined).launch {
                    body.writeTo(channel)
                    channel.close()
                }
                channel.readRemaining().readByteArray()
            }
            else -> error("Unsupported outgoing content type: $body")
        }

    @Test
    fun `getProfile GETs profiles me and parses a fully-null response`() = runTest {
        val httpClient = mockHttpClient { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/profiles/me", request.url.encodedPath)
            respond(
                content = emptyProfileJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = KtorProfileRepository(httpClient, mockUploadClient { error("no upload expected") })

        val profile = repository.getProfile()

        assertEquals(null, profile.name)
        assertEquals(false, profile.profileComplete)
    }

    @Test
    fun `saveProfile PUTs the full accumulated snapshot, not just the changed field`() = runTest {
        val httpClient = mockHttpClient { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/v1/profiles/me", request.url.encodedPath)
            val body = requestBodyText(request)
            // Every previously-set field must ride along, not just the one the user last touched.
            assertTrue(body.contains("\"name\":\"Rahul Sharma\""))
            assertTrue(body.contains("\"state\":\"Karnataka\""))
            assertTrue(body.contains("\"city\":\"Bengaluru\""))
            assertTrue(body.contains("\"playingRole\":\"BOWLER\""))
            assertTrue(body.contains("\"bowlingStyle\":\"RIGHT_ARM_FAST\""))
            respond(
                content = filledProfileJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = KtorProfileRepository(httpClient, mockUploadClient { error("no upload expected") })

        val snapshot = ProfileUpdateRequestDto(
            name = "Rahul Sharma",
            photoUrl = "https://bucket.s3.ap-south-1.amazonaws.com/users/u1/profile.jpg",
            state = "Karnataka",
            city = "Bengaluru",
            playingRole = PlayingRole.BOWLER,
            battingStyle = BattingStyle.RIGHT_HAND,
            bowlingStyle = BowlingStyle.RIGHT_ARM_FAST,
        )
        val result = repository.saveProfile(snapshot)

        assertEquals(true, result.profileComplete)
    }

    @Test
    fun `saveProfile throws on a non-2xx response`() = runTest {
        val httpClient = mockHttpClient {
            respond(content = "{}", status = HttpStatusCode.BadRequest, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repository = KtorProfileRepository(httpClient, mockUploadClient { error("no upload expected") })

        assertFailsWith<ProfileSaveFailedException> {
            repository.saveProfile(ProfileUpdateRequestDto(name = "x"))
        }
    }

    @Test
    fun `requestPhotoUploadUrl posts and parses the presigned fields`() = runTest {
        val httpClient = mockHttpClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/profiles/me/photo-upload-url", request.url.encodedPath)
            respond(
                content = photoUploadUrlJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = KtorProfileRepository(httpClient, mockUploadClient { error("no upload expected") })

        val info = repository.requestPhotoUploadUrl()

        assertEquals("https://crichere-media-dev.s3.ap-south-1.amazonaws.com/", info.uploadUrl)
        assertEquals("users/u1/profile.jpg", info.key)
        assertEquals(listOf("key", "x-amz-algorithm", "policy", "x-amz-signature"), info.fields.keys.toList())
    }

    @Test
    fun `requestPhotoUploadUrl throws PhotoUploadUnavailableException on a 503 -- unconfigured S3 in this environment`() = runTest {
        val httpClient = mockHttpClient {
            respond(
                content = """{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"photo-upload-unavailable"}""",
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }
        val repository = KtorProfileRepository(httpClient, mockUploadClient { error("no upload expected") })

        assertFailsWith<PhotoUploadUnavailableException> { repository.requestPhotoUploadUrl() }
    }

    @Test
    fun `uploadPhoto posts multipart with fields in order and file last, then computes photoUrl`() = runTest {
        val uploadInfo = PhotoUploadInfoDto(
            uploadUrl = "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/",
            fields = linkedMapOf(
                "key" to "users/u1/profile.jpg",
                "x-amz-algorithm" to "AWS4-HMAC-SHA256",
                "policy" to "base64policy",
                "x-amz-signature" to "sig",
            ),
            key = "users/u1/profile.jpg",
            expiresAt = "2026-09-03T12:05:00.000Z",
        )
        val uploadClient = mockUploadClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(uploadInfo.uploadUrl, request.url.toString())

            val bodyText = requestBodyBytes(request).decodeToString()
            val keyIndex = bodyText.indexOf("name=\"key\"")
            val algoIndex = bodyText.indexOf("name=\"x-amz-algorithm\"")
            val policyIndex = bodyText.indexOf("name=\"policy\"")
            val signatureIndex = bodyText.indexOf("name=\"x-amz-signature\"")
            val fileIndex = bodyText.indexOf("name=\"file\"")

            assertTrue(keyIndex in 0 until algoIndex)
            assertTrue(algoIndex in 0 until policyIndex)
            assertTrue(policyIndex in 0 until signatureIndex)
            assertTrue(signatureIndex in 0 until fileIndex, "the file part must come last")
            assertTrue(bodyText.contains("Content-Type: image/jpeg"))

            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val repository = KtorProfileRepository(mockHttpClient { error("no backend call expected") }, uploadClient)

        val photoUrl = repository.uploadPhoto(uploadInfo, byteArrayOf(1, 2, 3, 4), "image/jpeg")

        assertEquals("https://crichere-media-dev.s3.ap-south-1.amazonaws.com/users/u1/profile.jpg", photoUrl)
    }

    @Test
    fun `uploadPhoto throws PhotoUploadFailedException on a non-2xx S3 response`() = runTest {
        val uploadInfo = PhotoUploadInfoDto(
            uploadUrl = "https://bucket.s3.ap-south-1.amazonaws.com/",
            fields = mapOf("key" to "users/u1/profile.jpg"),
            key = "users/u1/profile.jpg",
            expiresAt = "2026-09-03T12:05:00.000Z",
        )
        val uploadClient = mockUploadClient { respond(content = "denied", status = HttpStatusCode.Forbidden) }
        val repository = KtorProfileRepository(mockHttpClient { error("no backend call expected") }, uploadClient)

        assertFailsWith<PhotoUploadFailedException> {
            repository.uploadPhoto(uploadInfo, byteArrayOf(1), "image/jpeg")
        }
    }

    private fun emptyProfileJson(): String = """
        {
            "userId": "11111111-1111-1111-1111-111111111111",
            "name": null,
            "photoUrl": null,
            "country": null,
            "state": null,
            "city": null,
            "playingRole": null,
            "battingStyle": null,
            "bowlingStyle": null,
            "profileComplete": false
        }
    """.trimIndent()

    private fun filledProfileJson(): String = """
        {
            "userId": "11111111-1111-1111-1111-111111111111",
            "name": "Rahul Sharma",
            "photoUrl": "https://bucket.s3.ap-south-1.amazonaws.com/users/u1/profile.jpg",
            "country": null,
            "state": "Karnataka",
            "city": "Bengaluru",
            "playingRole": "BOWLER",
            "battingStyle": "RIGHT_HAND",
            "bowlingStyle": "RIGHT_ARM_FAST",
            "profileComplete": true
        }
    """.trimIndent()

    private fun photoUploadUrlJson(): String = """
        {
            "uploadUrl": "https://crichere-media-dev.s3.ap-south-1.amazonaws.com/",
            "fields": {
                "key": "users/u1/profile.jpg",
                "x-amz-algorithm": "AWS4-HMAC-SHA256",
                "policy": "base64policy",
                "x-amz-signature": "sig"
            },
            "key": "users/u1/profile.jpg",
            "expiresAt": "2026-09-03T12:05:00.000Z"
        }
    """.trimIndent()
}
