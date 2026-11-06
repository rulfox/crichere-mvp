# Phase 18: Play compliance (report, block, terms acceptance, auction note)

**Status:** analysis and plan done, decisions closed. Nothing built.
**Date:** 2026-11-04

## 1. Scope

Designs: Crichere - V2 boards `P11 Report & Block` and `P12 Play Compliance`. P12 section 0 overrides P11.

- Report a person or league; block a person; blocked people list.
- Team actions: remove an image, suspend an account, with owner notices.
- Required terms checkbox at sign in; Terms and Privacy links on My Profile.
- Auction note on Auction Settings, live auction and web viewer.
- Reviewer login through one MSG91 demo number (D1). Owner setup, no code.

Decided already: reports go to the Crichere team only; blocking hides content and keeps names; organizers still see
blocked members' payment screenshots; only the main organizer counts for "leagues they organize"; auction amounts
are virtual and stay in rupees.

## 2. What the code does today

- **No moderation code exists:** no report, block or suspension in backend, shared, Android, iOS or web.
- **Auth is stateless:** `JwtAuthenticationFilter` only verifies the token and never loads the user. Access tokens
  last 15 minutes, refresh tokens 30 days. `AuthService` already refuses refresh for deleted accounts.
- **No roles:** the principal has an empty authority list, so there is no admin concept.
- **Reads are public:** `GET /leagues`, `/leagues/*`, the auction stream, results and live-now are `permitAll()`.
- **The auction stream is shared:** one public SSE payload per league, the same for every viewer, sent without
  caller identity.
- **Payloads already carry user ids:** `LeaguePlayerResponse.userId`, `LeagueFranchiseResponse.ownerUserId`,
  `LeagueResponse.organizerUserId`, `PlayerAuctionResultResponse.userId`.
- **No email:** the backend has no mail dependency or provider.
- **Next migration is V25.**

## 3. Spec gaps

| # | Gap | Why it matters | Proposed answer |
|---|---|---|---|
| G1 | Suspension has no enforcement point | A suspended user keeps working for up to 15 minutes, and can bid | Add `suspended_at`, refuse sign-in and refresh, revoke refresh tokens, delete device tokens, and check on authenticated requests (D2) |
| G2 | App would misread a suspended refresh | `AuthRepository.refresh` treats anything but 401 as a temporary failure, and 401 means plain logout. Neither reaches R13 | New problem code `ACCOUNT_SUSPENDED`, handled in the refresh path and in a shared response check |
| G3 | R13c is wrong as drawn | It says the live stream returns "suspended", but the stream is public and anonymous | Trigger on the next authenticated request, such as a bid. Viewing can continue until then, as anyone can watch |
| G4 | Hiding can't be done on the stream | The stream is identical for all viewers | Hide on the client for rosters, auction and results, using the block list and ids already sent. Filter on the server for dashboard lists |
| G5 | Some auction fields have no user id | `currentPlayerPhotoUrl` and franchise logos need mapping | Map `currentPlayerId` and franchise id through league data already loaded |
| G6 | Removing a profile photo breaks the profile | Photo is required (`ProfileCompletionService:49`), so removal makes the profile incomplete and conflicts with R12b | D3 |
| G7 | Removed and blocked images need the neutral look | A null image shows initials today, but boards say never initials | Add removed flags to responses, like the existing `deleted` flag (D3) |
| G8 | No way to act on a report | No admin screen, no roles, and the app must show action is taken | Runbook plus script for the team, no admin endpoint (D4) |
| G9 | "Emailed to hello@" has no sender | No mail provider exists | Dropped for launch (D5): reports are stored and checked daily |
| G10 | Evidence can disappear | A user can change an image after being reported, and `MediaSweep` deletes unreferenced objects | Snapshot name, text and image URLs at report time; sweep skips keys in open reports |
| G11 | Followed leagues undefined | R7a covers joined leagues only | D6 |
| G12 | Live-now is public with no caller | A blocked organizer's league can appear there | Filter on the client |
| G13 | Consent has no record | Two sign-in paths: Firebase `/auth/session` and MSG91 `/otp/verify` | Both take `termsAccepted`; store `terms_accepted_at` and `terms_version`. New accounts refused without it |
| G14 | Checkbox for returning users | The app doesn't know the account before OTP, so everyone sees it | Show it on every sign in, as drawn. Simple and accurate |
| G15 | Account deletion ignores new data | `AccountPurgeService` must clear new tables | Delete blocks both ways; keep reports with reporter removed (D7) |
| G16 | Ban evasion through deletion | Purge frees the phone number, so a suspended user could return | D7 |
| G17 | Suspended organizer mid-auction | Auction stalls unless a co-organizer can run it | Runbook rule: don't suspend an organizer during a live auction, except for urgent safety cases |
| G18 | Legal pages not ready | T1 links to pages with placeholders, `/delete-account` was 404 live, Privacy Policy lacks this new data | Fill placeholders, deploy web, add reports, blocks, consent and moderation to the policy |
| G19 | Links need a browser | P12 says in-app browser; no Custom Tabs dependency | Use Custom Tabs on Android, `SFSafariViewController` on iOS |
| G20 | iOS parity | Swift has never compiled | Write to match, record as unverified |

