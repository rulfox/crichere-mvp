package com.crichere.backend.appupdate

import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun `parses one to three whole numbers, missing parts are zero`() {
        assertEquals(AppVersion(1, 1, 0), AppVersion.parse("1.1"))
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse(" 1 "))
        assertEquals(AppVersion(2, 10, 3), AppVersion.parse("2.10.3"))
        listOf(null, "", "abc", "1.x", "1..2", "1.2.3.4", "-1.0", "1.0-beta").forEach { assertNull(AppVersion.parse(it), "$it") }
    }

    @Test
    fun `compares numerically, not as text`() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.0")!!)
        assertTrue(AppVersion.parse("1.1")!! >= AppVersion.parse("1.1.0")!!)
        assertTrue(AppVersion.parse("1.0.9")!! < AppVersion.parse("1.1.0")!!)
    }
}

class AppUpdateFilterTest {
    // The app's own mapper flattens a problem's extensions to the top level (Spring's mixin), as clients read them.
    private val mapper = JsonMapper.builder().addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java).build()

    private fun run(
        platform: String?,
        version: String?,
        properties: AppUpdateProperties = AppUpdateProperties(minAndroid = "1.1.0", minIos = "1.2.0", androidUrl = "https://play.example/app", iosUrl = "https://testflight.apple.com/join/abc"),
    ): Pair<MockHttpServletResponse, Boolean> {
        val request = MockHttpServletRequest("GET", "/api/v1/leagues").apply {
            platform?.let { addHeader("X-App-Platform", it) }
            version?.let { addHeader("X-App-Version", it) }
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        AppUpdateFilter(properties, mapper).doFilter(request, response, chain)
        return response to (chain.request != null)
    }

    @Test
    fun `requests it can't judge always go through`() {
        assertTrue(run(null, null).second, "no headers: web viewer, admin, older builds")
        assertTrue(run("android", null).second)
        assertTrue(run(null, "0.1.0").second)
        assertTrue(run("windows", "0.1.0").second)
        assertTrue(run("android", "not-a-version").second)
        assertTrue(run("android", "0.1.0", AppUpdateProperties()).second, "no minimum set")
        assertTrue(run("ios", "0.1.0", AppUpdateProperties(minAndroid = "9.0.0")).second, "only Android has a minimum")
    }

    @Test
    fun `a build at or above the minimum goes through`() {
        assertTrue(run("android", "1.1.0").second)
        assertTrue(run("Android", "1.10.0").second)
        assertTrue(run("ios", "1.2").second)
    }

    @Test
    fun `an older build gets 426 APP_UPDATE_REQUIRED with the minimum and its store link`() {
        val (response, passed) = run("ios", "1.1.9")
        assertTrue(!passed)
        assertEquals(HttpServletResponse.SC_UPGRADE_REQUIRED, response.status)
        val body = mapper.readTree(response.contentAsString)
        assertEquals("APP_UPDATE_REQUIRED", body["code"].asString())
        assertEquals("1.2.0", body["minVersion"].asString())
        assertEquals("https://testflight.apple.com/join/abc", body["updateUrl"].asString())

        val (android, _) = run("android", "1.0.0", AppUpdateProperties(minAndroid = "1.1.0"))
        assertEquals(426, android.status)
        assertNull(mapper.readTree(android.contentAsString)["updateUrl"])
        assertNotNull(mapper.readTree(android.contentAsString)["minVersion"])
    }
}
