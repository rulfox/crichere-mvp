package com.crichere.backend.me

import com.crichere.backend.moderation.ModerationNoticeResponse
import com.crichere.backend.moderation.ModerationNoticeService
import com.crichere.backend.me.dto.MyLeaguesResponse
import com.crichere.backend.me.dto.RegisterDeviceTokenRequest
import com.crichere.backend.me.dto.UnregisterDeviceTokenRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The current user's own cross-league standing -- see [MeService]. Authenticated (falls through to `.anyRequest().authenticated()`). */
@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val meService: MeService,
    private val moderationNoticeService: ModerationNoticeService,
) {

    @GetMapping("/leagues")
    fun getMyLeagues(@AuthenticationPrincipal userId: UUID): MyLeaguesResponse = meService.getMyLeagues(userId)

    /** Removed-image notices still to show (docs/PHASE18.md). */
    @GetMapping("/moderation-notices")
    fun moderationNotices(@AuthenticationPrincipal userId: UUID): List<ModerationNoticeResponse> =
        moderationNoticeService.pending(userId)

    @PostMapping("/moderation-notices/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dismissModerationNotice(@AuthenticationPrincipal userId: UUID, @PathVariable id: UUID) {
        moderationNoticeService.dismiss(userId, id)
    }

    /** Push notifications (docs/PHASE8.md) -- called after login and on FCM token refresh. */
    @PostMapping("/device-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun registerDeviceToken(@AuthenticationPrincipal userId: UUID, @Valid @RequestBody request: RegisterDeviceTokenRequest) {
        meService.registerDeviceToken(userId, requireNotNull(request.token), requireNotNull(request.platform))
    }

    /** Called before clearing local auth state on logout, so this device stops receiving push for the account it just left. */
    @PostMapping("/device-tokens/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregisterDeviceToken(@AuthenticationPrincipal userId: UUID, @Valid @RequestBody request: UnregisterDeviceTokenRequest) {
        meService.unregisterDeviceToken(userId, requireNotNull(request.token))
    }
}
