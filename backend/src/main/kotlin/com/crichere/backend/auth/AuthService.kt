package com.crichere.backend.auth

import com.crichere.backend.profile.ProfileCompletionLookup
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The outcome of a successful sign-in or refresh: a short-lived access token, the rotated
 * refresh token, and the one piece of state the mobile app needs to decide where to send the
 * user next.
 */
data class AuthResult(
    val userId: UUID,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val profileComplete: Boolean,
)

/**
 * The whole session lifecycle: sign in with a Firebase ID token, rotate the session, end it.
 *
 * The backend never sends an OTP. The mobile client completes the phone-OTP flow directly
 * against Firebase, and arrives here holding a Firebase ID token; all this service does is
 * decide whether to trust it and, if so, mint our own credentials.
 */
@Service
class AuthService(
    private val firebaseTokenVerifier: FirebaseTokenVerifier,
    private val phoneCryptoService: PhoneCryptoService,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val profileCompletionLookup: ProfileCompletionLookup,
    private val rateLimiter: AuthRateLimiter,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Signs a user in.
     *
     * Deliberately **not** `@Transactional` as a whole. Find-or-create races against the
     * `phone_lookup_hash` unique index when the same number signs in twice concurrently for
     * the very first time; recovering from that means letting the failed insert roll back and
     * re-reading, which is only possible if the insert had its own transaction. Inside one
     * long transaction the constraint violation would poison it and the retry would fail too.
     *
     * The cost of dropping the outer transaction is that user creation and refresh-token
     * insertion are not atomic. That is harmless in this direction: a user row without a
     * refresh token is exactly the state of someone who has never signed in, and the next
     * attempt finds and reuses it.
     */
    fun verifySession(idToken: String): AuthResult {
        val verified = firebaseTokenVerifier.verify(idToken)

        val lookupHash = phoneCryptoService.hmacLookupHash(verified.phoneNumber)

        // Phone-dimension rate limit. Applied here rather than in the servlet filter because
        // this is the first moment the phone number is known *and trustworthy* -- see
        // AuthRateLimiter's documentation.
        rateLimiter.tryConsumeForPhone(lookupHash)?.let { retryAfter ->
            throw RateLimitExceededException(retryAfter)
        }

        return signInVerifiedPhone(lookupHash, verified.phoneNumber)
    }

    /**
     * Mints a session for a phone number some provider has already proven the caller owns.
     * Shared by the Firebase path ([verifySession]) and the backend-driven OTP path
     * ([OtpAuthService]) so account resolution and session issuance cannot drift apart.
     *
     * @param lookupHash `PhoneCryptoService.hmacLookupHash(phoneNumber)`, passed in because both
     *   callers have already computed it.
     */
    fun signInVerifiedPhone(lookupHash: String, phoneNumber: String): AuthResult {
        val user = findOrCreateUser(lookupHash, phoneNumber)
        val userId = requireNotNull(user.id) { "persisted user must have an id" }

        return issueSession(userId)
    }

    /**
     * Exchanges a refresh token for a new pair, rotating the old one out.
     *
     * Rotation is the point: the presented token is revoked in the same transaction that
     * issues its replacement, so a token can be spent exactly once. If it is stolen and
     * replayed, at most one of the two holders gets a session and the other's next attempt
     * fails -- which is a detectable symptom rather than a silent shared session.
     *
     * `@Transactional` here, unlike [verifySession], because revoking the old row and
     * inserting the new one must not come apart: half of that would either strand the user or
     * leave two live tokens.
     */
    @Transactional
    fun refresh(rawRefreshToken: String): AuthResult {
        val existing = refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
            ?: throw InvalidRefreshTokenException()

        val now = clock.instant()
        // One flat failure for "revoked", "expired" and "unknown" alike -- see
        // AuthenticationFailedException.
        if (existing.revokedAt != null) throw InvalidRefreshTokenException()
        if (!existing.expiresAt.isAfter(now)) throw InvalidRefreshTokenException()

        existing.revokedAt = now
        refreshTokenRepository.save(existing)

        return issueSession(existing.userId)
    }

    /**
     * Ends a session by revoking the refresh token.
     *
     * Returns normally whether or not the token existed, was already revoked, or was pure
     * noise. The caller gets the same empty 204 in every case, so logout cannot be used to
     * probe which tokens are real. (An unknown token costs one indexed lookup and a valid one
     * costs a lookup plus an update; that difference is not a usable oracle, because reaching
     * the slower path already requires possessing a valid 256-bit token.)
     */
    @Transactional
    fun logout(rawRefreshToken: String) {
        val existing = refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
        if (existing != null && existing.revokedAt == null) {
            existing.revokedAt = clock.instant()
            refreshTokenRepository.save(existing)
        }
    }

    /**
     * Resolves a verified phone number to an account, creating one on first sight.
     *
     * Idempotent by construction: the lookup hash is deterministic and uniquely indexed, so
     * the same number always lands on the same row and can never produce a second account.
     * The `catch` covers the narrow race where two first-ever sign-ins for one number overlap
     * -- the loser re-reads the row the winner just inserted instead of failing the request.
     */
    private fun findOrCreateUser(lookupHash: String, phoneNumber: String): UserEntity {
        userRepository.findByPhoneLookupHash(lookupHash)?.let { return it }

        return try {
            userRepository.save(
                UserEntity(
                    phoneLookupHash = lookupHash,
                    phoneEncrypted = phoneCryptoService.encryptToString(phoneNumber),
                    createdAt = clock.instant(),
                ),
            ).also { log.info("Registered new user {}", it.id) }
        } catch (e: DataIntegrityViolationException) {
            userRepository.findByPhoneLookupHash(lookupHash) ?: throw e
        }
    }

    /** Mints a token pair for [userId] and records the refresh token's hash. */
    private fun issueSession(userId: UUID): AuthResult {
        val access = jwtService.issueAccessToken(userId)
        val refresh = jwtService.issueRefreshToken()

        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId = userId,
                // Only the hash is ever persisted; the raw token below leaves in the response
                // body and is never written down.
                tokenHash = refresh.tokenHash,
                issuedAt = clock.instant(),
                expiresAt = refresh.expiresAt,
            ),
        )

        return AuthResult(
            userId = userId,
            accessToken = access.token,
            accessTokenExpiresAt = access.expiresAt,
            refreshToken = refresh.rawToken,
            profileComplete = profileCompletionLookup.isComplete(userId),
        )
    }
}
