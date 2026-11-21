package com.crichere.backend.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admin_sessions")
class AdminSessionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    val tokenHash: String,

    @Column(name = "email", nullable = false, updatable = false, length = 320)
    val email: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "user_agent", length = 300, updatable = false)
    val userAgent: String? = null,
)

interface AdminSessionRepository : JpaRepository<AdminSessionEntity, UUID> {
    fun findByTokenHash(tokenHash: String): AdminSessionEntity?

    @Query("select count(s) from AdminSessionEntity s where s.email = :email and s.revokedAt is null and s.expiresAt > :now")
    fun countLive(@Param("email") email: String, @Param("now") now: Instant): Long

    @Modifying
    @Query("update AdminSessionEntity s set s.revokedAt = :now where s.email = :email and s.revokedAt is null")
    fun revokeAllFor(@Param("email") email: String, @Param("now") now: Instant): Int
}
