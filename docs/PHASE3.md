# Phase 3 — Joining, Franchises, Deep Linking

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions that apply here, and [PHASE2.md](PHASE2.md) for the league/ground/award model this phase builds on.

**Last updated:** 2026-09-12
**Status:** implemented (backend + Android). Build order followed the plan at
`C:\Users\rulfo\.claude\plans\plan-phase-3-implementation-floofy-thimble.md` (migrations V10-V13,
`player`/`franchise`/`me` packages, `LeagueService`/`LeagueController` follow+redaction+validation
additions, mobile shared DTOs/repositories/ViewModels/DI, Android League Detail extensions + Join/
Claim/My-Leagues screens + 3rd bottom-nav tab + `crichere://leagues/{id}` deep linking). Backend
`./gradlew test` and mobile `:shared:testDebugUnitTest` + all 3 iOS test-compile targets are green;
`androidApp:assembleDebug` succeeds. iOS SwiftUI screens are authored (`LeagueDetailView.swift`,
`JoinLeagueView.swift`, `ClaimFranchiseView.swift`, `MyLeaguesView.swift`,
`ScreenshotViewerView.swift`) but unwired, per the standing iOS posture (see Section 5). Still
open: real on-device Android walkthrough of the full join/claim/follow/deep-link flow (no
emulator/device in this environment), and the map-pin-drag / iOS gaps already tracked in
PHASE2.md.

---

## 1. Overview

Phase 2 made leagues discoverable and creatable, but closed with no way to actually join one — League Detail explicitly had "no join/registration action yet." Phase 3 closes that loop: a user can join a league as a player, or claim a franchise slot, against the capacity numbers already captured at creation (`players_required`/`franchises_required`). Anyone can also just follow a league without joining.

This is still **not** the live auction — no bidding, no player pools being drafted onto teams. It's the registration/commitment layer that would feed into an auction later: who's actually in, who owns which franchise, who's just watching.

The other half of this phase is distribution: an organizer gets a shareable link the moment a league exists, and tapping it opens the app straight to that league — this is the platform's real acquisition channel (shared in WhatsApp groups), not app-store discovery.

Money is the new surface here. Fees were already display-only numbers in Phase 2. Phase 3 makes them collectible — but peer-to-peer, directly to the organizer's own UPI ID, with Crichere never taking custody of funds and never integrating a payment gateway. Verification is proof-based (a payment screenshot only — no attempt to parse a UPI app's transaction response, see Decisions Made), not a reconciled ledger — an honor-system tradeoff explicitly accepted for MVP scale.

---

## 2. Features

- Join a league as a player (counts against `players_required`)
- Claim a franchise in a league (counts against `franchises_required`); a franchise is a real named entity (name + optional logo), not just a flag on the user
- If the league has a player/franchise fee set: joining requires payment proof first — pay the organizer's UPI ID directly, then attach a screenshot of that payment. Attaching the screenshot completes the join immediately (no organizer approval step)
- If the corresponding fee is unset: joining is instant and free
- Organizer sees a Players list and a Franchises list on their own League Detail, each entry showing whatever proof was captured, with a remove-participant action
- A joined player/franchise owner can request to leave; the organizer must approve the request before the slot actually frees up (not a self-serve instant leave)
- Once a league is marked completed, or once a role's capacity is full, its Join/Claim action is replaced with a status label ("Registration full" / "League completed") instead of just disappearing
- Follow a league (no fee, no capacity limit, no proof) — surfaces in a "My Leagues" view (Organizing / Playing / Franchise owner / Following)
- Share a league: League Detail gets a "Share" action that produces a link; opening that link (app installed) jumps straight to that league's detail screen
- Organizer can open any attached payment screenshot full-screen (zoomable) to actually check it, not just see a thumbnail
- Join/Claim flow shows a plain-language notice that Crichere doesn't process payment or handle refunds/disputes — that's between the player/franchise owner and the organizer directly
- Joining twice by mistake (e.g. a double-tapped confirm) can't create two active join rows for the same user in the same league
- Editing a league after people have joined is guarded: capacity can only be raised, never dropped below who's already in; a fee can't be changed once at least one person has paid it

