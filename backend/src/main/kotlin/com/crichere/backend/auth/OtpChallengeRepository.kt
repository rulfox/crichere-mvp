package com.crichere.backend.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Every state change on a challenge is a single conditional `UPDATE`, so the database -- not a
 * read-then-write in the service -- is what enforces "5 attempts", "single use" and "resend
 * cap". Each returns the number of rows changed; `0` means the guard did not hold.
 */
interface OtpChallengeRepository : JpaRepository<OtpChallengeEntity, UUID> {

    fun findFirstByPhoneLookupHashAndConsumedAtIsNullOrderByCreatedAtDesc(phoneLookupHash: String): OtpChallengeEntity?

    /**
     * Spends one verify attempt, and only if the challenge is still open, unexpired and under
     * [maxAttempts]. Called *before* the provider is asked to check the code, so parallel
     * guesses each pay for their attempt instead of racing past the cap.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OtpChallengeEntity c set c.verifyAttempts = c.verifyAttempts + 1
        where c.id = :id and c.consumedAt is null and c.expiresAt > :now and c.verifyAttempts < :maxAttempts
        """,
    )
    fun spendAttempt(
        @Param("id") id: UUID,
        @Param("now") now: Instant,
        @Param("maxAttempts") maxAttempts: Int,
    ): Int

    /** Marks the challenge used. `0` rows means someone else already consumed it. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OtpChallengeEntity c set c.consumedAt = :now where c.id = :id and c.consumedAt is null")
    fun consume(@Param("id") id: UUID, @Param("now") now: Instant): Int

    /**
     * Records a resend, only if the challenge is open, unexpired and under [maxResends].
     * Extends the expiry from the new send, since the provider issues a fresh code lifetime.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OtpChallengeEntity c set c.resends = c.resends + 1, c.lastSentAt = :now, c.expiresAt = :newExpiresAt
        where c.id = :id and c.consumedAt is null and c.expiresAt > :now and c.resends < :maxResends
        """,
    )
    fun recordResend(
        @Param("id") id: UUID,
        @Param("now") now: Instant,
        @Param("newExpiresAt") newExpiresAt: Instant,
        @Param("maxResends") maxResends: Int,
    ): Int

    /** Housekeeping: drop rows that can no longer matter. */
    @Transactional
    @Modifying
    @Query("delete from OtpChallengeEntity c where c.expiresAt < :cutoff")
    fun deleteExpiredBefore(@Param("cutoff") cutoff: Instant): Int
}