## 4. Security analysis

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| S1 | MSG91 demo numbers are a login backdoor on production (PHASE12: three numbers, fixed OTP `123456`) | High | Owner: remove two numbers, use a non-obvious OTP if MSG91 allows, point the last one at a plain sample account, remove or change it after review. Rotate the Auth Key and widget token exposed on 2026-10-03 |
| S2 | Suspended user acts inside the token window | Medium | Per-request check (D2). Suspension takes the same user row lock as refresh, so an in-flight refresh can't outrun it |
| S3 | Reporter identity leaks | Medium | No API returns reports to anyone but the team. No reporter in notices, pushes or logs |
| S4 | Report text used for injection | Medium | Stored as plain text, 500 limit, control characters stripped, never logged. Escaped in any email or tool output |
| S5 | Client-supplied evidence | Medium | Client sends ids, reason and text only. Server builds the snapshot, so no URL is ever accepted from a client |
| S6 | Mass or false reports used as harassment | Medium | Limit reports per user per hour, one open report per reporter and subject (unique index), and no automatic action, ever |
| S7 | Block list exposure | Medium | `GET /blocks` returns the caller's own list only. Nothing tells a person they are blocked. Block rows never appear in other responses |
| S8 | Hiding is not access control | Low | Accepted. Public pages show the same content, and URLs still reach the client. Don't describe blocking as security in copy |
| S9 | Removed image stays reachable | Medium | Delete the S3 object, not just the row. Needs the pending `s3:DeleteObject` grant. Device caches can keep it until cleared |
| S10 | Admin endpoint as attack surface | Medium | Don't add one (D4). Actions run from a script with production credentials, and each is written to an audit table |
| S11 | Sensitive evidence kept too long | Medium | Set retention, delete after it, and disclose in Privacy Policy. Reports can hold other people's data |
| S12 | Block or report spam | Low | New caps in `ContentRateLimiter`. Buckets are in memory and reset on restart, which is acceptable |
| S13 | Terms flag is client-asserted | Low | Accepted. It records the user's action, and server refuses account creation without it |
| S14 | Id probing via block and report | Low | User ids are already public. Same error for missing and deleted targets; reject self-block and self-report |

No new mobile permissions. No payment data involved.

## 5. Decisions (all closed 2026-11-04)

| # | Decision | Outcome |
|---|---|---|
| D1 | Reviewer login | **Decided 2026-11-04:** keep MSG91 (production provider, checked live). One demo number on the widget for the reviewer, no backend code |
| D2 | Suspension check | Check every authenticated request, using a short in-memory cache of suspended ids |
| D3 | Removed profile photo | Keep profile complete with a `photoRemoved` flag and neutral avatar, and prompt for a new photo with R12b |
| D4 | How the team acts | Script and runbook in `backend/tools`, with audit table. No admin endpoint or screen for launch |
| D5 | Report alerts | Amazon SES on the existing AWS account, alert with report id only, details read from the database. Alternative: no email, check the table daily |
| D6 | Followed league of a blocked organizer | Leaves the dashboard, follow row kept, pushes muted while blocked |
| D7 | Deletion and ban evasion | Keep a hash of suspended numbers after purge and refuse sign-up, disclosed in Privacy Policy. Reports keep evidence with reporter removed, for a set retention period |

## 6. Not verified