---

## 3. Screens

Describing what belongs on each screen — not layout, spacing, or component choice.

**League Creation** — gains one new optional field:
- *Fees* section gains an organizer UPI ID field, required only if either franchise fee or player fee is filled in (a fee with nowhere to pay it is a dead end).

**League Detail** — gains:
- "Join as Player" / "Claim a Franchise" actions, shown only to non-organizers who haven't already joined/claimed in that role. Once the league is completed, or that role's capacity is full, the action is replaced by a status label ("Registration full" / "League completed") rather than hidden with no explanation.
- Once joined/claimed: a "Request to leave" action in place of the join action, for that user only.
- A "Follow" toggle, always available.
- A "Share" action producing the league's deep link.
- Players list and Franchises list sections (visible to everyone, same as the existing Prizes section) — franchise entries show name + logo.
- Organizer-only: each Players/Franchises row gets a "Remove" action, plus visibility into the payment proof attached to that row, plus any pending leave requests surfaced for approval.

**Join / Claim flow** (new screen or sheet, entered from League Detail):
- If no fee is set for that role: a single confirm action, joins immediately.
- If a fee is set: shows the amount and the organizer's UPI ID, a "Pay via UPI" action that launches the device's UPI app chooser (just to make paying convenient), then an "Attach screenshot" picker. Submitting with a screenshot attached completes the join/claim.
- Claiming a franchise additionally asks for a franchise name (required) and logo (optional) as part of the same flow.

**Leave flow**: joined player/franchise owner taps "Request to leave" → sets a pending flag on their row, visible to the organizer. Organizer approves it (frees the slot, marks the row left) or dismisses it (stays joined). No self-serve instant leave — mirrors the "organizer has the last say" posture the Remove action already established.

**My Leagues** (new — reachable from the bottom nav or My Profile tab):
- Four lists: leagues you organize, leagues you've joined as a player, leagues where you hold a franchise, leagues you follow.

---

## 4. Decisions Made

