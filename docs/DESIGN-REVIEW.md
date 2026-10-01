# Design Review — changes needed in the Crichere Claude Design comp

The app's behavior reflects real decisions made while building it. This doc is a **directive
list**: what to change in the Claude Design comp so it matches those decisions, not a neutral
pros/cons list. Two design artifacts were reviewed:
- **"Crichere App"** (`https://claude.ai/artifact/StbWb5dWgm44sbC9itgnHy`) — the clickable
  prototype, 14 screens, one reachable state each (plus one OTP error state).
- **"Crichere All Screens"** (`https://claude.ai/artifact/94UPWQdCs1GYaU9gPh451J`) — a static
  board with every state per screen (A1, A2, A3...), annotated with real source file/ViewModel
  names. This is the more complete and more code-aware of the two, and is treated as the primary
  source for this review; the clickable prototype is only cited where it disagrees with All
  Screens or adds something All Screens doesn't cover.

Compared against the actual implemented app — Android (Jetpack Compose) is canonical.

## The single biggest finding: no approve/reject workflow exists

The design (Screenshot Viewer screen H, plus status chips scattered across E3/E4/G3/M1) assumes a
full review workflow: a player/franchise submission stays **PENDING**, the organizer opens the
payment screenshot full-screen, taps **Approve** or **Reject** (with a reason picked from a sheet:
"Amount not visible," "Wrong amount," "Not a receipt," "Duplicate"), and the submitter sees that
reason and can re-upload.

**None of this exists in the app.** Checked directly against `FranchiseEntity.kt`: no status
column at all (confirmed by reading the entity — it has a "leave-request-and-approve shape," which
is a *different* flow, for existing members asking to leave, not for reviewing new joins/claims).
A player or franchise becomes visible **immediately** on submission. The organizer's only lever is
**Remove** (a hard delete from League Detail), not approve/reject with a reason.
`ScreenshotViewerScreen.kt` is confirmed to be a plain full-screen zoomable image viewer with only
a Back button — no Approve/Reject actions anywhere in it.

**Change the design:**
- Screen H (Screenshot viewer): remove the Approve/Reject buttons (H1), the reject-reason sheet
  (H3), and the zoom-only distinction shown in H2 stays as-is since pinch-to-zoom is real — this
  screen should just become "view full-screen, zoom, go back."
- Remove every **PENDING** / **APPROVED** / **REJECTED** status chip: E3's franchise-claim-pending
  row and the "Payment proof rejected" notice with its quoted rejection reason, E4's entire "To
  review · 3" queue card, G3's implied pending state, M1's "PENDING" status chip on the Sangli
  Night Cup franchise row.
- E4 (organizer view of League Detail): replace the review-queue card with what the app actually
  has — the Players/Franchises lists further down the screen, each row with its own **Remove**
  button (and, separately, **Approve leave / Dismiss** for leave *requests* specifically, which is
  real).

---

## A — Sign in

**Change the design:**
- Replace the fixed `+91` prefix chip with a single free-text field — the app expects the full
  number typed by the user, e.g. `+919876543210` (the design's own A1 caption already says this:
  *"the code currently expects them to type '+919876543210'"*, so this is a known, already-flagged
  fix).
- A4's error copy "Enter a valid 10-digit mobile number." → change to the app's real message:
  "Enter a valid phone number with country code, e.g. +919876543210."

**Implemented (2026-10-01):** hero banner built (confirmed absent from `AuthNavHost` before this).
It shows only in the first-launch state (empty, unfocused field) and collapses to the compact
28sp wordmark header once the field is engaged, matching A1 vs A2–A4. The custom in-app numeric
keypad in A2 is **not** built: the system phone keyboard stays (MVP scope decision). Send is
disabled only while the field is empty; number validity is still checked on tap (A4).

**Verify, don't assume:** every state (A1–A4) shows a green "Crichere" hero banner + tagline above
the sign-in card. The Android code agent's read of `PhoneEntryScreen.kt` alone found no such
banner — but that agent only read the one screen file, not any shared root/Scaffold wrapper. Check
whether a hero banner exists anywhere in `AuthNavHost.kt`'s wrapping of this screen before deciding
whether to add it to the app or drop it from the design.

