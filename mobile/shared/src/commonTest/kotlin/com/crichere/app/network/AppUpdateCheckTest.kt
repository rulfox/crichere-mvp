package com.crichere.app.network

import com.crichere.app.account.AppUpdateRequired
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** docs/PHASE23.md: the app sends its version, and a 426 APP_UPDATE_REQUIRED raises the Update required screen. */
class AppUpdateCheckTest {
    private val problemJson = headersOf("Content-Type", "application/problem+json")

    @Test
    fun `both backend clients send the platform and version, and report an update required`() = runTest {
        configureAppInfo("android", "1.0.0")
        val seen = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            seen += request.headers["X-App-Platform"] to request.headers["X-App-Version"]
            respond(
                """{"code":"APP_UPDATE_REQUIRED","minVersion":"1.1.0","updateUrl":"https://play.example/app","status":426}""",
                HttpStatusCode.UpgradeRequired,
                problemJson,
            )
        }
        val raised = mutableListOf<AppUpdateRequired>()

        HttpClientFactory.create(loadTokens = { null }, refreshTokens = { null }, onUpdateRequired = { raised += it }, engine = engine)
            .get("/api/v1/leagues")
        HttpClientFactory.createAuthClient(onUpdateRequired = { raised += it }, engine = engine)
            .get("/api/v1/auth/config")

        assertEquals(listOf<Pair<String?, String?>>("android" to "1.0.0", "android" to "1.0.0"), seen)
        assertEquals(AppUpdateRequired("https://play.example/app", "1.1.0", "1.0.0"), raised.first())
        assertEquals(2, raised.size)
    }

    @Test
    fun `other errors, and a 426 without the code, raise nothing`() = runTest {
        val raised = mutableListOf<AppUpdateRequired>()
        var status = HttpStatusCode.UpgradeRequired
        var body = """{"code":"SOMETHING_ELSE"}"""
        val engine = MockEngine { respond(body, status, problemJson) }
        val client = HttpClientFactory.create(loadTokens = { null }, refreshTokens = { null }, onUpdateRequired = { raised += it }, engine = engine)

        client.get("/api/v1/leagues")
        status = HttpStatusCode.BadRequest
        body = """{"code":"APP_UPDATE_REQUIRED"}"""
        client.get("/api/v1/leagues")

        assertNull(raised.firstOrNull())
    }
}
