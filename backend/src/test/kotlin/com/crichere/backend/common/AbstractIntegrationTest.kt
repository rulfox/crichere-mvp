package com.crichere.backend.common

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Shared base for Testcontainers-backed integration tests. The Postgres container lives in
 * the companion object so every test class extending this one shares a single container
 * instance (and, since the Spring test context configuration is identical across them,
 * Spring's context cache reuses one application context too) instead of each constraint
 * test class paying the cost of its own container + context startup.
 *
 * Deliberately NOT using `@Testcontainers`/`@Container`: that JUnit5 extension manages the
 * container's start/stop lifecycle per test *class* (stopping it once the owning class's
 * tests finish), which tears the container down out from under every other subclass sharing
 * this same singleton instance. Instead the container is started exactly once, eagerly, and
 * never explicitly stopped -- Testcontainers' Ryuk reaper cleans it up when the JVM exits.
 *
 * `webEnvironment = NONE` because these tests exercise the JPA/repository layer directly --
 * no web server is needed.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class AbstractIntegrationTest {
    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:16")).apply { start() }
    }
}
