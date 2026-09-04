package com.crichere.backend.profile

import com.crichere.backend.profile.dto.PhotoUploadUrlResponse
import com.crichere.backend.profile.dto.ProfileResponse
import com.crichere.backend.profile.dto.ProfileUpdateRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The three profile endpoints. All authenticated (via the JWT bearer filter -- these paths are
 * not in `SecurityConfig`'s `permitAll()` list) and all scoped to the caller's own profile:
 * [userId] always comes from `@AuthenticationPrincipal`, i.e. the JWT `sub` claim
 * [com.crichere.backend.auth.JwtAuthenticationFilter] already verified, never from a path or
 * body parameter a client could set to someone else's id.
 */
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(
    private val profileService: ProfileService,
    private val photoUploadService: PhotoUploadService,
) {

    /**
     * The authenticated user's profile, with `profileComplete` computed the same way
     * `/auth/session` and `/auth/refresh` compute it. `200` with every optional field `null`
     * for a user who has not created a profile row yet -- see [ProfileResponse]'s class doc.
     */
    @GetMapping("/me")
    fun getMyProfile(@AuthenticationPrincipal userId: UUID): ProfileResponse =
        profileService.getMyProfile(userId)

    /** Upsert. See [ProfileUpdateRequest] for the full-replace semantics and [ProfileService] for the role/bowling-style rule. */
    @PutMapping("/me")
    fun updateMyProfile(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: ProfileUpdateRequest,
    ): ProfileResponse = profileService.upsert(userId, request)

    /** A presigned S3 POST for the caller's own profile photo. See [PhotoUploadService]. */
    @PostMapping("/me/photo-upload-url")
    fun createPhotoUploadUrl(@AuthenticationPrincipal userId: UUID): PhotoUploadUrlResponse =
        photoUploadService.createUploadUrl(userId)
}