- Whether the MSG91 demo numbers are still active on the production widget.
- Real SMS delivery through MSG91 on Jio, Airtel and Vi. Test before launch.
- Whether the live web deploy has caught up with the repo.
- P11 and P12 are drawn in Inter; the app uses its own fonts. Build uses app styles and exact CSS from the boards.

## 7. Build plan

Separate commit per step. Backend tests with each backend step, shared and web unit tests with theirs.

**Step 1: data (V25).**
- `users`: `suspended_at`, `terms_accepted_at`, `terms_version`.
- New tables: `user_blocks`, `reports`, `moderation_actions` (audit), `moderation_notices`, `suspended_phone_hashes`.
- `profiles.photo_removed`, `leagues.logo_removed`, `leagues.banner_removed`, `league_franchises.logo_removed`.

**Step 2: suspension and terms in auth.**
- Filter after JWT check: suspended id gets 403 `ACCOUNT_SUSPENDED`, using a short in-memory cache.
- `AuthService` and `OtpAuthService` refuse sign-in and refresh with the same code; sign-up refused for a hash in
  `suspended_phone_hashes`.
- `/auth/session` and `/otp/verify` take `termsAccepted`; a new account needs it, and acceptance is stored.

**Step 3: blocks and reports.**
- `GET /blocks`, `PUT /blocks/{userId}`, `DELETE /blocks/{userId}`.
- `POST /reports`: ids, reason and text only; server snapshots evidence; 409 `REPORT_ALREADY_OPEN`.
- New rate-limit caps. `listByArea` and `listNearest` filter by caller. No follower pushes exist today (only players,
  owners and organizers get them), so D6's mute needs no code; add it if follower pushes are ever built.
- `MediaSweep` skips keys in open reports. `AccountPurgeService` deletes blocks, removes the reporter from reports,
  and moves a suspended user's phone hash to `suspended_phone_hashes`.

**Step 4: removal flags and notices.**
- Removed flags on every response that carries the image. Profile stays complete when `photo_removed` is set.
- `GET /me/moderation-notices`, `POST /me/moderation-notices/{id}/dismiss`.

**Step 5: team tooling.**
- `backend/tools/moderation`: list open reports, dismiss, remove image (S3 and row), suspend, unsuspend. Each writes
  to `moderation_actions`. Runbook covers the daily check, the organizer-during-auction rule and retention.

**Step 6: shared KMP.**
- Block, report and notice repositories; block set as shared state, loaded after sign in.
- `ACCOUNT_SUSPENDED` handled in refresh and in a shared response check, routing to R13.
- `termsAccepted` on both sign-in paths.

**Step 7: Android.** Build from P11 and P12, with exact CSS from the boards.
- Roster menu, Report league link, report sheet, block dialog, blocked people, neutral images in rosters and auction.
- R9, R12 notices, R13, T1 checkbox, T2 links with Custom Tabs, V1 and V2 notes.
- Checks on the Pixel_9_Pro AVD with the local backend.

**Step 8: web.** V2 note in `LiveAuction.tsx`; `logoRemoved` and related flags where the web shows images. Vitest and
Playwright.

**Step 9: iOS.** Same screens in SwiftUI. Not compiled, so recorded as unverified.

**Step 10: docs.** Privacy Policy and Terms for reports, blocks, consent, moderation and retained hashes; review notes;
SECURITY-AUDIT entry; unverified scenarios in DESIGN-REVIEW Follow-ups.

**Board corrections to follow in the build:** R13c triggers on the next authenticated request, not the stream.

**Owner before deploy:** MSG91 tasks, `s3:DeleteObject` and `s3:ListBucket` grants, legal placeholders and lawyer review,
retention periods, web deploy. Deploy the backend first, then the mobile release straight after: older apps can't create accounts on the new backend.

## 8. Status (2026-11-06)

Built on branch `play-compliance`, not pushed, not deployed.

| Step | State |
|---|---|
| 1 to 5, backend and script | Done, tests pass. Script run on the local database with `--no-s3` |
| 6, shared | Done, tests pass |
| 7, Android | Done. Run on Pixel_9_Pro against a local backend; Compose tests added |
| 8, web | Done. Vitest, lint and Playwright pass |
| 9, iOS | Written, never compiled |
| 10, docs | Done. Placeholders in the legal pages stay for the owner |

Unverified scenarios are in DESIGN-REVIEW Follow-ups. Before release: decide how to block an organizer who has no roster
row, then finish the owner tasks in section 7.
