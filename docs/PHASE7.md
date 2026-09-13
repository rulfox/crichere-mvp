# Phase 7 — Co-Organizer Role Delegation

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and
[PHASE5.md](PHASE5.md) for the deferred decision this phase resolves.

**Last updated:** 2026-09-13
**Status:** implemented (backend + Android, tested and manually verified on-device). Backend
`./gradlew test` (unit + Testcontainers integration) and mobile `:shared:testDebugUnitTest` are
green; `:androidApp:assembleDebug` succeeds. On-device: granted the real Test Player account
co-organizer on a live test league, confirmed it immediately gained every organizer-only action
(Edit league, Auction settings, Manage co-organizers, Mark completed), self-revoked, confirmed
access was gone on the very next screen load with no re-login. iOS `ManageRolesView.swift` is
authored but unwired, per the standing iOS posture (see docs/PHASE2.md Section 5).

---

## 1. Overview

Today a league has exactly one person who can ever act as its organizer — `LeagueEntity.organizerUserId`,
a single, permanent column, checked at every organizer-only action across the app. `PHASE5.md`
explicitly deferred fixing this: *"Auctioneer is always the league organizer... role delegation (a
distinct Auctioneer role, and/or co-organizers) is planned for a later phase once a real need shows
up."* This phase is that later phase — it lets an organizer delegate full organizer authority to
other people, by phone number, so running a league doesn't depend on one person always being
available.

---

## 2. Features

- The organizer looks up another registered Crichere user by phone number, and grants them
  **co-organizer** access to a specific league
- A co-organizer can do everything the organizer can do, for that league: edit league details,
  manage awards, remove/approve-leave for players and franchises, configure and run the auction
  (start/next player/sold/unsold/undo/toggle-exceed-purse/end), everything
- More than one co-organizer can hold the role on the same league at once
- The organizer (or any co-organizer) can revoke a co-organizer's access at any time, including
  while an auction is `IN_PROGRESS` — access is removed immediately, on that person's very next
  request
- A co-organizer can also step down (revoke their own access)
- The original organizer's own standing is permanent — nothing in this feature can ever revoke or
  reassign who the "real" organizer is
- Everyone who can already see a league's public detail page (organizer, franchise owners, anyone)
  can see who its co-organizers are, same as they can already see the organizer's identity

---

## 3. Decisions Made

| Decision | Rationale |
|---|---|
| **Full co-organizer, not a narrower "Auctioneer" role** | Confirmed explicitly (2026-09-13). A grant satisfies the app's one shared `requireOrganizer` check everywhere, uniformly, rather than a second narrower check threaded through only the auction-control endpoints. This turned out to be the *simpler* build of the two options that were on the table, not just the more powerful one -- it changes what `requireOrganizer` itself means, once, instead of adding a parallel check to a subset of call sites. |
| **Eligibility: any registered user, looked up by phone number** | Confirmed explicitly (2026-09-13) -- not restricted to people already in the league's roster. Genuinely new scope: no user-lookup-by-phone endpoint existed anywhere in this app before this phase. Built on top of the existing phone-hash lookup mechanism auth/OTP already uses (see Security), not a new phone-matching scheme. |
| **Multiple co-organizers, via a proper `league_roles` table, not a single column** | Confirmed explicitly (2026-09-13). Mirrors this codebase's existing `league_players`/`league_franchises` partial-unique-active-row pattern. The `role` column is a plain string so a genuinely different role type can ship later without another schema reshape -- only `CO_ORGANIZER` exists this phase. |
| **Revocable anytime, including mid-auction, organizer's judgment** | Confirmed explicitly (2026-09-13). Matches Phase 5's existing "no fixed process, the organizer decides" posture (sold/unsold/undo/toggle-exceed-purse are all the same philosophy). Grant is equally unrestricted by auction status -- an organizer stepping away mid-auction and handing control to a co-organizer is a real use case this phase shouldn't block. |
| **A co-organizer can revoke their own grant** | Falls out of the design rather than needing special-casing: revoke is gated by "is this caller an organizer for this league," which a co-organizer satisfies for themselves too. |
| **The real organizer (`organizerUserId`) is permanent and untouchable by this feature** | It's a column on `leagues`, never a `league_roles` row -- nothing granted through this phase can revoke or reassign it. A co-organizer can grant or revoke *other* co-organizers (full delegate, symmetric -- not restricted to "only the person who granted it," since that's a permission model this phase doesn't need), but never the original organizer. |
| **`LeagueResponse.coOrganizers` is public** | Same posture as the rest of that response -- the organizer's identity and every franchise owner's name are already public; who's been delegated authority is no more sensitive. |
| **No consent step for the delegate** | Being granted co-organizer is imposed by whoever already organizes the league, not opted into by the delegate. Accepted as consistent with this app's existing "silent-first" posture (Phase 3's join/leave/approval notifications are the same shape -- no push infra exists, you find out next time you open the app) rather than a new gap -- see Security. |

---

## 4. Open Questions and Gaps

None outstanding for this phase's scope. The phone-lookup endpoint's residual PII-enumeration risk
is a known, deliberately-accepted tradeoff -- see Security, not an open question.

