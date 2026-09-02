package com.crichere.backend.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data repository for [RefreshTokenEntity]. [findByTokenHash] is the lookup the auth
 * feature needs to validate an incoming refresh token (hashed client-side/server-side before
 * lookup) -- implemented in a later task.
 */
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?
}
