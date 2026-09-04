package com.crichere.backend.league

/** No league matches the given id -- `GET`/`PUT`/`PATCH`/awards-CRUD on a nonexistent league id. */
class LeagueNotFoundException : RuntimeException("League not found")

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