---

## B — Verify code

**Change the design:**
- Resend cooldown shown as ~24–30s (B1, B4) → change to 60s (`RESEND_COOLDOWN_SECONDS = 60` in
  `OtpVerifyViewModel`).
- Replace the 6 separate digit boxes with a single "6-digit code" text field, capped at 6
  characters — that's the real input widget.
- B6's button reads "Send new code" after a lockout bounce-back; the app's Sign In screen always
  reads "Send code" regardless of how it was reached — change the design's label to match, unless
  a decision is made to special-case it (low priority either way).

**Implemented (2026-10-01):** back arrow and inline "Edit" both call `startOver()` (back to Sign
in, number kept). Verify is disabled until 6 digits. A "New code sent" snackbar shows after each
resend (B4). Resends exhausted shows the red notice + outlined "Request a new code" (B5). The
5-wrong-attempts bounce lands on Sign in with the "Too many incorrect attempts" banner (B6), keyed off
`attemptsRemaining == 0` in `AuthNavHost` so a voluntary start-over shows no banner. Kept from the
app: the system keyboard (no custom keypad), the "N attempts remaining" copy, and "Edit" in every
non-verifying state (the board only shows it in B1).

**Already correct, no change needed:** the 3-resend cap (B3/B4 show "1/3 used," "2/3 used" —
matches `MAX_RESENDS = 3`), the resends-exhausted dead-end state (B5), and the forced bounce-back
to sign-in after repeated wrong codes (B6) — all three match real `OtpVerifyViewModel` behavior.
Double-check B3's exact wrong-attempts count against `MAX_WRONG_ATTEMPTS = 5` (design shows "3
attempts left" on the first wrong try; the app's real sequence starts at 4 remaining).

---

## C — Profile setup

**Change the design:**
- Field order: change to Full name → Photo → Use my location → State/District/City → Playing role
  → Batting style → Bowling style (conditional). The design currently puts Photo before Full name;
  the app's real order is Name first.
- Add a visible "required" indicator on the photo step — the app blocks Save without one
  (`isSaveEnabled` requires non-blank `photoUrl`), so the design shouldn't imply it's skippable.

**Already correct, no change needed:** the conditional Bowling-style field (C5) — this was missing
from the older clickable-prototype review but the All Screens board already has it, matching the
app exactly.

**Needs your call, not a code contradiction:** C2–C4 and C6 show an in-app photo preview step
(circle-crop, drag-to-adjust, upload-progress percentage, retry-on-failure) marked "NEW" on the
board itself. None of this exists in the app today — `PhotoUploadService` uploads directly via the
system Photo Picker with no in-app preview/crop/progress UI at all. This isn't a contradicted
decision, just unbuilt. Decide: build this preview/crop flow into the app, or strip it back out of
the design to match today's plain "pick photo → label changes to 'Photo selected'" behavior.

---

## D — Dashboard

**Change the design:**
- Filter row: replace the icon-only "Near me" toggle with the app's real full-label button that
  cycles through "Show nearest to me" → "Finding your location..." → "Showing nearest leagues --
  tap to clear." Add the app's "Clear filters" button (shown when filters are active) and its
  manual "Refresh" button — neither appears in the design.
- D5's loading skeleton → change to the app's plain centered spinner.
- D6's empty state (icon + "No leagues in Wai yet" + "All of Satara"/"Create league" actions,
  shown alongside a separate "Couldn't refresh leagues" error banner) → the app just shows plain
  text "No leagues found." with no distinct actions. Simplify to match, or treat the richer version
  as new scope (see below).
- Cards showing computed distance ("2.4 km away" in D3) → the app doesn't compute or display
  distance anywhere, even in near-me mode; only city/district/state text is shown. Remove the
  distance text unless this becomes real scope.

