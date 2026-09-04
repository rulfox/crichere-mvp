package com.crichere.backend.auth

import com.crichere.backend.common.AbstractWebIntegrationTest
import com.crichere.backend.profile.BattingStyle
import com.crichere.backend.profile.BowlingStyle
import com.crichere.backend.profile.PlayingRole
import com.crichere.backend.profile.ProfileEntity
import com.crichere.backend.profile.ProfileRepository
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The end-to-end auth flow over real HTTP against a real Postgres:
 * `/session` -> `/refresh` -> `/logout`, plus the error shapes.
 *
 * Only [FirebaseTokenVerifier] is mocked (see [AbstractWebIntegrationTest]); everything else
 * -- routing, validation, the security filter chain, JWT signing, phone encryption, Flyway
 * migrations, JPA -- is the real thing.
 */
class AuthFlowIntegrationTest : AbstractWebIntegrationTest {

    constructor() : super()

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var profileRepository: ProfileRepository

    @Autowired
    private lateinit var phoneCryptoService: PhoneCryptoService

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var rateLimiter: AuthRateLimiter

    /** Each test gets its own phone number, so tests never collide on the `users` unique index. */
    private lateinit var phone: String

    @BeforeEach
    fun freshCaller() {
        phone = uniquePhone()
        // The IP bucket is a context-scoped singleton shared by every test in this class.
        rateLimiter.reset()
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    fun `session then refresh then logout`() {
        givenFirebaseAccepts(phone)

        // --- sign in -------------------------------------------------------------
        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.profileComplete").value(false))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()
            .body()

        val userId = UUID.fromString(session["userId"] as String)
        val firstRefreshToken = session["refreshToken"] as String

        // The access token really is ours and really names this user.
        assertEquals(userId, jwtService.parseAccessToken(session["accessToken"] as String))

        // --- rotate --------------------------------------------------------------
        val refreshed = postJson("/api/v1/auth/refresh", mapOf("refreshToken" to firstRefreshToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.profileComplete").value(false))
            .andReturn()
            .body()

        val secondRefreshToken = refreshed["refreshToken"] as String
        assertNotEquals(firstRefreshToken, secondRefreshToken, "refresh must hand back a new token")
        assertNotEquals(session["accessToken"], refreshed["accessToken"])

        // Rotation means the spent token is dead, not merely superseded.
        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to firstRefreshToken))
            .andExpect(status().isUnauthorized)

        // --- log out -------------------------------------------------------------
        postJson("/api/v1/auth/logout", mapOf("refreshToken" to secondRefreshToken))
            .andExpect(status().isNoContent)

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to secondRefreshToken))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the access token opens a protected endpoint`() {
        givenFirebaseAccepts(phone)
        val accessToken = signIn()["accessToken"] as String

        // No protected endpoint has been built yet, so the observable difference is the status:
        // 401 without a token, and something other than 401 (404 -- no such route) with one.
        // That is precisely what proves the JWT filter authenticates rather than merely decodes.
        mockMvc.perform(get("/api/v1/protected-probe"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(get("/api/v1/protected-probe").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isNotFound)
    }

    // ---------------------------------------------------------------- persistence

    @Test
    fun `signing in twice with the same number reuses one account`() {
        givenFirebaseAccepts(phone)

        val first = signIn()
        val second = signIn()

        assertEquals(first["userId"], second["userId"])
        val lookupHash = phoneCryptoService.hmacLookupHash(phone)
        assertEquals(
            1,
            userRepository.findAll().count { it.phoneLookupHash == lookupHash },
            "the phone_lookup_hash unique index and find-or-create must yield exactly one row",
        )
    }

    @Test
    fun `the phone number is not stored in plaintext`() {
        givenFirebaseAccepts(phone)
        val userId = UUID.fromString(signIn()["userId"] as String)

        val user = userRepository.findById(userId).orElseThrow()

        assertFalse(user.phoneEncrypted.contains(phone))
        assertFalse(user.phoneLookupHash.contains(phone))
        assertEquals(phone, phoneCryptoService.decryptFromString(user.phoneEncrypted))
    }

    @Test
    fun `the raw refresh token is never written to the database`() {
        givenFirebaseAccepts(phone)
        val session = signIn()
        val rawToken = session["refreshToken"] as String
        val userId = UUID.fromString(session["userId"] as String)

        val rows = refreshTokenRepository.findAll().filter { it.userId == userId }

        assertEquals(1, rows.size)
        assertNotEquals(rawToken, rows.single().tokenHash)
        assertEquals(jwtService.hashRefreshToken(rawToken), rows.single().tokenHash)
        assertNull(rows.single().revokedAt)
        // And the raw token is genuinely findable by its hash, which is how /refresh works.
        assertNotNull(refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawToken)))
    }

    @Test
    fun `refresh revokes the old row rather than deleting it`() {
        givenFirebaseAccepts(phone)
        val session = signIn()
        val userId = UUID.fromString(session["userId"] as String)
        val firstHash = jwtService.hashRefreshToken(session["refreshToken"] as String)

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to session["refreshToken"]))
            .andExpect(status().isOk)

        // Revocation history survives, which is what makes reuse of a rotated token auditable.
        assertNotNull(refreshTokenRepository.findByTokenHash(firstHash)?.revokedAt)
        assertEquals(2, refreshTokenRepository.findAll().count { it.userId == userId })
    }

    @Test
    fun `profileComplete reflects a finished profile on both session and refresh`() {
        givenFirebaseAccepts(phone)
        val userId = UUID.fromString(signIn()["userId"] as String)

        profileRepository.save(
            ProfileEntity(
                userId = userId,
                name = "Jasprit Bumrah",
                photoUrl = "https://cdn.crichere.app/photos/jasprit.jpg",
                country = "IN",
                state = "Gujarat",
                city = "Ahmedabad",
                playingRole = PlayingRole.BOWLER,
                battingStyle = BattingStyle.RIGHT_HAND,
                bowlingStyle = BowlingStyle.RIGHT_ARM_FAST,
            ),
        )

        val session = postJson("/api/v1/auth/session", mapOf("idToken" to "token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profileComplete").value(true))
            .andReturn()
            .body()

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to session["refreshToken"]))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profileComplete").value(true))
    }

    // ---------------------------------------------------------------- error shapes

    @Test
    fun `a validation failure returns an RFC 7807 problem detail`() {
        postJson("/api/v1/auth/session", mapOf("idToken" to ""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/validation-failed"))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(jsonPath("$.instance").value("/api/v1/auth/session"))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(jsonPath("$.errors.idToken").value("idToken is required"))
    }

    @Test
    fun `a malformed body returns a problem detail and leaks nothing`() {
        mockMvc.perform(
            post("/api/v1/auth/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andExpect(noInternalsLeaked())
    }

    @Test
    fun `a rejected Firebase token returns a 401 problem detail from the controller advice`() {
        every { firebaseTokenVerifier.verify(any()) } throws InvalidFirebaseIdTokenException()

        postJson("/api/v1/auth/session", mapOf("idToken" to "a-forged-token"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/invalid-credentials"))
            .andExpect(jsonPath("$.title").value("Invalid credentials"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.instance").value("/api/v1/auth/session"))
            .andExpect(noInternalsLeaked())
    }

    @Test
    fun `an unauthenticated request to a protected endpoint returns a 401 problem detail`() {
        // This is the gap the entry-point wiring exists to close: Spring Security rejects the
        // request inside the filter chain, so @RestControllerAdvice never runs. Without a
        // custom AuthenticationEntryPoint the caller would get Spring Boot's default error
        // page shape instead of the format every other error uses.
        mockMvc.perform(get("/api/v1/protected-probe"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/unauthenticated"))
            .andExpect(jsonPath("$.title").value("Unauthenticated"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty)
            .andExpect(noInternalsLeaked())
    }

    @Test
    fun `an expired or forged bearer token is treated as no token at all`() {
        listOf(
            "not-a-jwt",
            // A well-formed HS256 JWT signed with the wrong key.
            JwtService(JwtProperties(secret = "a-completely-different-signing-secret"))
                .issueAccessToken(UUID.randomUUID()).token,
        ).forEach { token ->
            mockMvc.perform(get("/api/v1/protected-probe").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }

    @Test
    fun `an unknown refresh token is rejected exactly like a revoked one`() {
        givenFirebaseAccepts(phone)
        val session = signIn()

        val unknown = responseBodyOf(
            postJson("/api/v1/auth/refresh", mapOf("refreshToken" to "clearly-not-a-real-token"))
                .andExpect(status().isUnauthorized)
                .andReturn(),
        )

        postJson("/api/v1/auth/logout", mapOf("refreshToken" to session["refreshToken"]))
            .andExpect(status().isNoContent)
        val revoked = responseBodyOf(
            postJson("/api/v1/auth/refresh", mapOf("refreshToken" to session["refreshToken"]))
                .andExpect(status().isUnauthorized)
                .andReturn(),
        )

        // Byte-identical apart from the timestamp: the API gives away nothing about whether a
        // token ever existed.
        assertEquals(
            unknown.filterKeys { it != "timestamp" },
            revoked.filterKeys { it != "timestamp" },
        )
    }

    @Test
    fun `logging out an unknown token still answers 204`() {
        postJson("/api/v1/auth/logout", mapOf("refreshToken" to "never-issued-by-us"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `the auth endpoints are POST-only`() {
        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
    }

    // ---------------------------------------------------------------- rate limiting

    /**
     * Exercises the per-phone dimension end to end: [AuthService] throws
     * [RateLimitExceededException] after the default ten attempts for one phone number, and
     * [com.crichere.backend.common.GlobalExceptionHandler] turns it into a 429 problem detail.
     * The default `crichere.rate-limit.phone-capacity` (10, see `application.yml`) is used
     * as-is rather than overridden, so this doubles as a check that the configured default is
     * actually wired through to [AuthRateLimiter].
     */
    @Test
    fun `exceeding the per-phone rate limit returns a 429 problem detail`() {
        givenFirebaseAccepts(phone)

        repeat(10) {
            postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
                .andExpect(status().isOk)
        }

        postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isTooManyRequests)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/rate-limit-exceeded"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber)
            .andExpect(noInternalsLeaked())
    }

    /**
     * Exercises the per-IP dimension, which is enforced entirely inside [AuthRateLimitFilter]
     * -- before the request ever reaches [AuthController] or [AuthService]. A distinct phone
     * number is used on every call so the per-phone bucket (capacity 10) never trips; only the
     * per-IP bucket (capacity 20, all calls share `MockMvc`'s default remote address) can be
     * responsible for the rejection this test expects.
     */
    @Test
    fun `exceeding the per-IP rate limit returns a 429 problem detail from the filter`() {
        repeat(20) {
            givenFirebaseAccepts(uniquePhone())
            postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
                .andExpect(status().isOk)
        }

        givenFirebaseAccepts(uniquePhone())
        postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isTooManyRequests)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.type").value("https://api.crichere.app/problems/rate-limit-exceeded"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(noInternalsLeaked())
    }

    // ---------------------------------------------------------------- helpers

    private fun givenFirebaseAccepts(phoneNumber: String) {
        every { firebaseTokenVerifier.verify(any()) } returns
            VerifiedFirebaseToken(uid = "firebase-uid-$phoneNumber", phoneNumber = phoneNumber)
    }

    private fun signIn(): Map<String, Any?> =
        postJson("/api/v1/auth/session", mapOf("idToken" to "a-valid-firebase-id-token"))
            .andExpect(status().isOk)
            .andReturn()
            .body()

    private fun postJson(path: String, body: Map<String, Any?>) =
        mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun MvcResult.body(): Map<String, Any?> = responseBodyOf(this)

    @Suppress("UNCHECKED_CAST")
    private fun responseBodyOf(result: MvcResult): Map<String, Any?> =
        objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any?>

    /**
     * Asserts an error body carries nothing an attacker could use: no stack frames, no SQL, no
     * package or class names.
     */
    private fun noInternalsLeaked() = org.springframework.test.web.servlet.ResultMatcher { result ->
        val body = result.response.contentAsString
        listOf(
            "com.crichere", "org.springframework", "java.lang", "jakarta.",
            "Exception", "at ", "select ", "insert into", "Caused by",
        ).forEach { needle ->
            assertFalse(body.contains(needle, ignoreCase = true), "error body leaked '$needle': $body")
        }
    }

    private companion object {
        /** E.164-shaped and unique per test, so tests are independent of each other's state. */
        fun uniquePhone(): String =
            "+9199" + UUID.randomUUID().mostSignificantBits.toString().filter { it.isDigit() }.take(8).padEnd(8, '7')
    }
}
