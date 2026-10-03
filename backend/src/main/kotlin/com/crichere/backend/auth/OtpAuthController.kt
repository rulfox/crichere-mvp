package com.crichere.backend.auth

import com.crichere.backend.auth.dto.AuthConfigResponse
import com.crichere.backend.auth.dto.AuthResponse
import com.crichere.backend.auth.dto.OtpChallengeResponse
import com.crichere.backend.auth.dto.OtpResendRequest
import com.crichere.backend.auth.dto.OtpSendRequest
import com.crichere.backend.auth.dto.OtpVerifyRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The backend-driven OTP endpoints (docs/PHASE12.md), live only while
 * `crichere.otp.provider=msg91`. `/auth/session` (Firebase) is untouched and always available.
 *
 * Per-IP limits are applied before this controller, in [AuthRateLimitFilter].
 */
@RestController
@RequestMapping("/api/v1/auth")
class OtpAuthController(
    private val otpAuthService: OtpAuthService,
    private val appCheckVerifier: AppCheckVerifier,
    private val otpProperties: OtpProperties,
) {

    /** Tells the app which login mechanism to use, so the provider can change without an app release. */
    @GetMapping("/config")
    fun config(): AuthConfigResponse = AuthConfigResponse(otpProvider = otpProperties.provider.wireName)

    @PostMapping("/otp/send")
    fun send(
        @Valid @RequestBody request: OtpSendRequest,
        @RequestHeader(APP_CHECK_HEADER, required = false) appCheckToken: String?,
    ): OtpChallengeResponse {
        otpAuthService.requireEnabled()
        requireAppCheck(appCheckToken)
        return OtpChallengeResponse.from(otpAuthService.requestOtp(request.phoneNumber))
    }

    @PostMapping("/otp/resend")
    fun resend(
        @Valid @RequestBody request: OtpResendRequest,
        @RequestHeader(APP_CHECK_HEADER, required = false) appCheckToken: String?,
    ): OtpChallengeResponse {
        otpAuthService.requireEnabled()
        requireAppCheck(appCheckToken)
        return OtpChallengeResponse.from(otpAuthService.resendOtp(requireNotNull(request.challengeId)))
    }

    @PostMapping("/otp/verify")
    fun verify(@Valid @RequestBody request: OtpVerifyRequest): AuthResponse =
        AuthResponse.from(otpAuthService.verifyOtp(requireNotNull(request.challengeId), request.code))

    private fun requireAppCheck(token: String?) {
        if (otpProperties.appCheckRequired && !appCheckVerifier.isValid(token)) throw AppCheckFailedException()
    }

    private companion object {
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
    }
}
