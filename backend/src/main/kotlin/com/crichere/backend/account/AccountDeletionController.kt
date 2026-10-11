package com.crichere.backend.account

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Account deletion (docs/PHASE16.md). Authenticated (falls through to `.anyRequest().authenticated()`).
 * There is no cancel endpoint: the request signs the user out everywhere, and signing in again is
 * the cancel.
 */
@RestController
@RequestMapping("/api/v1/me/deletion")
class AccountDeletionController(
    private val accountDeletionService: AccountDeletionService,
) {

    /** What would block a deletion right now, so the app can show it before asking for confirmation. */
    @GetMapping
    fun status(@AuthenticationPrincipal userId: UUID): AccountDeletionStatusResponse = accountDeletionService.status(userId)

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(
        @AuthenticationPrincipal userId: UUID,
        @RequestBody request: RequestAccountDeletionRequest,
    ): AccountDeletionScheduledResponse = accountDeletionService.request(userId, request.confirm)
}
