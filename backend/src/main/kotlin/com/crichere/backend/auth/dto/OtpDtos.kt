package com.crichere.backend.auth.dto

import com.crichere.backend.auth.OtpChallengeTicket
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Body of `POST /api/v1/auth/otp/send`. Format is validated by `PhoneNumberNormalizer`, not here; this only bounds size. */
data class OtpSendRequest(
    @field:NotBlank(message = "phoneNumber is required")
    @field:Size(max = 32, message = "phoneNumber is too long")
    val phoneNumber: String,
)

/** Body of `POST /api/v1/auth/otp/resend`. */
data class OtpResendRequest(
    @field:NotNull(message = "challengeId is required")
    val challengeId: UUID?,
)

/** Body of `POST /api/v1/auth/otp/verify`. Deliberately has no phone number: it comes from the challenge. */
data class OtpVerifyRequest(
    @field:NotNull(message = "challengeId is required")
    val challengeId: UUID?,
    @field:NotBlank(message = "code is required")
    @field:Pattern(regexp = "^[0-9]{4,8}$", message = "code must be 4 to 8 digits")
    val code: String,
)

/** Response of send and resend. [challengeId] is the client's only handle; the provider's request id never leaves the backend. */
data class OtpChallengeResponse(
    val challengeId: UUID,
    val expiresAt: Instant,
    val resendAvailableAt: Instant,
) {
    companion object {
        fun from(ticket: OtpChallengeTicket) =
            OtpChallengeResponse(ticket.challengeId, ticket.expiresAt, ticket.resendAvailableAt)
    }
}

/** Response of `GET /api/v1/auth/config`: which login mechanism the app should use. */
data class AuthConfigResponse(val otpProvider: String)
