package com.crichere.backend.profile

import com.crichere.backend.auth.VerifiedFirebaseToken
import com.crichere.backend.common.AbstractWebIntegrationTest
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * `POST /profiles/me/photo-upload-url`'s happy path over real HTTP -- a separate test class
 * from [ProfileFlowIntegrationTest] because it needs `crichere.aws.s3.bucket`/`region`
 * configured and a fake [AwsCredentialsProvider] bean, which would otherwise make every other
 * profile test implicitly depend on AWS being "configured". [AwsCredentialsProvider] is faked
 * the same way [com.crichere.backend.auth.FirebaseTokenVerifier] is faked in
 * [AbstractWebIntegrationTest] -- real routing, real JWT auth, real [PhotoUploadService] logic,
 * only the actual credential source (and, implicitly, the eventual call to S3 itself, which
 * this test never makes) is substituted.
 */
@TestPropertySource(
    properties = [
        "crichere.aws.s3.bucket=crichere-media-dev",
        "crichere.aws.s3.region=ap-south-1",
    ],
)
@Import(PhotoUploadEndpointIntegrationTest.FakeAwsCredentialsConfiguration::class)
class PhotoUploadEndpointIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @TestConfiguration
    class FakeAwsCredentialsConfiguration {
        @Bean
        @Primary
        fun fakeAwsCredentialsProvider(): AwsCredentialsProvider =
            object : AwsCredentialsProvider {
                override fun resolveCredentials(): AwsCredentials =
                    AwsBasicCredentials.create("AKIAFAKEACCESSKEY", "fakeSecretAccessKeyFakeSecretAccessKey12")
            }
    }

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `the presigned photo upload targets the caller's own user id and the configured bucket`() {
        val phone = uniquePhone()
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "fb-$phone", phoneNumber = phone)

        val session = mockMvc.perform(
            post("/api/v1/auth/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("idToken" to "a-valid-firebase-id-token"))),
        )
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val body = objectMapper.readValue(session.response.contentAsString, Map::class.java) as Map<String, Any?>
        val userId = UUID.fromString(body["userId"] as String)
        val accessToken = body["accessToken"] as String

        mockMvc.perform(
            post("/api/v1/profiles/me/photo-upload-url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("users/$userId/profile.jpg"))
            .andExpect(jsonPath("$.uploadUrl").value("https://crichere-media-dev.s3.ap-south-1.amazonaws.com/"))
            .andExpect(jsonPath("$.fields.key").value("users/$userId/profile.jpg"))
            .andExpect(jsonPath("$.fields['x-amz-signature']").isNotEmpty)
            .andExpect(jsonPath("$.fields['policy']").isNotEmpty)
            .andExpect(jsonPath("$.expiresAt").isNotEmpty)
    }

    private fun uniquePhone(): String =
        "+9199" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
}
