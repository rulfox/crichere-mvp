package com.crichere.backend.common

import com.crichere.backend.auth.FirebaseTokenVerifier
import io.mockk.clearMocks
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Supplies a mocked [FirebaseTokenVerifier].
 *
 * `@Primary` so it wins over the real `FirebaseAdminTokenVerifier`, which stays in the context
 * harmlessly -- that bean only touches the Admin SDK lazily, on a call these tests never make.
 */
@TestConfiguration
class MockedFirebaseConfiguration {
    @Bean
    @Primary
    fun mockFirebaseTokenVerifier(): FirebaseTokenVerifier = mockk()
}

/**
 * Base for HTTP-level integration tests: the whole application, a real Postgres, real Flyway
 * migrations, the real Spring Security filter chain, and requests driven through `MockMvc` so
 * every filter, converter and exception handler on the path is exercised.
 *
 * **Exactly one thing is faked: [FirebaseTokenVerifier].** Verifying a real Firebase ID token
 * would mean a network call to Google and a service account this project does not have.
 * Nothing else -- not the database, not the JWT signing, not the phone encryption, not the
 * rate limiter -- is stubbed, and no test in this codebase ever reaches the real Firebase
 * Admin SDK.
 *
 * The container is a shared singleton started once for the JVM rather than a `@Container`
 * managed per class, so sibling test classes reuse it instead of each paying container
 * startup. Same reasoning as `AbstractIntegrationTest`.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(MockedFirebaseConfiguration::class)
abstract class AbstractWebIntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var firebaseTokenVerifier: FirebaseTokenVerifier

    /** The mock is a context-scoped singleton, so stubbing must not leak between tests. */
    @BeforeEach
    fun resetFirebaseVerifierStubbing() {
        clearMocks(firebaseTokenVerifier)
    }

    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:16")).apply { start() }
    }
}