| Decision | Rationale |
|---|---|
| **Join/claim is proof-gated, not organizer-approval-gated** | Matches Phase 2's existing "no approval gate" posture for league creation itself — confirmed explicitly (2026-09-12). A screenshot present completes the join instantly; the organizer's roster view + remove action is the after-the-fact safety valve, not a pre-join gate. |
| **Franchise is a real entity now (name + optional logo), not just an identity flag** | Confirmed explicitly (2026-09-12) — League Detail needs a Franchise list distinct from a Players list, and the future auction phase needs team identity anyway. Better to name it once than rebuild it later. |
| **Payment is peer-to-peer via the organizer's own UPI ID, never through Crichere** | Crichere never custodies funds, which keeps it out of payment-aggregator/PA-PG regulatory territory entirely. The tradeoff: verification is honor-system (a screenshot), not a reconciled ledger — accepted as the right MVP tradeoff for a community platform. |
| **Proof is screenshot-only — no attempt to parse a UPI app's transaction response** | Reversed same-day (2026-09-12): real-world UPI intent response behavior varies by app (GPay/PhonePe/Paytm) and Android version, some don't reliably return a transaction id/status to the calling app at all. Parsing it would be unreliable scope for something the screenshot already covers on its own. The "Pay via UPI" button still launches the UPI app chooser for convenience — the app just never tries to read anything back from it. |
| **Deep linking starts as a custom URI scheme (`crichere://leagues/{id}`), not domain-based App Links/Universal Links** | Confirmed explicitly (2026-09-12) — no real domain/hosting exists yet. Works instantly when the app is installed; a dead link when it isn't is the accepted tradeoff (same "authored now, upgrade later" posture as the Maps API key / Firebase gaps in Phase 2). Revisit once a real domain exists. |
| **Payment step only triggers when the corresponding fee is actually set on the league** | Fees are still optional per Phase 2 — a league with no player_fee shouldn't force a payment step to join as a player. Free join stays instant. |
| **Leaving is request-and-approve, not self-serve instant** | Confirmed explicitly (2026-09-12) — a player/franchise owner can request to leave, but the organizer must approve before the slot frees up. Consistent with the organizer already holding final say via the Remove action; prevents a paid slot from silently vanishing without the organizer's knowledge. |
| **Join/Claim hides behind a status label when the league is completed or that role's capacity is full**, not just removed with no explanation | Confirmed explicitly (2026-09-12) — a blank screen where the join button used to be reads as a bug. "Registration full" / "League completed" tells the user why. |
| **Capacity fields (`players_required`/`franchises_required`) can only be edited upward, never below the current active joined/claimed count** | Confirmed explicitly (2026-09-12) as the recommended fix for editing capacity after joins exist — a simple floor validation on league edit, avoids orphaning already-joined participants. |
| **Fee fields (`player_fee`/`franchise_fee`) lock once at least one active join/claim exists for that role** | Confirmed explicitly (2026-09-12) as the recommended fix for editing fees after joins exist — already-joined people paid under the old number; letting the organizer change it after would retroactively misrepresent what their submitted screenshot was for. Fee becomes editable again only once that role has zero active joins. |
| **A join/claim row is always tied to the authenticated submitting user** — screenshot provenance concern narrowed, not left open | Clarified (2026-09-12): joining happens through an authenticated API call, so the row is never anonymous — it's attributable to a real account from the start. The residual risk is narrower than "no link to account": the screenshot's *content* isn't cryptographically verified as that exact transaction (a reused/old screenshot is still possible). Same accepted honor-system risk as forged proof generally, not a distinct gap. |
| **Capacity overflow hard-blocks** — no waitlist | Confirmed explicitly (2026-09-12). Once `players_required`/`franchises_required` is reached, Join/Claim shows "Registration full." Simplest option, matches this project's consistent MVP-simplicity bias; a waitlist/queue is real added scope for a benefit not yet needed at this league size. |
| **Dual roles allowed freely, no restriction** | Confirmed explicitly (2026-09-12). A franchise owner can also join as a player, and a user can claim more than one franchise in the same league — no extra validation. Matches real life (owners often play for their own team) and needs no new checks. Revisit only if abuse shows up. |
| **Deep link requires login first, then resumes into the league** | Confirmed explicitly (2026-09-12). `crichere://leagues/{id}` falls through to the existing OTP flow if not logged in, then lands on that league's detail once authenticated — consistent with Phase 1/2's existing all-screens-require-auth posture, no new public read-only screen variant needed. |
| **Notifications ship silent-first (in-app state only), push deferred** | Confirmed explicitly (2026-09-12). Join/remove/leave-approval/follow updates are just current state, checked by opening the screen — no push infra exists yet beyond Firebase Phone Auth (OTP delivery, not messaging). FCM messaging is a real fast-follow, not bundled into this already-large phase. |

---

## 5. Open Questions and Gaps

Every genuinely open item from scoping has been resolved by explicit decision (2026-09-12) — see Decisions Made. Remaining, real residual items:

