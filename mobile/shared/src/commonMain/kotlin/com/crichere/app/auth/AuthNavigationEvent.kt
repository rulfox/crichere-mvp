package com.crichere.app.auth

/**
 * The navigation contract [OtpVerifyViewModel] emits after a successful sign-in, and the one
 * forced-back-to-start case that belongs to this task (max wrong attempts). `NavigateToPhoneEntry`
 * is wired to a real destination by this task (the screen that emits it also exists here);
 * `NavigateToProfileSetup`/`NavigateToOwnProfile` are wired to placeholder/blank composables here
 * (marked `TODO(Task 7)`) since the real screens they point at are Task 7's job to build.
 */
sealed interface AuthNavigationEvent {
    /** `profileComplete == false` on the session-exchange response: onboarding isn't done yet. */
    data object NavigateToProfileSetup : AuthNavigationEvent

    /** `profileComplete == true`: the user already has a complete cricket-player profile. */
    data object NavigateToOwnProfile : AuthNavigationEvent

    /**
     * The 5th wrong OTP attempt (never the 6th -- see `OtpVerifyViewModel`'s
     * `MAX_WRONG_ATTEMPTS` handling) forces the user back to a fresh Phone Entry, per PHASE1.md's
     * "bounced back to request a fresh code" behavior. A stale `verificationId` cannot be reused
     * from here; a brand new `sendVerificationCode` call is required.
     */
    data object NavigateToPhoneEntry : AuthNavigationEvent
}
