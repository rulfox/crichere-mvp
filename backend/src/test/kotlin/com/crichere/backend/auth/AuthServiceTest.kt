package com.crichere.backend.auth

import com.crichere.backend.profile.ProfileCompletionLookup
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the session lifecycle. Every collaborator is mocked, including
 * [FirebaseTokenVerifier] -- no test anywhere in this codebase touches the real Firebase
 * Admin SDK, which is the entire reason that interface exists.
 */
class AuthServiceTest {

    private val now: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val firebaseTokenVerifier = mockk<FirebaseTokenVerifier>()
    private val userRepository = mockk<UserRepository>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val profileCompletionLookup = mockk<ProfileCompletionLookup>()

    // The crypto and JWT services are real: they are pure, already unit-tested, and mocking
    // them here would make the assertions about "the hash we store" meaningless.
    private val phoneCryptoService = PhoneCryptoService(PhoneCryptoProperties(secret = PHONE_SECRET))
    private val jwtService = JwtService(JwtProperties(secret = JWT_SECRET), clock)

    private lateinit var rateLimiter: AuthRateLimiter
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        rateLimiter = AuthRateLimiter(RateLimitProperties())
        authService = AuthService(
            firebaseTokenVerifier = firebaseTokenVerifier,
            phoneCryptoService = phoneCryptoService,
            jwtService = jwtService,
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            profileCompletionLookup = profileCompletionLookup,
            rateLimiter = rateLimiter,
            clock = clock,
        )
        every { profileCompletionLookup.isComplete(any()) } returns false
        every { refreshTokenRepository.save(any()) } answers { firstArg() }
    }

    // ---------------------------------------------------------------- sign-in

    @Test
    fun `a first-time phone number gets an account`() {
        givenVerifiedPhone(PHONE)
        every { userRepository.findByPhoneLookupHash(any()) } returns null
        val saved = slot<UserEntity>()
        every { userRepository.save(capture(saved)) } answers { firstArg<UserEntity>().also { it.id = USER_ID } }

        val result = authService.verifySession("firebase-id-token")

        assertEquals(USER_ID, result.userId)
        assertEquals(phoneCryptoService.hmacLookupHash(PHONE), saved.captured.phoneLookupHash)
        assertEquals(now, saved.captured.createdAt)
    }

    @Test
    fun `the stored phone number is encrypted, not plaintext`() {
        givenVerifiedPhone(PHONE)
        every { userRepository.findByPhoneLookupHash(any()) } returns null
        val saved = slot<UserEntity>()
        every { userRepository.save(capture(saved)) } answers { firstArg<UserEntity>().also { it.id = USER_ID } }

        authService.verifySession("firebase-id-token")

        assertFalse(saved.captured.phoneEncrypted.contains(PHONE), "the number must not be readable at rest")
        assertFalse(saved.captured.phoneLookupHash.contains(PHONE))
        assertEquals(PHONE, phoneCryptoService.decryptFromString(saved.captured.phoneEncrypted))
    }

    @Test
    fun `signing in again with the same number reuses the account and creates no duplicate`() {
        givenVerifiedPhone(PHONE)
        val existing = UserEntity(
            id = USER_ID,
            phoneLookupHash = phoneCryptoService.hmacLookupHash(PHONE),
            phoneEncrypted = phoneCryptoService.encryptToString(PHONE),
        )
        every { userRepository.findByPhoneLookupHash(existing.phoneLookupHash) } returns existing

        val first = authService.verifySession("firebase-id-token")
        val second = authService.verifySession("another-firebase-id-token")

        assertEquals(USER_ID, first.userId)
        assertEquals(USER_ID, second.userId)
        // The critical assertion: idempotent find-or-create never inserts a second user.
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `a concurrent first sign-in for the same number resolves to the winner's account`() {
        givenVerifiedPhone(PHONE)
        val lookupHash = phoneCryptoService.hmacLookupHash(PHONE)
        val winner = UserEntity(id = USER_ID, phoneLookupHash = lookupHash, phoneEncrypted = "x")
        // First lookup misses, the insert loses the race against the unique index, the retry
        // finds the row the other request just wrote.
        every { userRepository.findByPhoneLookupHash(lookupHash) } returnsMany listOf(null, winner)
        every { userRepository.save(any<UserEntity>()) } throws
            org.springframework.dao.DataIntegrityViolationException("unique violation")

        val result = authService.verifySession("firebase-id-token")

        assertEquals(USER_ID, result.userId)
    }

    @Test
    fun `sign-in issues a usable access token and persists only the refresh token's hash`() {
        givenExistingUser()
        val savedToken = slot<RefreshTokenEntity>()
        every { refreshTokenRepository.save(capture(savedToken)) } answers { firstArg() }

        val result = authService.verifySession("firebase-id-token")

        assertEquals(USER_ID, jwtService.parseAccessToken(result.accessToken))
        assertEquals(USER_ID, savedToken.captured.userId)
        assertNotEquals(result.refreshToken, savedToken.captured.tokenHash)
        assertEquals(jwtService.hashRefreshToken(result.refreshToken), savedToken.captured.tokenHash)
        assertEquals(now, savedToken.captured.issuedAt)
        assertEquals(now.plus(Duration.ofDays(30)), savedToken.captured.expiresAt)
        assertNull(savedToken.captured.revokedAt)
    }

    @Test
    fun `profileComplete comes from the profile feature, not from auth`() {
        givenExistingUser()
        every { profileCompletionLookup.isComplete(USER_ID) } returns true

        assertTrue(authService.verifySession("firebase-id-token").profileComplete)

        every { profileCompletionLookup.isComplete(USER_ID) } returns false
        assertFalse(authService.verifySession("firebase-id-token").profileComplete)
    }

    @Test
    fun `a rejected Firebase token never reaches the database`() {
        every { firebaseTokenVerifier.verify(any()) } throws InvalidFirebaseIdTokenException()

        assertThrows<InvalidFirebaseIdTokenException> { authService.verifySession("bad-token") }

        verify(exactly = 0) { userRepository.findByPhoneLookupHash(any()) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `the per-phone rate limit trips after the configured number of attempts`() {
        givenExistingUser()
        rateLimiter = AuthRateLimiter(RateLimitProperties(phoneCapacity = 3, ipCapacity = 1_000))
        authService = rebuiltWith(rateLimiter)

        repeat(3) { authService.verifySession("firebase-id-token") }

        assertThrows<RateLimitExceededException> { authService.verifySession("firebase-id-token") }
    }

    @Test
    fun `the per-phone rate limit is per phone number, not global`() {
        rateLimiter = AuthRateLimiter(RateLimitProperties(phoneCapacity = 1, ipCapacity = 1_000))
        authService = rebuiltWith(rateLimiter)
        every { userRepository.findByPhoneLookupHash(any()) } answers {
            UserEntity(id = USER_ID, phoneLookupHash = firstArg(), phoneEncrypted = "x")
        }

        givenVerifiedPhone(PHONE)
        authService.verifySession("token")
        givenVerifiedPhone(OTHER_PHONE)
        // A different number has its own bucket and must not be blocked by the first one.
        authService.verifySession("token")

        givenVerifiedPhone(PHONE)
        assertThrows<RateLimitExceededException> { authService.verifySession("token") }
    }

    // ---------------------------------------------------------------- refresh

    @Test
    fun `refresh rotates - the presented token is revoked and a new one is stored`() {
        val raw = "raw-refresh-token"
        val stored = storedRefreshToken(raw)
        every { refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(raw)) } returns stored
        val saves = mutableListOf<RefreshTokenEntity>()
        every { refreshTokenRepository.save(capture(saves)) } answers { firstArg() }

        val result = authService.refresh(raw)

        // Old row revoked...
        assertEquals(now, stored.revokedAt)
        // ...a genuinely different token handed back...
        assertNotEquals(raw, result.refreshToken)
        // ...and its hash persisted as a fresh, unrevoked row.
        val inserted = saves.last()
        assertEquals(jwtService.hashRefreshToken(result.refreshToken), inserted.tokenHash)
        assertNull(inserted.revokedAt)
        assertEquals(USER_ID, inserted.userId)
        assertEquals(USER_ID, jwtService.parseAccessToken(result.accessToken))
    }

    @Test
    fun `refresh reports profileComplete too`() {
        val raw = "raw-refresh-token"
        every { refreshTokenRepository.findByTokenHash(any()) } returns storedRefreshToken(raw)
        every { profileCompletionLookup.isComplete(USER_ID) } returns true

        assertTrue(authService.refresh(raw).profileComplete)
    }

    @Test
    fun `an already-revoked refresh token is rejected`() {
        val raw = "raw-refresh-token"
        val revoked = storedRefreshToken(raw).apply { revokedAt = now.minusSeconds(60) }
        every { refreshTokenRepository.findByTokenHash(any()) } returns revoked

        assertThrows<InvalidRefreshTokenException> { authService.refresh(raw) }
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `an expired refresh token is rejected`() {
        val raw = "raw-refresh-token"
        val expired = storedRefreshToken(raw).apply { expiresAt = now.minusSeconds(1) }
        every { refreshTokenRepository.findByTokenHash(any()) } returns expired

        assertThrows<InvalidRefreshTokenException> { authService.refresh(raw) }
    }

    @Test
    fun `an unknown refresh token is rejected with the same error as a revoked one`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        // Same exception type as the revoked/expired cases, so the API cannot be used to
        // distinguish "no such token" from "token you no longer own".
        assertThrows<InvalidRefreshTokenException> { authService.refresh("never-issued") }
    }

    @Test
    fun `refresh looks the token up by hash, never by its raw value`() {
        val raw = "raw-refresh-token"
        val queried = slot<String>()
        every { refreshTokenRepository.findByTokenHash(capture(queried)) } returns storedRefreshToken(raw)

        authService.refresh(raw)

        assertNotEquals(raw, queried.captured)
        assertEquals(jwtService.hashRefreshToken(raw), queried.captured)
    }

    // ---------------------------------------------------------------- logout

    @Test
    fun `logout revokes the token`() {
        val raw = "raw-refresh-token"
        val stored = storedRefreshToken(raw)
        every { refreshTokenRepository.findByTokenHash(any()) } returns stored

        authService.logout(raw)

        assertEquals(now, stored.revokedAt)
        verify(exactly = 1) { refreshTokenRepository.save(stored) }
    }

    @Test
    fun `logging out an unknown token succeeds quietly`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        // No exception: a caller must not be able to tell a real token from a made-up one by
        // logging it out.
        authService.logout("never-issued")

        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `logging out twice does not move the revocation timestamp`() {
        val raw = "raw-refresh-token"
        val alreadyRevoked = storedRefreshToken(raw).apply { revokedAt = now.minusSeconds(600) }
        every { refreshTokenRepository.findByTokenHash(any()) } returns alreadyRevoked

        authService.logout(raw)

        assertEquals(now.minusSeconds(600), alreadyRevoked.revokedAt, "the original revocation time survives")
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `a refresh token revoked by logout can no longer be exchanged`() {
        val raw = "raw-refresh-token"
        val stored = storedRefreshToken(raw)
        every { refreshTokenRepository.findByTokenHash(any()) } returns stored

        authService.logout(raw)

        assertThrows<InvalidRefreshTokenException> { authService.refresh(raw) }
    }

    // ---------------------------------------------------------------- helpers

    private fun rebuiltWith(limiter: AuthRateLimiter) = AuthService(
        firebaseTokenVerifier = firebaseTokenVerifier,
        phoneCryptoService = phoneCryptoService,
        jwtService = jwtService,
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        profileCompletionLookup = profileCompletionLookup,
        rateLimiter = limiter,
        clock = clock,
    )

    private fun givenVerifiedPhone(phone: String) {
        every { firebaseTokenVerifier.verify(any()) } returns VerifiedFirebaseToken(uid = "firebase-uid", phoneNumber = phone)
    }

    private fun givenExistingUser() {
        givenVerifiedPhone(PHONE)
        every { userRepository.findByPhoneLookupHash(any()) } returns
            UserEntity(
                id = USER_ID,
                phoneLookupHash = phoneCryptoService.hmacLookupHash(PHONE),
                phoneEncrypted = phoneCryptoService.encryptToString(PHONE),
            )
    }

    private fun storedRefreshToken(raw: String) = RefreshTokenEntity(
        id = UUID.randomUUID(),
        userId = USER_ID,
        tokenHash = jwtService.hashRefreshToken(raw),
        issuedAt = now.minusSeconds(3600),
        expiresAt = now.plus(Duration.ofDays(29)),
    )

    private companion object {
        val USER_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
        const val PHONE = "+919876543210"
        const val OTHER_PHONE = "+919876500000"
        const val PHONE_SECRET = "auth-service-test-phone-secret-aaaaa"
        const val JWT_SECRET = "auth-service-test-jwt-secret-aaaaaaa"
    }
}
