# Phase 16 — Account deletion

**Last updated:** 2026-10-11
**Status:** backend and web built and tested (unit, integration, live against the local backend). The Claude Design
export is in (`Crichere Account Deletion.dc.html`, frames D1–D10). Section 8 covers the API gaps it needed and the
shared mobile layer, both built. Section 9 covers the Android screens, checked on the emulator, and the iOS screens,
written but not compiled.

---

## 1. Why / scope

Google Play requires every app that lets users create an account to offer account deletion in the app and to
give a web URL for it in the Data safety form. The App Store requires in-app deletion too. The privacy draft
(`docs/legal/PRIVACY-POLICY.md`) promises erasure. Before this phase there was no delete path at all.

A plain `DELETE FROM users` cannot work. Every FK to `users(id)` cascades (leagues, players, franchises, grounds,
follows, roles, tokens), so it would wipe other people's leagues and auction history. `league_roles.granted_by_user_id`
has no `ON DELETE` rule and would block the delete. A pure soft delete (`deleted_at` only) keeps the personal data,
which fails Play and the DPDP Act.

In scope: backend (request, grace period, purge, handover), the web `/delete-account` page, the Privacy and Terms
text on `/privacy` and `/terms`, docs. Out of scope this round: the mobile screens (design first).

## 2. Decisions made (owner, 2026-10-11)

| Decision | Why |
|---|---|
| **Tombstone, not a row delete.** Personal data is erased; the `users` row stays with `deleted_at` and both phone columns `NULL`. | Keeps other people's leagues, results and bids intact. The unique index on `phone_lookup_hash` allows many `NULL`s, so the number can sign up again as a new account. |
| **"Deleted user" comes from the backend.** The purge keeps the profile row with only `name = "Deleted user"`. | Every existing mapping (rosters, results, co-organizer lists, auction) already reads the profile name, so all clients show it with no release. Using "profile missing" as the signal was not possible: a player can join a league before finishing their profile. |
| **14-day grace period.** The request signs the user out everywhere (all refresh tokens revoked, push tokens deleted). Signing in again within 14 days cancels the deletion; the session response carries `accountRestored: true`. | Undoes mistakes and a request made by someone else holding the phone. No cancel endpoint is needed: signing in is the cancel. |
| **Typed confirmation ("DELETE"), no OTP re-check.** | OTP verify always signs in (no confirm-only mode) and prod has two OTP providers. The grace period plus restore-on-sign-in covers the risk at no SMS cost. |
| **Blocked while an auction the user organizes, plays in or owns a franchise in is `IN_PROGRESS`.** | Removing a bidder or player mid-auction breaks purse and squad maths. |
| **Organizer of a non-completed league needs a co-organizer.** At the purge the earliest-granted active co-organizer (not pending deletion themselves) becomes the organizer, gets a push, and the league carries `organizerHandoverAt` for a banner. No accept step. With no co-organizer the request is refused, listing those leagues. | Owner's choice: the league continues without a stuck invite. The new organizer inherits payment screenshots and fee duty; the push and banner tell them. |
| **Handover happens at the purge, not at the request.** | A user who restores during the grace period keeps their leagues untouched. |
| **The last eligible co-organizer of a leaving organizer cannot be revoked** (409 `LAST_SUCCESSOR`). | Otherwise the purge could never finish. |
| **Web: `/delete-account` explains the in-app steps and an email route.** No web OTP form. | Satisfies the Play deletion URL with no new auth surface on the web. Play's rule covers every app with accounts, not only games. |

## 3. What the purge does

Runs daily at 03:30 IST (`crichere.account-deletion.purge-cron`). For each account past its grace period:

1. Blocker pre-check (read only). Blocked: postpone, retry next run.
2. S3 deletes: `users/{id}/profile.jpg` and `leagues/{leagueId}/payments/{userId}.jpg` for every league the user
   ever had a player or franchise row in. Done before the database so nothing personal is left if the DB step
   fails (deleting a missing key succeeds, so the retry is harmless). A failed delete stops this account until
   the next run.
3. One transaction, holding `SELECT … FOR UPDATE SKIP LOCKED` on the user row (safe if two instances ever run):
   - re-check live auctions and find every successor first; any missing: postpone, nothing written;
   - revoke the successors' co-organizer rows and the user's own co-organizer rows;
   - player and franchise rows: payment screenshot URL cleared on all; rows in leagues still taking sign-ups
     (auction not started, not completed) are withdrawn (`removed_at`), rows in auctioned or completed leagues stay;
   - profile reduced to `name = "Deleted user"`; follows, device tokens, refresh tokens and OTP challenges deleted;
   - phone columns cleared, `deleted_at` set;
   - last, `organizer_user_id` moved to each successor (`organizer_handover_at` set).
