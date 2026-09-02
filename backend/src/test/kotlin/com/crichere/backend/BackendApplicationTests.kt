package com.crichere.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertNotNull

/**
 * Exit criterion for Task 1: the Spring context boots successfully against a real
 * Postgres instance (via Testcontainers), not just an in-memory/mocked one.
 *
 * This is intentionally the only test in this skeleton task — later tasks own real
 * feature test coverage.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTests {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `context loads against a real Testcontainers Postgres`() {
        assertNotNull(applicationContext)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
    }
}
