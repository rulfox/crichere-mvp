package com.crichere.backend.account

import java.time.Instant
import java.util.UUID

/** Why a league stops its organizer's (or a participant's) account from being deleted right now. */
enum class DeletionBlockReason {
    /** The league's auction is running and the user organizes it, plays in it or owns a franchise in it. Wait for it to end. */
    AUCTION_LIVE,

    /** The user organizes this not-yet-completed league and nobody could take it over. Add a co-organizer or mark it completed. */
    NO_CO_ORGANIZER,
}

data class DeletionBlocker(
    val leagueId: UUID,
    val leagueName: String,
    val reason: DeletionBlockReason,
)

/** Body of `GET /api/v1/me/deletion`: what (if anything) stands in the way, and how long the grace period is. */
data class AccountDeletionStatusResponse(
    val blockers: List<DeletionBlocker>,
    val gracePeriodDays: Long,
)

/** Body of `POST /api/v1/me/deletion`. [confirm] must be exactly `DELETE` -- the typed confirmation from the app's dialog. */
data class RequestAccountDeletionRequest(
    val confirm: String? = null,
)

/** Response of `POST /api/v1/me/deletion`: the account and its data are erased at [purgeAfter] unless the user signs in again before then. */
data class AccountDeletionScheduledResponse(
    val purgeAfter: Instant,
)

/** `409` -- see [DeletionBlockReason]. The response lists every blocking league so the app can show them all at once. */
class AccountDeletionBlockedException(val blockers: List<DeletionBlocker>) : RuntimeException("Account deletion is blocked")

/** `400` -- the request didn't carry the typed `DELETE` confirmation. */
class AccountDeletionNotConfirmedException : RuntimeException("Account deletion was not confirmed")

/**
 * `409` -- revoking this co-organizer would leave a league whose organizer is deleting their account
 * with nobody to hand it to, which would stall the purge (docs/PHASE16.md).
 */
class LastSuccessorRoleException : RuntimeException("This co-organizer is the league's only successor")
