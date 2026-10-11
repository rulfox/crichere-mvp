package com.crichere.backend.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Spring Data repository for [RefreshTokenEntity]. [findByTokenHash] is the lookup the auth
 * feature needs to validate an incoming refresh token (hashed client-side/server-side before
 * lookup) -- implemented in a later task.
 */
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    /** Ends every session a user has -- an account deletion request (docs/PHASE16.md). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    fun revokeAllForUser(@Param("userId") userId: UUID, @Param("now") now: Instant): Int

    /** Account purge: the revoked rows themselves are no longer worth keeping. */
    fun deleteByUserId(userId: UUID)
}
