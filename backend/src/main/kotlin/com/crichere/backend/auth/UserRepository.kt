package com.crichere.backend.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Spring Data repository for [UserEntity]. [findByPhoneLookupHash] is the lookup the auth
 * feature needs to resolve an incoming OTP-verified phone number to an existing user (or
 * discover there isn't one, meaning first-time signup) -- implemented in a later task.
 */
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByPhoneLookupHash(phoneLookupHash: String): UserEntity?

    /**
     * Cancels a pending deletion (sign-in during the grace period, docs/PHASE16.md). Conditional,
     * so it is a no-op for an account that isn't pending -- or one a purge already finished.
     * @return 1 if a pending deletion was cancelled.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.deletionRequestedAt = null where u.id = :userId and u.deletionRequestedAt is not null and u.deletedAt is null")
    fun cancelPendingDeletion(@Param("userId") userId: UUID): Int

    /** Accounts whose grace period ran out at or before [cutoff], oldest first -- the purge job's work list. */
    @Query("select u.id from UserEntity u where u.deletionRequestedAt <= :cutoff and u.deletedAt is null order by u.deletionRequestedAt")
    fun findDueForPurge(@Param("cutoff") cutoff: Instant): List<UUID>

    /**
     * Locks one due account for purging. `SKIP LOCKED` so two app instances never purge the same
     * account, and the conditions are re-checked under the lock in case a sign-in cancelled it.
     */
    @Query(
        value = "SELECT * FROM users WHERE id = :userId AND deletion_requested_at <= :cutoff AND deleted_at IS NULL FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun lockDueForPurge(@Param("userId") userId: UUID, @Param("cutoff") cutoff: Instant): UserEntity?
}
