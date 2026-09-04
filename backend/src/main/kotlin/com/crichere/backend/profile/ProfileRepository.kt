package com.crichere.backend.profile

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data repository for [ProfileEntity]. The primary key is the owning user's id, so
 * [JpaRepository.findById] doubles as "load this user's profile if it exists" -- no extra
 * query method needed for that.
 */
interface ProfileRepository : JpaRepository<ProfileEntity, UUID>
