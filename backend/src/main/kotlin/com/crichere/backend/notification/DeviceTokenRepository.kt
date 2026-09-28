package com.crichere.backend.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [DeviceTokenEntity]. */
interface DeviceTokenRepository : JpaRepository<DeviceTokenEntity, UUID> {

    /** [FcmSender]'s fan-out target -- every device currently registered for a user. */
    fun findByUserId(userId: UUID): List<DeviceTokenEntity>

    /** The upsert-by-token lookup (see docs/PHASE8.md's Decisions Made). */
    fun findByToken(token: String): DeviceTokenEntity?

    /** [FcmSender]'s stale-token cleanup on a failed send -- system-initiated, not caller-scoped. */
    fun deleteByToken(token: String)

    /** The explicit unregister-on-logout endpoint -- scoped to the caller's own token, so nobody can unregister a token they don't own. */
    fun deleteByTokenAndUserId(token: String, userId: UUID)
}
