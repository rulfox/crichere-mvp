package com.crichere.backend.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class MediaUrlPolicyTest {

    private val policy = MediaUrlPolicy(AwsProperties(AwsProperties.S3Properties(bucket = "crichere-media-prod", region = "ap-south-1")))
    private val host = "https://crichere-media-prod.s3.ap-south-1.amazonaws.com/"
    private val userId = UUID.randomUUID()
    private val leagueId = UUID.randomUUID()

    @Test
    fun `the caller's own upload is accepted, with or without the cache-busting query`() {
        assertDoesNotThrow { policy.requireProfilePhoto("${host}users/$userId/profile.jpg?v=abc123", userId) }
        assertDoesNotThrow { policy.requireProfilePhoto("${host}users/$userId/profile.jpg", userId) }
        assertDoesNotThrow { policy.requireProfilePhoto(null, userId) }
    }

    @Test
    fun `a URL on another host is rejected -- it would let that host log every viewer's IP`() {
        assertThrows<MediaUrlNotAllowedException> { policy.requireProfilePhoto("https://tracker.example/users/$userId/profile.jpg", userId) }
        // Our host as a subdomain or path of theirs is still theirs.
        assertThrows<MediaUrlNotAllowedException> {
            policy.requireProfilePhoto("https://crichere-media-prod.s3.ap-south-1.amazonaws.com.evil.example/users/$userId/profile.jpg", userId)
        }
        assertThrows<MediaUrlNotAllowedException> { policy.requireProfilePhoto("https://evil.example/?u=${host}users/$userId/profile.jpg", userId) }
    }

    @Test
    fun `another user's object in our bucket is rejected`() {
        assertThrows<MediaUrlNotAllowedException> { policy.requireProfilePhoto("${host}users/${UUID.randomUUID()}/profile.jpg", userId) }
        assertThrows<MediaUrlNotAllowedException> { policy.requireProfilePhoto("${host}users/$userId/profile.jpg#x", userId) }
    }

    @Test
    fun `league images are bound to the league on update, and must be a league image on create`() {
        assertDoesNotThrow { policy.requireLeagueLogo("${host}leagues/$leagueId/logo.jpg?v=1", leagueId) }
        assertDoesNotThrow { policy.requireLeagueBanner("${host}leagues/$leagueId/banner.jpg", leagueId) }
        assertThrows<MediaUrlNotAllowedException> { policy.requireLeagueLogo("${host}leagues/$leagueId/banner.jpg", leagueId) }
        assertThrows<MediaUrlNotAllowedException> { policy.requireLeagueLogo("${host}leagues/${UUID.randomUUID()}/logo.jpg", leagueId) }

        assertDoesNotThrow { policy.requireAnyLeagueImage("${host}leagues/$leagueId/logo.jpg") }
        assertThrows<MediaUrlNotAllowedException> { policy.requireAnyLeagueImage("${host}users/$userId/profile.jpg") }
    }

    @Test
    fun `franchise logos and payment screenshots must be the caller's own keys in this league`() {
        assertDoesNotThrow { policy.requirePendingFranchiseLogo("${host}leagues/$leagueId/franchise-logos/$userId-${UUID.randomUUID()}.jpg?v=2", leagueId, userId) }
        assertThrows<MediaUrlNotAllowedException> {
            policy.requirePendingFranchiseLogo("${host}leagues/$leagueId/franchise-logos/${UUID.randomUUID()}-x.jpg", leagueId, userId)
        }
        assertThrows<MediaUrlNotAllowedException> {
            policy.requirePendingFranchiseLogo("${host}leagues/$leagueId/franchise-logos/$userId-/../../payments/x.jpg", leagueId, userId)
        }

        assertDoesNotThrow { policy.requirePaymentScreenshot("${host}leagues/$leagueId/payments/$userId.jpg?v=3", leagueId, userId) }
        assertThrows<MediaUrlNotAllowedException> { policy.requirePaymentScreenshot("https://tracker.example/proof.jpg", leagueId, userId) }
    }

    @Test
    fun `with no bucket configured (dev, CI) nothing is checked`() {
        val unconfigured = MediaUrlPolicy(AwsProperties())
        assertDoesNotThrow { unconfigured.requireProfilePhoto("https://example.com/p.jpg", userId) }
    }
}
