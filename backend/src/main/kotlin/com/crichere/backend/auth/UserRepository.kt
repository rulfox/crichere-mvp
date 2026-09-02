package com.crichere.backend.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data repository for [UserEntity]. [findByPhoneLookupHash] is the lookup the auth
 * feature needs to resolve an incoming OTP-verified phone number to an existing user (or
 * discover there isn't one, meaning first-time signup) -- implemented in a later task.
 */
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByPhoneLookupHash(phoneLookupHash: String): UserEntity?
}