**Needs your call:** the dashboard's whole card treatment — banner-free but data-rich cards with a
league short-code, date/format/entry-fee, and a player-count progress bar — doesn't exist in the
app at all. The app's actual dashboard list is plain text rows: name, location,
`"Starts X -- format"`, nothing else. This is a real feature gap, not a copy fix — decide whether
to build the richer cards or simplify the design to match today's plain rows.

---

## E — League detail

**Change the design (beyond the approve/reject removal above):**
- No banner image exists in the app anywhere on this screen — either add one to the app or drop it
  from the design.
- No organizer byline ("by Vikram Deshmukh") exists in the app — same call.
- Status is plain text in the app ("Completed"/"Announced"), not a colored badge.
- The "Auction" teaser banner (current player + live bid shown inline on League Detail, E1) has no
  app equivalent — "Live Auction" is a plain button with no bid preview. Either build the teaser or
  drop it.
- Share and "Copy watch link" are full-width buttons stacked lower on the app's page, not top-bar
  icons — both actions genuinely exist in the app, this is a layout-only difference to reconcile
  either direction.

---

## F — Join as player

**Change the design:**
- Remove the numbered "Step 1 / Step 2" structure — the app's fields are just inline, no step
  chrome.
- Add a copy-UPI-ID icon button next to "Pay via UPI" — the app only has the UPI-pay button, no
  separate copy action. (Note: F2's "no UPI app installed" fallback state — copy-with-confirmation
  — is good coverage to keep once the copy button exists; the app's own UPI-intent launch already
  silently no-ops with no fallback shown today when no UPI app is installed, so this fallback
  banner is also new scope to build, not just a copy button.)
- Disclaimer wording: align to the app's real text — "Crichere doesn't process payment or handle
  refunds/disputes -- that's between you and the organizer directly."
- Button label "Join league" → "Join" to match the app.
- F6 (free league, no payment step) is already correct — the app's `JoinLeagueViewModel` really
  does skip the fee/screenshot fields entirely when `playerFee == null`. Keep this state as-is.

**Needs your call:** F3–F5's preview/checklist/upload-progress/retry steps for the payment
screenshot are new, unbuilt UX (same bucket as C2–C4/C6) — the app currently just cycles a button
label through "Uploading..." → "Screenshot attached." Decide build-vs-strip.

---

## G — Claim a franchise

**Change the design:**
- Button label "Send claim" → "Claim Franchise" to match the app.
- The disclaimer currently reads (per All Screens' own intro paragraph, correctly): *"The claim
  stays pending until the organizer approves it (see E3)"* — but per the approve/reject finding
  above, **there is no pending/approval state in the app at all**. This line needs to change to
  something that doesn't promise a review step that doesn't exist — e.g. just the same
  no-payment-processing disclaimer used on the Join screen.

**Needs your call:** G2's logo preview/crop step — same unbuilt-preview bucket as above.

---

## I — Create / edit league

**Already correct, no change needed:** the UPI ID field (I6) and the map-based ground picker (I4)
are both present in this board and both real — the older clickable-prototype review flagged these
as missing, but All Screens already has them.

**Change the design:**
- **Add an Awards section.** Checked I1 through I6 (Basics, Location & ground, Schedule, Capacity
  & fees) — there is no Awards section anywhere in League Creation, on either design artifact. The
  app has a full add/remove Awards editor (name, cash amount, trophy checkbox) in
  `LeagueCreationScreen.kt`, and League Detail (screen E) displays whatever awards were entered —
  so there's currently no way in the design to create the awards it later shows.
- I4's ground picker bundles search + suggestion chips + map-tap-to-place all on one screen. The
  app's real flow is two separate steps: a search-existing-grounds-by-name list (in League
  Creation's own Ground section) that only falls through to the map (with a **draggable**, not
  tap-to-place, pin) when registering a brand-new ground. Reconcile the IA to match, or confirm the
  single-screen version is preferred and treat it as a real redesign of that flow.

**Needs your call:** I1/I2's live "HOW IT WILL LOOK" preview card and banner-crop step — unbuilt,
same bucket as the other preview/crop items above.

---

## J — Auction settings