---

## 5. Pure Technical Things (as built)

### Data model

- New `league_roles` table (`V16__add_league_roles.sql`): `id`, `league_id`, `user_id`, `role`
  (`VARCHAR(20)`, only `'CO_ORGANIZER'` used this phase), `granted_by_user_id`, `granted_at`,
  `revoked_at` (nullable -- a revoked row is kept, not deleted, for an audit trail, same reasoning
  `auction_bids.reversed` already uses). Partial unique index on `(league_id, user_id, role) WHERE
  revoked_at IS NULL` -- at most one *active* grant of a given role per person per league, mirroring
  `league_players`/`league_franchises`'s existing active-row pattern.
- `LeagueAuthorization` (`backend/.../league/LeagueAuthorization.kt`) changed from two pure top-level
  functions to an injectable `@Component`, since checking a co-organizer grant needs a repository
  call where the old `organizerUserId`-only comparison didn't. `isOrganizer(league, callerId)`
  short-circuits on `league.organizerUserId == callerId` first, so the real organizer's own requests
  never touch `league_roles` at all -- the added query only runs for a caller who isn't the plain
  organizer.

### API surface (as built)

- `POST /api/v1/leagues/{id}/roles/lookup` -- organizer-only (organizer or active co-organizer),
  `{ phoneNumber }` in, `{ userId, name }` out on a match, 404 on a miss. Rate-limited to 10/hour per
  caller.
- `POST /api/v1/leagues/{id}/roles` -- organizer-only, `{ userId, role: "CO_ORGANIZER" }`, grants.
  Rejects granting to the league's own organizer (no-op) or a duplicate active grant (409).
- `DELETE /api/v1/leagues/{id}/roles/{roleId}` -- organizer-only, revokes.
- `GET /api/v1/leagues/{id}` -- `LeagueResponse` gains `coOrganizers: List<LeagueRoleResponse>`
  (`id`, `userId`, `name`, `grantedAt`), public like the rest of the response.
- Every one of the 20 pre-existing `requireOrganizer` call sites across `LeagueService`,
  `AuctionService`, `FranchiseService`, `PlayerService` now also accepts an active co-organizer,
  with zero change to their own call shape -- the widening happened entirely inside
  `LeagueAuthorization`.

### Security

Analysis done 2026-09-13.

**The phone-lookup endpoint is this phase's one genuinely new risk: a PII-enumeration surface** --
and it's worth being direct about what's actually mitigated versus what's an accepted residual risk,
rather than overclaiming. "Organizer-only" sounds like a real gate on who can attempt a lookup, but
it barely is one: creating a league has no barrier at all (any authenticated user can create one for
free, per Phase 2), so any registered user can become an "organizer" in one extra API call. The
defenses that actually hold:
- **Volume is capped per *user*, not per *league*.** `ContentRateLimiter` buckets are keyed on
  `userId`, so creating more throwaway leagues doesn't grant more lookup quota -- the trivial
  self-organizer path above doesn't defeat rate limiting. Capacity is 10/hour, well below every other
  bucket in that file (20-300), since this is a PII-lookup surface, not a content-creation one.
- **Exact match only.** Reuses `PhoneCryptoService`'s existing HMAC lookup-hash (the same mechanism
  auth/OTP already relies on) -- no fuzzy/partial search, so a caller can't probe "is there a user
  starting with +9198...", only "does this *exact* number exist."
- **Minimal disclosure on a hit** -- name only, no phone number echoed back, no other profile field.
- **What's not eliminated**: a rate-limited, exact-match "does this phone number belong to a
  registered Crichere user" oracle still exists for anyone willing to create a throwaway league --
  10 attempts/hour, indefinitely, is a real if slow enumeration channel. Accepted as a bounded,
  low-severity residual risk given this app's actual profile: a community cricket-league tool, not a
  high-value target, where the phone numbers in question typically already circulate inside the same
  league's own WhatsApp group in the real world. Fully eliminating it (genericizing every response so
  success/failure are indistinguishable, forgot-password-style) would meaningfully worsen the
  organizer's UX for a real feature -- catching a typo before handing someone unrelated real access
  -- for a threat this app doesn't currently face. Revisit if abuse actually shows up, same
  "don't build for a hypothetical" bias this project already applies elsewhere.

**No consent step for the delegate.** Unlike joining a league or claiming a franchise (both
self-initiated), being granted co-organizer is imposed by someone else. Consistent with this app's
existing posture, not a new gap: Phase 3 already ships "silent-first" for join/leave/approval (no
push infra exists), and the blast radius is bounded to the granting organizer's own league --
nothing here reaches into the delegate's account, profile, or any other league.

**Revocation needs no token invalidation.** The check queries `league_roles` fresh on every request
-- never cached in the JWT, which still carries only a bare `userId` (no new claim added, consistent
with `JwtAuthenticationFilter`'s existing "no roles in the token" stance). A revoked co-organizer
loses access on their very next request.

**Every hard block stays server-side**, same standing rule this codebase follows everywhere else --
the mobile client's organizer-or-co-organizer check is for UI gating only; `LeagueAuthorization` is
what actually decides, unconditionally, on every request.