4. After commit: push to each new organizer.

Kept: the tombstone row, grounds the user registered, league history in auctioned or completed leagues,
franchise names and logos in them.

## 4. API

| Endpoint | Result |
|---|---|
| `GET /api/v1/me/deletion` | `{blockers: [{leagueId, leagueName, leagueLogoUrl, reason}], gracePeriodDays: 14, erasesOn, handovers: [{leagueId, leagueName, leagueLogoUrl, newOrganizerUserId, newOrganizerName}]}`; reason `AUCTION_LIVE` or `NO_CO_ORGANIZER`; `erasesOn` is now + 14 days, or the original date while a request is pending |
| `POST /api/v1/me/deletion` `{confirm: "DELETE"}` | 202 `{purgeAfter}`; 400 `CONFIRMATION_REQUIRED`; 409 `ACCOUNT_DELETION_BLOCKED` with `blockers`; idempotent while pending; rate limit 5/hour |
| `POST /auth/session`, `/auth/otp/verify` | new field `accountRestored` |
| `GET /api/v1/leagues/{id}` | new fields `organizerHandoverAt`, `showHandoverNotice` (true only for the current organizer until dismissed); `deleted` on `players[]` and `coOrganizers[]`, `ownerDeleted` on `franchises[]` |
| `POST /api/v1/leagues/{id}/handover-notice/dismiss` | 204, organizer only (not co-organizers), idempotent; a later handover shows it again |
| `GET /api/v1/leagues/{id}/auction/results` | `deleted` on each won player |
| `DELETE /api/v1/leagues/{id}/roles/{roleId}` | new 409 `LAST_SUCCESSOR` |

Migration `V22__account_deletion.sql`: `users.deletion_requested_at`, `users.deleted_at`, phone columns nullable,
partial index for the purge scan, `leagues.organizer_handover_at`. Migration `V23__account_deletion_display.sql`:
`profiles.account_deleted` (set by the purge; every mapping already loads the profile, so the flag costs no extra
query and no name-text match) and `leagues.organizer_handover_seen_at`.

## 5. Security analysis

- **Who can delete:** only the signed-in user, for their own id (JWT principal). No id in the path or body.
- **No re-auth:** someone holding an unlocked phone can request deletion. Mitigated: nothing is erased for 14 days
  and the owner's next sign-in cancels it. Accepted by the owner.
- **Access tokens after the request:** refresh tokens are revoked at once, but an access token already issued stays
  valid until it expires (15 min); the JWT filter does not check the DB. Acceptable: the account is untouched
  during the grace period anyway.
- **Refresh racing the request (found by the commit security review, fixed 2026-10-11):** a refresh in flight when
  the request committed could write a new refresh token the bulk revoke never saw, keeping the session alive.
  Now `/auth/refresh` takes the same user-row lock as the request (`UserRepository.findByIdForUpdate`) and refuses
  an account that is pending deletion or purged, so no old token yields a new session. A sign-in racing the
  request can still get one access token (15 min); its first refresh is refused.
- **Data left after purge:** phone, profile details, photo, payment screenshots, sessions, push tokens, follows and
  OTP challenges are gone. The user id remains in league rows; it no longer maps to a person (no phone, no name).
  Payment screenshots of *other* users in a league the deleted user organized stay, under that league's retention.
- **Race with sign-in:** restore is a conditional update (`deleted_at IS NULL`), the purge re-checks
  `deletion_requested_at` under the row lock. A purged account has no phone hash, so sign-in creates a new account.
- **Handover consent:** the promoted co-organizer gets payer data without opting in (owner decision); push and
  banner tell them.
- **Rate limit:** 5 requests per user per hour (bounds a script hammering the blockers query).
- **Ops step (owner):** grant the backend IAM user `s3:DeleteObject` on `crichere-media-prod` for the `users/`
  and `leagues/*/payments/` prefixes. Until then every purge in production fails at the S3 step and retries
  daily (no data is lost, but nothing is erased either).
- **Single instance assumption:** rate limiting is in memory (as elsewhere); the purge itself is safe with
  several instances (`SKIP LOCKED`).

## 6. How to run / verify

- `cd backend && ./gradlew test`: 508 tests, all passing, including `AccountDeletionFlowIntegrationTest` (8) and
  `AccountDeletionServiceTest` (4).
- Live (2026-10-11, local backend + docker Postgres, fake OTP): blocker listed for a player in the running
  "E2E Bid League" auction; for a clean number: wrong confirm 400, request 202, refresh 401, sign-in
  `accountRestored: true`; request again, `deletion_requested_at` back-dated 15 days, the per-minute test cron
  (`CRICHERE_ACCOUNTDELETION_PURGECRON="0 * * * * *"`) purged it: phone NULL, profile "Deleted user", 0 refresh
  tokens; the same number then signed up as a new user id.