**Change the design:**
- **Remove the "Bid timer" field entirely** (J1, and the locked read-only display in J2) — this
  was the example you gave. `AuctionSettingsViewModel` has exactly five fields: base price, purse
  per franchise, squad size **min**, squad size **max**, and bid increment. There is no timer
  concept anywhere in the app's auction settings.
- **Add Squad size (min) and Squad size (max) fields** — neither appears anywhere in the design's
  Auction Settings screen at all, but both are required fields in the app
  (`AuctionSettingsViewModel.submit()` requires all five fields to be present).
- J2's validation example ("Base price can't be more than the purse") — change to the app's real
  validation, which is a different formula entirely: a warning about squad max × franchises
  required exceeding players required, and a simpler check that all five fields parse as numbers
  (no base-price-vs-purse comparison exists in the code).
- J2's "locked once auction starts" read-only state — worth checking directly against the code
  (not confirmed either way in this pass) before deciding whether to build this guard or drop the
  locked-state design.

---

## K — Co-organizers

**Change the design:**
- Replace the fixed `+91` prefix chip with a single free-text phone field — same fix as screen A,
  one systemic pattern.
- **Add the Look-up → confirm dialog → Grant flow.** The design's K2 shows a single "Add" button
  with an inline not-on-Crichere error; the app's real flow is explicit and deliberate (per a code
  comment: friction is intentional) — a "Look up" button first, then, only if found, a confirmation
  dialog ("Grant co-organizer access? [Name] will be able to do everything you can do for this
  league...") before the actual grant happens. This is real, deliberate app behavior the design
  should represent, not simplify away.

---

## L — Live auction

**The largest set of changes.** Per the app's `AuctionLiveScreen.kt`: current player name, current
bid amount + leading franchise name, a free-text bid-amount field with a single "Place Bid" button,
and (organizer only) Start/Next Player/Sold/Unsold/Undo/End buttons plus an exceed-purse toggle.
Franchise purse/squad-count only appears **after** the auction ends, in a Results block — never
live during bidding.

**Remove from the design entirely** (none of this exists in the app):
- The countdown timer ring (L1's "11 SEC," L2's "04 SEC" urgency state, L3's "09") — this was your
  named example.
- The three quick-bid preset buttons (+₹500/+₹1,000/+₹2,000) — the app only has a free-text amount
  field.
- The live, horizontally-scrollable franchise-purse strip shown *during* bidding (L1, L3) — the app
  only shows this after the auction ends.
- The running bid-history feed shown live (L1) — no equivalent in the app during bidding.
- The full-bleed "SOLD" overlay moment (L4) — the app has a plain Sold/Unsold button pair with no
  overlay animation.
- The player photo on the block (L1–L4) — not present in the app's auction-live UI.
- L2's "outbid" alert and over-purse-warning-on-the-bid-button — no equivalent messaging exists in
  the app's bid form.
- L5's "reconnecting" status text and not-started/viewer card, L6's end-of-auction summary screen —
  none of these specific UI treatments exist; the app just shows `"Status: $auctionStatus"` as
  plain text and the post-auction Results block (franchise name, players won, purse spent/remaining,
  below-min-squad warning) covers the summary, in a much plainer form than L6.

**Already correct, no change needed:** L3's organizer controls (Sold, Unsold/Mark unsold, Undo,
End auction) match the app's real buttons — just without the design's countdown/timer chrome
around them. The exceed-purse toggle is also real (`allowExceedPurse` / `onToggleExceedPurse`).

This screen needs the most decisive simplification in the whole design — confirm the app's current
plain bid-form is the intended final shape (in which case most of L1–L6 gets cut), or treat this
as a real, sizable feature backlog (timer, quick-bid, live franchise strip, bid history) to build.
Given you named the timer specifically as something to remove, the direction here is probably
"simplify the design," not "build the features" — but the size of this gap is worth confirming
explicitly before cutting all of it.

---

## M — My leagues

**Change the design:**
- **Add a "Following" tab/section.** The app has four categories — Organizing, Playing, Franchise
  owner, Following — but the design's M1 only shows three tabs ("Playing," "Franchise,"
  "Organizing," plus "All") with no "Following" anywhere.
