package com.crichere.backend.admin

import com.crichere.backend.auth.JwtService
import com.crichere.backend.common.AbstractWebIntegrationTest
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A local stand-in for Google's signing keys, so tests can mint ID tokens. */
object TestGoogleKeys {
    val key: RSAKey = RSAKeyGenerator(2048).keyID("test-google").generate()
    val otherKey: RSAKey = RSAKeyGenerator(2048).keyID("test-google").generate()
}

@TestConfiguration
class TestGoogleJwksConfiguration {
    @Bean
    @Primary
    fun testGoogleJwkSource(): JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(TestGoogleKeys.key.toPublicJWK()))
}

/** Admin sign-in and sessions over real HTTP and a real database (docs/PHASE20.md Step 2). */
@Import(TestGoogleJwksConfiguration::class)
class AdminAuthFlowIntegrationTest : AbstractWebIntegrationTest() {

    @Autowired private lateinit var jwtService: JwtService
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var signInLimiter: AdminSignInRateLimiter

    @BeforeEach
    fun resetLimiter() = signInLimiter.reset()

    // ------------------------------------------------------------------ helpers

    private fun idToken(
        email: String = "admin@example.com",
        audience: String = CLIENT_ID,
        issuer: String = "https://accounts.google.com",
        emailVerified: Boolean = true,
        expiresAt: Instant = Instant.now().plusSeconds(600),
        key: RSAKey = TestGoogleKeys.key,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer).audience(audience).subject("1234567890")
            .claim("email", email).claim("email_verified", emailVerified)
            .issueTime(Date()).expirationTime(Date.from(expiresAt))
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(key)) }.serialize()
    }

    private fun MockHttpServletRequestBuilder.fromAdminSite(): MockHttpServletRequestBuilder =
        header("Origin", ORIGIN).header(ADMIN_HEADER, "1")

    private fun signIn(token: String, guarded: Boolean = true): ResultActions =
        mockMvc.perform(
            post("/api/v1/admin/session").let { if (guarded) it.fromAdminSite() else it }
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"credential":"$token"}"""),
        )

    private fun sessionCookie(token: String = idToken()): Cookie {
        val setCookie = signIn(token).andExpect(status().isOk).andReturn().response.getHeader(HttpHeaders.SET_COOKIE)
        assertNotNull(setCookie)
        val value = setCookie.substringAfter("$ADMIN_COOKIE=").substringBefore(";")
        return Cookie(ADMIN_COOKIE, value)
    }

    private fun current(cookie: Cookie?): ResultActions =
        mockMvc.perform(get("/api/v1/admin/session").let { if (cookie != null) it.cookie(cookie) else it })

    // ------------------------------------------------------------------ sign-in

    @Test
    fun `an allowlisted Google account gets a locked-down session cookie`() {
        val result = signIn(idToken())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("admin@example.com"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        assertTrue("HttpOnly" in setCookie, setCookie)
        assertTrue("Secure" in setCookie, setCookie)
        assertTrue("SameSite=Strict" in setCookie, setCookie)
        assertTrue("Path=/api/v1/admin" in setCookie, setCookie)
        assertTrue("Max-Age=1800" in setCookie, setCookie)
        assertTrue("Domain" !in setCookie, setCookie)
    }

    @Test
    fun `the cookie opens the admin API and only a hash is stored`() {
        val cookie = sessionCookie()
        current(cookie).andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("admin@example.com"))
            .andExpect(jsonPath("$.liveSessions").isNumber)
        val stored = jdbcTemplate.queryForList("select token_hash from admin_sessions", String::class.java)
        assertTrue(stored.none { it == cookie.value }, "raw token must never be stored")
    }

    @Test
    fun `allowlist matching ignores case`() {
        signIn(idToken(email = "second.admin@EXAMPLE.com")).andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("second.admin@example.com"))
    }

    @Test
    fun `every bad token gets the same refusal`() {
        val bad = listOf(
            idToken(email = "someone@example.com"),
            idToken(audience = "another-client.apps.googleusercontent.com"),
            idToken(issuer = "https://evil.example.com"),
            idToken(emailVerified = false),
            idToken(expiresAt = Instant.now().minusSeconds(600)),
            idToken(key = TestGoogleKeys.otherKey),
            "not-a-jwt",
        )
        bad.forEach { token ->
            signIn(token).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("ADMIN_SIGN_IN_FAILED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        }
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from admin_sessions where email = 'someone@example.com'", Long::class.java))
    }

    @Test
    fun `sign-in must come from the admin site`() {
        signIn(idToken(), guarded = false).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ADMIN_REQUEST_REJECTED"))
        mockMvc.perform(
            post("/api/v1/admin/session").header("Origin", "https://evil.example.com").header(ADMIN_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON).content("""{"credential":"${idToken()}"}"""),
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            post("/api/v1/admin/session").header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content("""{"credential":"${idToken()}"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `sign-in attempts are rate limited per IP`() {
        repeat(10) { signIn(idToken(email = "someone@example.com")).andExpect(status().isUnauthorized) }
        signIn(idToken()).andExpect(status().isTooManyRequests).andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
    }

    // ------------------------------------------------------------------ separation from the app

    @Test
    fun `no cookie, a junk cookie or an app token gets nothing`() {
        current(null).andExpect(status().isUnauthorized)
        current(Cookie(ADMIN_COOKIE, "junk")).andExpect(status().isUnauthorized)
        val appToken = jwtService.issueAccessToken(UUID.randomUUID()).token
        mockMvc.perform(get("/api/v1/admin/session").header(HttpHeaders.AUTHORIZATION, "Bearer $appToken"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the admin cookie is worthless on app routes`() {
        val cookie = sessionCookie()
        mockMvc.perform(get("/api/v1/blocks").cookie(cookie)).andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------ ending sessions

    @Test
    fun `sign out ends this session at once and clears the cookie`() {
        val cookie = sessionCookie()
        val res = mockMvc.perform(delete("/api/v1/admin/session").fromAdminSite().cookie(cookie))
            .andExpect(status().isNoContent).andReturn()
        assertTrue("Max-Age=0" in res.response.getHeader(HttpHeaders.SET_COOKIE)!!)
        current(cookie).andExpect(status().isUnauthorized)
    }

    @Test
    fun `sign out needs the admin-site guard too`() {
        val cookie = sessionCookie()
        mockMvc.perform(delete("/api/v1/admin/session").cookie(cookie)).andExpect(status().isForbidden)
        current(cookie).andExpect(status().isOk)
    }

    @Test
    fun `sign out everywhere ends every session for that admin only`() {
        val first = sessionCookie()
        val second = sessionCookie()
        val other = sessionCookie(idToken(email = "second.admin@example.com"))
        mockMvc.perform(post("/api/v1/admin/session/sign-out-everywhere").fromAdminSite().cookie(first))
            .andExpect(status().isNoContent)
        current(first).andExpect(status().isUnauthorized)
        current(second).andExpect(status().isUnauthorized)
        current(other).andExpect(status().isOk)
    }

    @Test
    fun `an expired session stops working`() {
        val cookie = sessionCookie()
        jdbcTemplate.update("update admin_sessions set expires_at = now() - interval '1 second' where email = 'admin@example.com'")
        current(cookie).andExpect(status().isUnauthorized)
    }

    @Test
    fun `sign-in and sign-out are audited with the admin's email`() {
        val cookie = sessionCookie(idToken(email = "second.admin@example.com"))
        mockMvc.perform(delete("/api/v1/admin/session").fromAdminSite().cookie(cookie)).andExpect(status().isNoContent)
        val actions = jdbcTemplate.queryForList(
            "select action from moderation_actions where admin_email = 'second.admin@example.com' order by created_at",
            String::class.java,
        )
        assertTrue(actions.containsAll(listOf("ADMIN_SIGN_IN", "ADMIN_SIGN_OUT")), actions.toString())
    }

    // ------------------------------------------------------------------ CORS

    @Test
    fun `only the admin origin passes the preflight, with credentials`() {
        mockMvc.perform(
            options("/api/v1/admin/session").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,$ADMIN_HEADER"),
        ).andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
        mockMvc.perform(
            options("/api/v1/admin/session").header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "POST"),
        ).andExpect(status().isForbidden)
    }

    private companion object {
        const val ORIGIN = "https://admin.test"
        const val CLIENT_ID = "test-admin-client.apps.googleusercontent.com"
    }
}
