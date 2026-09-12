package com.crichere.backend.league

/** No league matches the given id -- `GET`/`PUT`/`PATCH`/awards-CRUD on a nonexistent league id. */
class LeagueNotFoundException : RuntimeException("League not found")

/**
 * No award matches the given id under the given league -- also thrown (not a different error)
 * when the award id is real but belongs to a *different* league, so a client can never learn
 * "that award id exists, just not here" by probing.
 */
class LeagueAwardNotFoundException : RuntimeException("Award not found")

/**
 * A create/edit request referenced a `groundId` that doesn't exist. Checked explicitly
 * (`GroundRepository.existsById`) rather than left to surface as a raw FK-violation `500` --
 * this is the one part of a league request a client could plausibly get wrong (a stale id from
 * a race with another client), so it deserves a clean `404`, not an opaque server error.
 */
class GroundNotFoundException : RuntimeException("Ground not found")

/**
 * The caller is not this league's organizer. Every mutating league endpoint (edit, complete,
 * awards CRUD, logo/banner presign) checks this -- see docs/PHASE2.md's Security Considerations
 * for why this is Phase 2's first genuinely new authorization surface (Phase 1's profile
 * mutations were always implicitly self-scoped, so no equivalent check ever existed before).
 */
sealed class LeagueAuthorizationException(message: String) : RuntimeException(message) {
    class NotOrganizerException : LeagueAuthorizationException("You are not the organizer of this league")
}

typealias NotOrganizerException = LeagueAuthorizationException.NotOrganizerException

/** A join/claim was attempted on a league that's already been marked completed. */
class LeagueCompletedException : RuntimeException("This league is completed")

/** [role] is `"player"` or `"franchise"` -- shared by both `PlayerService.join` and `FranchiseService.claim`. */
class LeagueCapacityFullException(val role: String) : RuntimeException("$role capacity is full")

/** A create/edit request set a franchise/player fee but left `organizerUpiId` blank -- see docs/PHASE3.md's Decisions Made. */
class OrganizerUpiRequiredException : RuntimeException("organizerUpiId is required when a fee is set")

/** A `PUT` edit tried to drop `playersRequired`/`franchisesRequired` below the current active count for [role]. */
class CapacityBelowActiveCountException(val role: String) : RuntimeException("$role capacity cannot be set below the current active count")

/** A `PUT` edit tried to change `playerFee`/`franchiseFee` for [role] while at least one active row already exists for it. */
class FeeLockedException(val role: String) : RuntimeException("$role fee is locked while active $role rows exist")

/** [role]'s fee is set on this league but the join/claim request didn't include a payment screenshot. */
class PaymentScreenshotRequiredException(val role: String) : RuntimeException("A payment screenshot is required to join/claim as $role")

/** An auction-settings save had `squadMin` greater than `squadMax` -- see docs/PHASE4.md. */
class SquadSizeInvalidException : RuntimeException("squad_min cannot be greater than squad_max")