- Tabs (design) vs. always-visible stacked sections (app, no tab-switching, no "All" concept) is a
  structural IA mismatch — reconcile in one direction. Given the app already ships this way,
  changing the design to stacked sections is the lower-effort direction, but confirm.
- M2's empty state (icon, "No leagues yet," "Browse leagues"/"Create" actions) has no app
  equivalent — the app currently renders nothing at all (blank screen, no header, no message) when
  every category is empty. This is a real app bug worth fixing regardless of the design direction
  — build M2's empty state into the app.

---

## N — My profile

**Change the design or the app (pick one per item):**
- The app displays **no photo/avatar anywhere on this screen** at all, despite capturing one at
  signup — likely an unintentional omission. Fix the app to show it (the design's N1/N3
  photo-with-camera-overlay / initials-fallback pattern is the right target).
- The app has **no "Phone" row and no "Member since" row** — both exist in the design (N1). Add
  them to the app, or drop them from the design.
- The design's N1 shows one combined location line ("Kolhapur, Kolhapur, Maharashtra"); the app
  shows State/District/City as three separate rows. Reconcile either direction.
- **Independent bug, fix regardless of the design work**: Playing role, Batting style, and Bowling
  style are shown as raw enum names (`"ALL_ROUNDER"`, `"RIGHT_ARM_OFFBREAK"`) instead of the
  humanized labels used everywhere else in the app (Profile Setup shows "All-rounder"). This is a
  real display bug, not a design-alignment question.

---

## Cross-cutting changes

1. **Fixed `+91` phone-prefix chip** (screens A, K): the app uses a single free-text field on
   both. Fix the pattern once, applies everywhere.
2. **The approve/reject/pending review workflow** (screens H, and status chips on E3/E4/G3/M1):
   doesn't exist anywhere in the app's data model — see the top of this doc. This is the largest,
   most confident single change across the whole design.
3. **Unbuilt image-preview/crop/upload-progress steps**, marked "NEW" on the All Screens board
   itself: C2–C4/C6 (profile photo), F3–F5 (payment screenshot), G2 (franchise logo), I1–I2
   (league banner/logo), N2 (profile photo options sheet). None of this exists in
   `PhotoUploadService` today — it uploads directly via the system Photo Picker with no in-app
   crop/preview/progress UI. Decide once, applies to every upload flow in the app: build this UX,
   or strip it from the design to match today's simpler "pick → label changes" behavior.

## Needs your decision (not contradicted by a specific known app decision — genuinely open calls)

- Sign In's green hero banner (screen A) — verify whether it exists in a shared wrapper before
  assuming it needs to be added.
- Dashboard's rich cards and richer filter/loading/empty states (screen D).
- League Detail's banner image, organizer byline, and live-auction teaser (screen E).
- The image-preview/crop/upload-progress UX bucket (see Cross-cutting #3).
- League Creation's live "HOW IT WILL LOOK" preview card (screen I).
- Auction Settings' "locked once started" guard (screen J) — check the code directly.
- Live Auction's overall scope (screen L) — see that section; leaning toward "simplify the design"
  given the timer was named explicitly, but the size of the gap is worth an explicit confirmation.

## Follow-ups (todo)

Open items found while implementing the redesign, not yet scheduled.

- [ ] **Uploads go live before Save** (found on screen C, 2026-10-01). Photo uploads use a fixed
  S3 key per owner (`users/{userId}/profile.jpg`, `leagues/{id}/logo.jpg`, ...), so the live image
  is replaced the moment the upload finishes, even if the user then cancels or never taps Save.
  Backend change: upload to a fresh key and only point the record at it on save.
- [ ] **Profile Setup subtitle kept on filled forms** (screen C). "So organizers know who's joining
  their league." stays visible after a name is typed. The design hides it in the filled states
  (C4, C6), but hiding it makes the whole form jump on the first keystroke. Revisit with the design.