- **No real payment reconciliation or fraud detection.** A forged or reused screenshot is technically acceptable at this scope — accepted community-scale risk. The only backstops are the organizer's Remove action and the fact every join is tied to a real logged-in account.
- **No refund/dispute handling, and nobody's told that yet.** Money moves peer-to-peer; Crichere is deliberately out of that loop. The Join/Claim flow now includes a plain-language disclaimer (see Features) — exact copy/placement is a UI-writing detail for implementation, not an open product decision.
- **Single UPI ID per league** (the organizer's) covers both franchise and player fee collection — not modeled as two separate payee IDs. Flag if that's ever wrong (e.g. co-organizers with separate accounts).

---

## 6. Pure Technical Things (draft, pending implementation planning)

### Data model

- `leagues` gains `organizer_upi_id` (nullable — only required when a fee is set).
- `league_players`: id, league_id (FK), user_id (FK), joined_at, payment_screenshot_url (nullable — only null when the league has no player_fee), leave_requested_at (nullable), removed_at (nullable — set on organizer Remove or on organizer-approved leave, same field covers both since the effect is identical: slot freed). Unique constraint on (league_id, user_id) where removed_at is null, so a double-submitted join can't create two active rows.
- `league_franchises`: id, league_id (FK), owner_user_id (FK), name, logo_url (nullable), joined_at, payment_screenshot_url (nullable, same rule as above), leave_requested_at (nullable), removed_at (nullable). No per-user uniqueness constraint here (dual roles/multiple franchises per user are allowed per Decisions Made) — double-tap protection for this table is a client-side disable-after-submit, not a DB constraint, since a legitimate second franchise claim by the same user is valid.
- `league_follows`: league_id (FK), user_id (FK), followed_at — composite key, no payment/capacity concept at all.
- League edit validation: reject if new `players_required`/`franchises_required` is below that role's current active (non-removed) row count; reject changes to `player_fee`/`franchise_fee` while that role has at least one active row (see Decisions Made).

### API surface (draft)

- `POST /api/v1/leagues/{id}/players` — join as player (body carries the payment screenshot URL, when the league has a player_fee). Rejects once active player count reaches `players_required` (hard block, no waitlist).
- `POST /api/v1/leagues/{id}/franchises` — claim a franchise (body: name, optional logo, payment screenshot URL when the league has a franchise_fee). Rejects once active franchise count reaches `franchises_required` (hard block, no waitlist).
- `DELETE /api/v1/leagues/{id}/players/{playerId}`, `DELETE /api/v1/leagues/{id}/franchises/{franchiseId}` — organizer-only remove.
- `POST /api/v1/leagues/{id}/players/{playerId}/leave-request`, `.../franchises/{franchiseId}/leave-request` — self-scoped, sets `leave_requested_at`.
- `POST .../leave-request/approve`, `POST .../leave-request/dismiss` — organizer-only, either sets `removed_at` (approve) or clears `leave_requested_at` (dismiss).
- `POST /api/v1/leagues/{id}/follow`, `DELETE /api/v1/leagues/{id}/follow` — self-scoped.
- `GET /api/v1/me/leagues` — the four My Leagues lists (organizing/playing/franchise/following) in one call.
- Franchise logo presign mirrors league logo/banner: `POST /api/v1/leagues/{id}/franchises/{franchiseId}/logo-upload-url`, same S3 presigned-POST pattern, key prefix `leagues/{leagueId}/franchises/{franchiseId}/logo.jpg`.

### Deep linking

- Android: custom-scheme `<intent-filter>` (`crichere://leagues/{id}`) in `AndroidManifest.xml`, routed through the existing plain-state-switcher nav pattern to open `LeagueDetail(leagueId)` directly.
- iOS: `CFBundleURLTypes` custom scheme + `onOpenURL`, authored alongside Android per the standing iOS posture (code written now, verified once a Mac is available — see PHASE2.md Section 5).
- Auth gate: if not logged in, the deep link resumes into the existing OTP flow first, then routes to `LeagueDetail(leagueId)` once authenticated (see Decisions Made) — no public/pre-login screen variant needed.

### UPI payment intent

- Standard `Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=$upiId&pn=$organizerName&am=$amount&tn=$note&cu=INR"))` launched via `startActivityForResult`, purely as a convenience to open a UPI app pre-filled with the organizer's ID and amount.
- Nothing is read back from the launched app's result — screenshot is the only proof captured, per the decision above.

### Security

- Join/claim/follow/unfollow are self-scoped (`user_id == caller`) — same ownership-check discipline Phase 2 introduced. Remove-participant is organizer-only (`league.organizer_user_id == caller`).
- Franchise logo presign needs a franchise-owner-or-organizer check before issuing a URL.
- Per-user rate limit on join/claim endpoints (same Bucket4j pattern as Phase 2's league/ground creation limits) to prevent spam-joining.
- Screenshot uploads reuse the existing S3 presigned-POST flow — no server-side image content validation planned, same posture as profile/league photos.
- No payment data of real sensitivity is ever stored server-side (no card/UPI-PIN/credential capture) — only a screenshot image, the same one already sitting in the payer's own phone gallery.
