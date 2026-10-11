package com.crichere.backend.common

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Checks that an image URL a client asks us to store points at our own bucket, at the key that our
 * upload endpoint issued for it (docs/PHASE17.md, security review 2026-10-11).
 *
 * Photos and logos are loaded by every viewer of a roster, auction or league page. If any
 * `https://` URL were accepted, a user could point their photo at a server they run and collect the
 * IP address of everyone who opens a list they're in. Binding each field to its upload key also
 * stops one user reusing another's object.
 *
 * Each URL keeps the `?v=` query the apps add for cache busting (`PhotoUpload.kt`), so only the part
 * before `?` is compared.
 *
 * With no bucket configured (dev/CI, see [AwsProperties]) nothing can be uploaded anyway, so the
 * check is skipped and the request DTO's `https://` rule is all that applies.
 */
@Component
class MediaUrlPolicy(private val properties: AwsProperties) {

    /** `users/{userId}/profile.jpg`, see [PhotoUploadService.createUploadUrl]. */
    fun requireProfilePhoto(url: String?, userId: UUID) = require(url) { it == "users/$userId/profile.jpg" }

    /** On update: this league's own logo key. */
    fun requireLeagueLogo(url: String?, leagueId: UUID) = require(url) { it == "leagues/$leagueId/logo.jpg" }

    /** On update: this league's own banner key. */
    fun requireLeagueBanner(url: String?, leagueId: UUID) = require(url) { it == "leagues/$leagueId/banner.jpg" }

    /**
     * On create the league has no id yet; the apps create first and upload after, so a create
     * normally carries no image. Anything sent must still be a league image in our bucket.
     */
    fun requireAnyLeagueImage(url: String?) = require(url) { LEAGUE_IMAGE.matches(it) }

    /** `leagues/{leagueId}/franchise-logos/{userId}-{random}.jpg`, see [PhotoUploadService.createPendingFranchiseLogoUploadUrl]. */
    fun requirePendingFranchiseLogo(url: String?, leagueId: UUID, userId: UUID) =
        // The exact shape the endpoint issues (random part = a UUID): no room for `%2e%2e`, `\` or other
        // path tricks that a browser would resolve to a different object.
        require(url) { Regex("^leagues/$leagueId/franchise-logos/$userId-$UUID_PATTERN\\.jpg$").matches(it) }

    /** The caller's own payment-proof key in this league, see [PhotoUploadService.paymentScreenshotKey]. */
    fun requirePaymentScreenshot(url: String?, leagueId: UUID, userId: UUID) =
        require(url) { it == PhotoUploadService.paymentScreenshotKey(leagueId, userId) }

    private fun require(url: String?, keyAllowed: (String) -> Boolean) {
        if (url == null) return
        val bucket = properties.s3.bucket
        val region = properties.s3.region
        if (bucket.isBlank() || region.isBlank()) return
        val prefix = "https://$bucket.s3.$region.amazonaws.com/"
        val withoutQuery = url.substringBefore('?')
        if (withoutQuery.contains('#') || !withoutQuery.startsWith(prefix) || !keyAllowed(withoutQuery.removePrefix(prefix))) {
            throw MediaUrlNotAllowedException()
        }
    }

    private companion object {
        const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val LEAGUE_IMAGE = Regex("^leagues/$UUID_PATTERN/(logo|banner)\\.jpg$")
    }
}

/** An image URL that isn't our upload for this field -- `400` (see [MediaUrlPolicy]). */
class MediaUrlNotAllowedException : RuntimeException("Image URL is not one of our uploads")