- Web: `npm test`, `npm run lint`, `npx playwright test` (legal spec covers `/delete-account`).
- Live, round 2 (2026-10-11, after the design-export API gaps): `node backend/e2e/account-deletion-live.mjs`
  against the local backend with the per-minute test cron, 18/18 checks pass:
  - the blocker carries `leagueLogoUrl`; `erasesOn` is now + 14 days and stays at the original date while
    pending; the handover names the co-organizer;
  - after the purge the co-organizer is organizer with `showHandoverNotice: true`, while anonymous callers see
    false;
  - dismiss by another user gives 403; the organizer gets 204 twice and the notice stays hidden;
  - the history row reads "Deleted user" with `deleted: true`, and `profiles.account_deleted` is set;
  - signing in after a request returns `accountRestored`.
- Browser (2026-10-11, Playwright MCP through the Chrome extension, ~300 px wide): `/delete-account` and `/privacy`
  render with no horizontal overflow and `noindex, follow`; the mailto link carries the subject "Delete my account".
  Found and fixed a bug: a bullet containing a link split into three columns, because legal list items are flex
  rows (marker + text). Both such bullets now wrap their text in one span. Web `npm test` 74/74, lint clean,
  `npx playwright test` 25/25. Run e2e with no dev server on :3000, or it reuses that server against the real
  backend and fails.

## 8. Design export: API gaps and mobile (2026-10-11)

The handoff text (`Crichere Account Deletion.dc.html`) described its own API. The owner chose to keep the existing
one with no renames and to add only what the screens need.
- **Mapping:** `deletionCancelled` is `accountRestored`, `NEEDS_CO_ORGANIZER` is `NO_CO_ORGANIZER`, POST
  `erasesOn` is `purgeAfter`. "Blocked" is an empty `blockers` list, and the POST 409 carries the list.
- **Added:** `handovers` (D3b) and `erasesOn` (D3, D6) on GET; league logos on blockers and handovers (D4); the D9
  notice flag plus a dismiss endpoint; the D10 `deleted` flags. See section 4.
- **D9:** only the new organizer sees the notice. The dismissal is stored server-side in one column per league,
  since only one person sees it.
- **Not flagged:** the live auction state (`currentPlayerName`, last result). A deleted user can't be in a running
  auction: the purge waits for it to end and withdraws them from leagues whose auction hasn't started.
- **Shared mobile layer:**
  - `account/AccountDeletionRepository`;
  - `DeleteAccountViewModel` (Loading / CanDelete / Blocked / Error; dialog rule: trimmed input equals `DELETE`);
  - `AccountNotices` (one-shot D7 and D8, in memory);
  - `AuthRepository.clearLocalSession()` (no backend calls; the server already revoked everything);
  - `AuthResult.accountRestored`;
  - `LeagueDetailViewModel.dismissHandoverNotice()` (optimistic, best-effort).

## 9. Mobile screens (2026-10-11)

Built from the Claude Design board `Crichere Account Deletion.dc.html` (light frames D1–D10). Spacing, type and
colours come from a `getComputedStyle` dump of the board DOM.
- **Android:**
  - `DeleteAccountScreen.kt` (D2–D5);
  - `DeleteAccountConfirmDialog.kt` (D6a–e: JetBrains Mono, `KeyboardCapitalization.Characters`, autocorrect off,
    no dismiss while submitting);
  - `AccountDeletionUi.kt` (neutral avatar, dismissible notice, date format);
  - D1 row in `OwnProfileScreen`, D7 notice on `PhoneEntryScreen`, D8 bar in `MainRoute`, D9 on League detail,
    D10 on League detail rows and the auction results roster;
  - eight Material Symbols Rounded icons added as vector drawables.
- **iOS:** `DeleteAccountView.swift`: the screen, a confirm sheet with the same enable rule, the D7 notice on
  Phone Entry, the D8 toast in `MainTabView`, D9 and D10 in `LeagueDetailView`. Authored only, never compiled
  (no Mac), same as earlier phases.
- **Where the build differs from the board:**
  - D6e: the board drops the check icon once the field loses focus, so the app shows it only while focused.
  - D8: the bar disappears after 4 s, or on a tap.
- **Verified on device:** see DESIGN-REVIEW.md, account deletion follow-up.

## 7. Not verified / open

- Mobile: iOS never compiled; Android D2, D3a, D6d and D10b not seen on a device (DESIGN-REVIEW.md follow-up).
- Real S3 deletes: local has no bucket, so the deleter logged and skipped. Needs the IAM change above, then one
  real purge in production to confirm.
- Push to the promoted co-organizer: covered by code, not seen on a device (no FCM locally).
- Organizer handover live: covered by the integration test, not run against the local backend by hand.
