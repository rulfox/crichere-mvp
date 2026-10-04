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

**Implemented (2026-10-01):** the board's decided rich cards are built from data the list endpoint
already returns (no backend change): logo or short-code tile, "Starts d MMM", format, entry fee
("Free" when none), and players joined / players required bar. Header avatar (initials or photo)
opens My Profile. Area chips open a searchable bottom-sheet picker (D2) and are dimmed while near-me
owns the list (D3/D4). Empty state (D6) and location-off banner (D7, shown after the permission is
denied; not verified on-device because ColorOS blocks revoking permissions over adb). Bottom nav
restyled (tinted bar, pill indicator, filled icon when selected). Fixed along the way: a slow older
list request could overwrite the list for a newer filter; near-me asked only the network location
provider (often no fix) -- it now uses a recent fix from any provider, then fused/network/GPS.

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

**Implemented (2026-10-01), after design update #2.** One page for everyone: banner + logo tile (or
a compact header when there's no banner), facts, capacity/fees, then role-specific actions. E2
"You're registered" card + Request to leave (dialog), E3 leave-requested notice, E5 completed +
"Watch link copied", E6 full / load error. Organizers: the board's E4 still shows only management,
so by decision organizers see the same league info first, then the menu (incl. Mark completed),
Share / Copy, and rosters with Remove / Approve leave / Dismiss and empty-roster text. Backend
now returns each player's `playingRole` on roster rows. Player-only states (E1-E3) not verified
on-device (the test account organizes the only league).

Originally found (sent to Claude Design as update request #2):
- Organizer view (E4): league info (name, location, ground, start date, format, status,
  description), capacity/fees card, banner/logo, awards, Share / Copy watch link, and a "Mark
  completed" menu row (organizer-only today). Also: completed-league organizer view, empty roster
  ("No players yet"), an error line for failed Remove/Approve, and a "Removing…" in-flight row.
- Player view: league description; a loaded league *without* a banner (most leagues -- only the
  E6 error frame shows a banner-less header); the "Request to leave" entry point for a joined
  player and "Leave franchise" for a franchise owner (E3 only shows the already-requested state);
  Follow's "Following" state; Claim a Franchise when franchises are full; a joined player's own
  payment screenshot link; franchise logo (not only initials) in roster rows; initial loading state.
- Already handled without a design change: player role on roster rows (backend now returns it),
  "paid ₹500" (derived from screenshot + league fee).

## F — Join as player

**Implemented (2026-10-01).** Fee card with Pay via UPI + copy UPI ID (F1); no-UPI-app fallback
with "Copied" + snackbar (F2); screenshot preview with a tick-what-you-see checklist, "Use this"
enabled only when all are ticked (F3/F4, decided instead of receipt detection); upload progress,
Cancel, failed + Retry, attached + Replace/Remove (F5); free league "Joining as / Role" from the
viewer's profile (F6); friendly join-failure banner keyed on the backend's problem `code`, e.g.
CAPACITY_FULL -> "Registration is full." (F7); load failure (F8); fee but no UPI ID (F9). The
fee/UPI card, proof card and checklist sheet live in `PaymentProof.kt` for reuse by screen G.
Verified by rendering each state with fakes on-device (the test account can't join its own league).

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

**Implemented (2026-10-01).** Name card with a square logo tile: dashed "Logo" placeholder, then
initials once a name is typed, then the cropped logo with an edit badge + "Remove logo" (G1/G3/G4).
The name field is a boxed placeholder while empty and switches to the label + underline style once
filled; clearing it shows "Enter a franchise name" (G1). Logo picks go through the crop sheet in
its rounded-square mode with the 32/48dp "At auction and roster sizes" previews (G2;
`PhotoCropSheet(square = true)`). Fee card: "Franchise fee ₹5,000" header, Pay via UPI and a dashed
"Attach payment screenshot / Required" row, which collapses to the compact attached row with
Replace/Remove once the screenshot is in (G1/G3). The screenshot goes through the same
tick-the-checklist sheet as F. Free franchise note (G4). Cards dim to 55% and the button shows
"Claiming…" while submitting (G5). Friendly failure banner keyed on the problem `code`, e.g.
CAPACITY_FULL -> "All franchise slots are taken." (G6). League load failure (G7).
Verified by rendering each state with fakes on-device; G2 shares the C3 crop sheet's code.

Verified live on CPH2487 against the real backend: logo crop and upload, screenshot checklist and
upload with progress, and the LEAGUE_COMPLETED banner from a real claim on a completed league.

Decisions taken where the board has no frame (confirmed by the owner 2026-10-01):
- Uploading and failed screenshot states reuse the compact row: a % overlay with Cancel, or "Upload
  failed" with Retry/Remove.
- The no-UPI-app and no-UPI-ID fallbacks reuse F2/F9's notes.
- A failed logo upload drops the logo and shows "Couldn't upload the logo." in the bottom banner.
- The disclaimer shows once a screenshot is attached, as on G3; G1/G5/G6 omit it.

**Change the design:**
- Button label "Send claim" → "Claim Franchise" to match the app.
- The disclaimer currently reads (per All Screens' own intro paragraph, correctly): *"The claim
  stays pending until the organizer approves it (see E3)"* — but per the approve/reject finding
  above, **there is no pending/approval state in the app at all**. This line needs to change to
  something that doesn't promise a review step that doesn't exist — e.g. just the same
  no-payment-processing disclaimer used on the Join screen.

**Needs your call:** G2's logo preview/crop step — same unbuilt-preview bucket as above.

---

## H — Screenshot viewer

**Implemented (2026-10-01):** H1 dark viewer (top bar with back arrow, fitted image with 6dp
corners, outlined Back pill), H2 zoomed state (top bar and Back hide; a zoom chip and a minimap
with the visible area outlined in gold appear), H3 failed load with Retry. The image now loads via
Coil instead of URL/BitmapFactory, so Retry is a real reload; the https-only check stays. Panning
is clamped so a zoomed image can't be dragged off screen. Verified live on CPH2487 with a real
uploaded proof (H1, H2 via an injected pinch) and an unreachable URL (H3).

Decisions taken where the board has no frame:
- Loading shows a white spinner on the dark background.
- System bar icons switch to white while the viewer is open.
- iOS gets the dark styling, Retry and the https-only check, but not the zoom chip or minimap.

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

**Decided (2026-10-01, owner):**
- **Images upload on Save.** League logo/banner presign needs a league id, and there is no
  pending-upload endpoint. Pick → crop → the preview card shows the local image at once; the upload
  runs as part of Save, with the Save pill showing progress. No backend change; nothing goes live
  before Save. I3's in-form "Uploading 42%" therefore doesn't appear in create mode.
- **Format is a dropdown + Other:** T10, T20, 50 overs, Test, Other (reveals a text field). A stored
  value outside the list loads as Other with its text.
- **Save is enabled once the form is edited and validates on tap:** disabled while untouched
  (create) or unchanged (edit); on tap, missing fields get inline errors, a "N fields need attention"
  banner appears and the form scrolls to the first one (I7, I9).

**Implemented (2026-10-01):** top bar with Save pill (Cancel text in edit mode), the "How it will
look" card (16:9 banner + logo tile + name / city · date), square logo crop and 16:9 banner crop
(I2), Basics, Location (I9), Ground search / selected card (I4), full-screen register-ground map with
draggable green pin and error banner (I5/I11), Schedule & format with the styled date picker (I6),
Capacity, Fees with UPI ID (I7), Awards cards (I8), discard dialog (I10), Saving… / Uploading N% and
the save-failure bar with Retry (I12). The error-count banner stays pinned under the top bar and a
Save tap scrolls to the first missing field's section. A Save retried after the league was created
but an image upload failed updates that league instead of creating a second one. Verified with
fake-data captures for every frame and live on CPH2487 against the real backend (I1, logo crop,
I2, filled card with real images, I10 dialog, I5 real map) -- Save was never tapped live.

Decisions taken where the board has no frame or disagrees with itself (confirmed by the owner 2026-10-01):
- "Basics" heading shown in every state (I1 has it, I3/I10 don't).
- Fields are 52dp with a white fill everywhere (most frames; I1/I7 draw 54dp transparent).
- Location fields stay full width in edit mode too (I9; I10 puts State/District side by side).
- Image actions are Change/Remove for logo and banner. No "Cancel banner upload" (uploads only
  happen inside Save) and no check badge on the logo.
- "How it will look" label shows only while the card is empty, as in I1.
- Register ground: a missing league location or unplaced pin shows in the sheet's error banner;
  the coordinates line reads "Pin not placed yet" until the pin moves; "Registering…" while busy.
- Discard copy: create "This league won't be created."; edit "Your edits won't be saved. The league
  stays as it was."
- Format "Other" reveals a "Format name" field (placeholder "e.g. 8 overs").
- Fees and cash show without thousands separators (I7 shows "5,000", I12 "5000").
- The pre-filled First/Second/Third Prize awards stay (the board shows "Winner").

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

**Implemented (2026-10-02), board J1-J5.** Back-arrow top bar; Base price, Purse, Squad min/max
side by side, Bid increment as mono form fields; amber squad warning (live); Auction pool card with
the empty-franchises line (J3); Save in a bottom bar, greyed while errors show (J2), spinner +
fields at 55% while saving (J3); dark "Auction settings saved" bar (J4, 3s) and save-failure bar
with Retry (J5). Whole amounts pre-fill without ".0". The board's "no locked state" note holds for
the screen, but the server does reject saves once the auction has started -- that shows as its own
message without Retry. Load failure reuses the "Couldn't load" + Retry view. After a successful
Save the screen stays open with the saved bar (owner, 2026-10-02: matches J4, keeps the values and
pool in view for repeat tweaks; Back returns to League detail). Verified on CPH2487 on
a real league: J2 (75k -> "Enter a number", live warning 30 x 5 = 150 > 100, greyed Save, Save tap
-> "Required" on the blank field), J3's empty pool line; field gaps checked against the DOM (the
field's 7dp notch reserve means 5dp spacers for the board's 12dp). Rules decided by the owner, see
PHASE4 Decisions Made.

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

**Implemented (2026-10-02), board K1-K8.** Back-arrow top bar and intro; phone field + Look up
pill (widens to "Looking up…" with a spinner, K6); found card with initials avatar and Grant pill
("Granting…", K7); Grant confirmation dialog (K3); inline not-found error on the field (K4);
current co-organizers card with initials and outlined Revoke pills, now behind a confirmation
dialog, then "Revoking…" in place (K5); grant-failure banner (K8). Messages for undrawn states
(rate limit, already granted, organizer, revoke failure) are in PHASE7 Decisions Made. Rows show
names only, as decided. Load failure reuses the "Couldn't load" + Retry view.

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


**Implemented (2026-10-02), board L1-L10 (design update #3, imported from the Claude Design
project).** Status chip (pulsing Live / Not started / Ended) and "Player 12 of 58" / "11 of 58
done"; On the block card with 88dp photo (gold initials fallback), role chip, current bid with the
leading franchise tile, "Next bid at least" (or "Opening bid at least"); Recent bids (up to 5,
newest highlighted, "12s ago" ticking); docked bid form with "Bidding as … · ₹ left · n/max",
pre-filled Amount, +increment chip, Place Bid, inline errors (L6) and Placing… (L7); organizer
dock: Sold to <franchise> · ₹amount, Unsold / Next Player (disabled while a player is up) / Undo,
Allow exceeding purse, End Auction; between players (L8) with the last result; not started for
organizer / others (L3 / L9); viewer note (L4); loading (L10); Results with expandable franchise
cards, player photos and "Show all n" (L5) plus a coral "below min squad" note the board doesn't
draw. Lost-connection line with Retry after a stream drop. Verified with fake data on CPH2487
(all nine states side by side with the board); live data needs the backend deploy.
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


**Implemented (2026-10-02), board M1-M3.** 28sp "My leagues" header; stacked Organizing / Playing /
Franchise owner / Following sections (empty ones hidden) with white rows: league logo or initials
tile, name, "city, state -- starts", chevron. App fix M2: empty state with Browse leagues
(Dashboard tab) and Create (new league). M3: spinner on first load, then an error card with Retry;
a refresh on re-entry keeps the last list instead of flashing the spinner. Verified on CPH2487
with real data (M1, side by side with the board).
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


**Implemented (2026-10-02), board N1-N5.** 104dp profile photo (initials on green when there's
none, N3) with a camera badge, name, a card of State / District / City / Playing role / Batting
style / Bowling style with humanized labels, Edit profile, Log out behind a confirmation (N4).
Tapping the photo opens the photo sheet (N2): View photo (the existing full-screen viewer, titled
"Profile photo") and Choose new photo (picker -> square crop -> upload -> full-profile save; the
cropped image shows with a progress ring while it uploads, the old photo stays if it fails). No
Remove, no Phone, no Member since (decided). With no photo, the badge and "Add a photo so
organizers recognise you" go straight to the picker. N5 uses the shared load-error view; a failed
refresh keeps the profile on screen. Verified on CPH2487 with real data: N1 side by side, N2
sheet, View photo + Back, N4 dialog + Cancel.
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

## Design update request #2 (2026-10-01) -- screens E to N

Found while implementing (E) and by checking the F-N frames against the app's screens, ViewModels
and data. Three kinds of item: (a) things the app has that no frame shows, (b) design items that
need data the app doesn't have, (c) design items that need a decision. E's list is in its section
above.

**F -- Join as player**
- (a) Failure after tapping Join (league full, rate-limited, network) -- no error frame.
- (a) League failed to load -- no error frame. Missing UPI ID (organizer set a fee but no UPI) --
  no frame.
- (c) F4 "doesn't look like a payment receipt" needs image recognition (OCR/ML) the app doesn't
  have. Drop it, or make it a manual checklist only?
- Build items, not gaps: F2 no-UPI-app fallback; F6 "Joining as / Role" (needs the profile on
  this screen).

**G -- Claim a franchise**
- (a) Free franchise (no franchise fee: no payment step) -- only the paid variant is drawn.
- (a) Claim failure (franchises full, rate-limited, network) and league load failure.
- (a) "Claiming…" in-flight state.

**H -- Payment screenshot viewer**
- (a) Image failed to load ("Couldn't load this screenshot.").

**I -- Create / edit league**
- (a) **Location section (State / District / City + Use my location)** -- required fields, absent
  from every I frame (I1 only shows "City · start date" in the preview).
- (a) Edit mode ("Edit league" title, pre-filled form, cancel/back).
- (a) Ground registration errors ("Set the league's State/District/City before registering a
  ground", "Drag the pin…") and save failure.
- (c) I7 "At least 11 per franchise" -- no such rule exists in the app. Add the rule, or drop the hint?

**J -- Auction settings**
- (a) Auction pool with no franchises yet ("No franchises have claimed yet.").
- (a) "Saving…" state and save success / failure feedback.

**K -- Co-organizers**
- (a) **Revoke** on each current co-organizer (plus "Revoking…") -- the list has no action.
- (a) "Looking up…" / "Granting…" states and grant failure.
- (b) The list shows each co-organizer's phone number -- the API returns only name, user id and
  granted date. Add phone to the API, or drop it from the design? (It is personal data.)

**L -- Live auction**
- (a) Bid rejected (invalid amount, below current bid, over purse) and "Placing…".
- (a) Live but nobody on the block (after Sold/Unsold, before Next Player).
- (a) Not started, as seen by a non-organizer; initial loading.

**M -- My leagues**
- No gaps found (M2 empty state is already an app-fix build item).

**N -- My profile**
- (b) N1 shows the phone number -- the profile API doesn't return it (the backend stores it
  encrypted). Add it to the API, or drop the row?
- (c) N2 "Remove photo": a photo is required for a complete profile, so removing it would make the
  profile incomplete. Keep Remove (and what happens then), or drop it?
- (a) Profile failed to load.

## Follow-ups (todo)

Open items found while implementing the redesign, not yet scheduled.

- [ ] **Share cards: remaining live checks** (2026-10-03, [PHASE13.md](PHASE13.md) §4). Verified: crawler tags
  + S3 logo card on crichere.com, App Link verified and opening on CPH2487, share-sheet text, real WhatsApp preview card. Not yet
  seen: a Facebook preview, a link tapped inside WhatsApp, a real notification tap, release-key
  App Link, iOS (Universal Links not built).

- [ ] **Country-code picker (future scope)** (2026-10-03). Phone entry and the co-organizer lookup now take
  only the national number with +91 added silently. A picker needs: more `Country` entries
  (`PhoneNumberInput.kt`), a picker UI on both screens, per-country display on the OTP screen, and the
  backend `PhoneNumberNormalizer` (India-only by policy) generalised.
- [ ] **10-digit phone input not verified on-device / iOS** (2026-10-03). Android unit + compile only until
  the phone run; the Swift hint changes (`PhoneEntryView`, `ManageRolesView`) are unbuilt; instrumented
  tests (`PhoneEntryScreenTest`, `ManageRolesScreenTest`, `OtpVerifyScreenTest`) updated but not run
  (running them uninstalls the app and wipes the phone's session); `RoleFlowIntegrationTest` needs Docker.

- [ ] **MSG91 OTP not verified against the real service** (2026-10-03, [PHASE12.md](PHASE12.md) §6).
  Phase 0 spike (real account): widget API shapes, no-DLT delivery on Jio/Airtel/Vi, price,
  production permission. `OtpFlowIntegrationTest` unrun (no Docker). On-device: SMS autofill (likely
  absent), switching `OTP_PROVIDER` back to `firebase`. App Check needs the mobile client first.

- [ ] **Uploads go live before Save** (found on screen C, 2026-10-01). Photo uploads use a fixed
  S3 key per owner (`users/{userId}/profile.jpg`, `leagues/{id}/logo.jpg`, ...), so the live image
  is replaced the moment the upload finishes, even if the user then cancels or never taps Save.
  Backend change: upload to a fresh key and only point the record at it on save.
- [x] **Profile Setup subtitle kept on filled forms** (screen C). Resolved by design update #4 (C8-C11):
  the subtitle stays in every state. The name field now reserves a 16 dp error row and shows "Enter your
  full name." after Save with fewer than 2 letters (seen on the emulator, edit mode; the photo row didn't move).
- [ ] **C1 (empty new-user Profile setup) not verified on-device** -- needs a second Firebase test
  number for a fresh account.
- [ ] **Stale refresh token -> 401 after reinstall** (2026-10-01, 10:08 IST). App start presented an
  already-rotated refresh token and the session was wiped, although the previous rotation (04:30
  UTC) had succeeded. Railway's HTTP log showed only 2 requests in that window despite other app
  traffic, so the cause is unconfirmed. Investigate rotation/persistence ordering (e.g. a refresh
  whose response is lost) and consider a short reuse grace window on the backend.
- [ ] **Orphaned test uploads in S3** (screens G/H, 2026-10-01). On-device verification uploaded the
  generated test logo and payment receipt (twice) without attaching them to any franchise or
  player. Delete them, or add a cleanup for uploads that are never referenced.
- [ ] **Not yet verified on-device** -- scenarios built but not exercised on CPH2487:
  - G: success path of a claim (needs a league that is open, with a test account that isn't the
    organizer); CAPACITY_FULL and RATE_LIMIT_EXCEEDED banners; logo-upload failure banner;
    screenshot upload failure (Retry/Remove) and Cancel mid-upload; no-UPI-ID and no-UPI-app
    fallbacks; free league (G4).
  - H (U4 E1, 2026-10-04): the fades now use the board's three stops and fade in on the first zoom, then stay;
    compiled only (no S3 locally, so no screenshot to open on the emulator).
  - H: Retry going through the loading spinner (the test URL failed instantly); opening the viewer
    from League Detail with a real proof (the harness opened it directly); pan clamping by touch
    (covered by tests only); nav-bar icons hard to see when zoomed into a white image (2026-10-04: a dark fade now sits behind the
    system bars while zoomed; compiled, not seen on a device).
  - I: a real Save end to end (create and edit, with logo/banner upload progress against S3);
    Use my location live; ground search and registration against real data; edit mode live; the
    discard dialog at its new 314dp width; Profile setup (C) after the shared field changes (not
    re-captured, its tests pass).
  - iOS: ClaimFranchiseView, ScreenshotViewerView and LeagueCreationView changes are not compiled
    (no Xcode here).
- [x] **Register-ground map opens on all of India** (screen I, 2026-10-01). With no seed the pin
  starts at the country centre and has to be dragged across the map. Consider centring on the
  league's city or the device location (only if location permission is already granted).
  Done 2026-10-02: opens on the league's city (geocoded), see PHASE2 Decisions "fixed centre pin".
- [ ] **Register-ground map: centre pin + search** (screen I, 2026-10-02). Verified on CPH2487 by
  the owner: panning places the pin and updates the coordinates, map opens on the league's city,
  geocoder search ("Komalapuram" -> 9.5315, 76.3413) flies there and sets the location, register.
  Still not verified: Places mode (needs Places API (New) on the key); iOS centre pin (not
  compiled). Known, not fixed: with the keyboard up for the ground name, the sheet covers nearly
  the whole map and the pin is pushed to the top edge (the saved location is unaffected).
  Still open 2026-10-04: any fix changes the sheet while the keyboard is open, so it needs a design call.
  **Design update #4 (I13):** built -- while the name field has focus and the keyboard is up, the sheet is a
  68 dp bar (field + Register, disabled while empty) and the search box, hint and coordinates hide. Seen on the
  emulator, but its keyboard floats (no bottom inset), so the pin position with a real 300 dp keyboard, the
  240 dp minimum map and iOS (I14) are not verified. Simplification: the bar switches with the keyboard rather
  than morphing frame by frame with the IME animation.

- [ ] **Auction settings (J) partly unverified** (2026-10-02): a real Save was confirmed working by
  the owner on CPH2487. Still unverified: the J5 failure bar + Retry live, the
  AUCTION_ALREADY_STARTED message, load failure view, franchises list with real claims, iOS
  AuctionSettingsView (not compiled).

- [ ] **Co-organizers (K) partly unverified** (2026-10-02). Verified on CPH2487 against the real
  backend: empty list (K1 without rows) and a real not-found lookup (K4), both matching the board;
  3 UI tests (lookup found / not found / revoke confirmation) pass on the phone. Not verified live:
  found user + Grant + dialog, Looking up… / Granting… / Revoking… frames, grant failure banner,
  rate-limit message (needs a second registered account; a live grant changes who manages the
  league), iOS ManageRolesView (not compiled). Fixed 2026-10-04 (unit-tested, not run live): a co-organizer who
  revokes their own access now leaves the screen.
  **2026-10-04, design update #4 (PHASE15.md 7.2):** "You" tag, own-access dialog (K9), pop back with the
  snackbar (K10) and the member view (K11) verified on the emulator with a second account; grant + dialog also
  run live there. Not seen: the "Removing…" locked state (too fast locally), a failed self-revoke, iOS.

- [ ] **League detail: Mark completed (U4 E10-E13)** (2026-10-04, PHASE15.md 7.2). Verified on the emulator:
  confirm dialog, failure snackbar with Retry and swipe-away, success snackbar and the completed organizer view
  (no organizer card, "Auction results", read-only rosters). Not seen: "Completing…" (too fast locally), Retry
  reopening the dialog, iOS LeagueDetailView. Open question for design: when the backend refuses because the
  auction is still running (409), the snackbar still says "Check your connection" -- there is no copy for that case.

- [ ] **Live auction (L) polish + live check** (2026-10-02). Open: the On the block card renders
  ~10dp taller than the board (text box heights; not tracked down); Amount shows "15500" where the
  board shows "15,500"; real photos, live bids, recent-bid timing, results reload after a sale,
  and Next Player / Sold / Unsold / Undo / End against the deployed backend not yet checked; iOS
  AuctionLiveView updated but not compiled.
  **2026-10-04 (PHASE15.md):** against a *local* backend on an emulator the organizer flow is now verified
  (live bids, Next Player, Sold, Unsold, Undo of a bid and of a sale, exceed-purse toggle, results, auto-complete,
  Mark completed). Reconnect after a real outage is now verified too (PHASE15.md section 6). Still open:
  End Auction button, photos, the deployed backend, Results showing "₹-250 left" once the purse is exceeded
  (needs design), and the "Amount shows 15500" item is fixed (15,500, Indian grouping). The card-height
  mismatch above is still open (needs measuring against the design).
  **2026-10-04, design update #4 (PHASE15.md section 7):** card height fixed (225 dp measured on the
  emulator, matching U4 A5), "over purse" Results (L11a/b), dead-end cards for organizer and owner (L12a/b,
  both switch variants), dock tiles Leading / Squad full (L13, L14a) and the connection pill (L15-L17) all
  checked on the Pixel_9_Pro AVD against the local backend. Not seen live: the outbid label + haptic (L13b,
  needs a second franchise bidding on the same player), the purse-can't-cover tile (L14b), the over-purse
  dock line (L11c), the Amount field focused / error / 9-digit states (L18), Retry tapped on the pill, the
  real deployed backend, CPH2487. iOS: ported to SwiftUI 2026-10-04 (PHASE15 7.5) but not compiled or run.

- [ ] **My leagues (M) unverified states** (2026-10-02): M2 empty and M3 error not seen on device
  (the test account has leagues); a long city name truncated the start date (fixed 2026-10-04: only the place ellipsizes; not seen with a real long city); iOS empty state added but not compiled.
  U4 M5 (2026-10-04): name up to 2 lines, " · 10 Oct 2026" in the device locale; the " -- " on League detail's
  format line is now " · ". Not seen with a real long name/place.

- [ ] **My profile (N) unverified** (2026-10-02): Choose new photo was not run live (it would replace
  the owner's real photo; covered by VM tests), N3 no-photo and N5 error not seen on device, iOS
  OwnProfileView only got the load-failed state (no photo sheet; not compiled).

- [ ] **Navigation 3 migration (PHASE10.md Part B), not verified on-device** (2026-10-02):
  predictive-back gesture animation (phone uses 3-button nav), deep link cold and warm, new-league
  `replaceTop`, Join/Claim completion, OTP lockout, Edit profile save.
- [x] **Same-number re-send hangs on "Sending code…"** (fixed 2026-10-02, PHASE10.md "Send-code
  hang fix") (found 2026-10-02 via OTP -> back -> Send
  code within 60s; pre-existing via OTP's "Edit"). Firebase gives no callback without a
  force-resend token, and the client has no timeout. See PHASE10.md Open gaps.

- [ ] **Auction settings (J): "Auction date & time" -- now designed (U4 J9-J12, 2026-10-04).** Verified on the
  emulator: helper row, date dialog with past days disabled and today outlined, time dialog colours / title /
  Back keeping the date, the past-time error, clear + "Auction time cleared". Not seen: tapping Undo (the
  snackbar timed out between scripted taps), the passed-time warning with real data, the J5 save failure, iOS.
  Known differences: the helper shows "GMT+05:30" on an en_US phone (CLDR has "IST" only for en_IN); M3 draws
  the hour as "07" in its own type, the board shows "6". Both resolved by U5 J14 / J15 (2026-10-04): the helper is
  "Uses your phone's time zone." and M3's "07" is accepted (digits now in Instrument Sans).

- [ ] **Design update #5 (PHASE15 8), Android** (2026-10-04). Verified on the Pixel_9_Pro emulator against the
  local backend: L19a with 1 and 2 players, L19b dead-end copy, L20 "Ending…" (emulator network delay 5 s), L21
  network snackbar 12 dp above the dock with Retry reopening the dialog, the success path (Ended, no snackbar),
  E14 row + "Live Auction · in progress", E15 race (league page loaded before the auction started; row turned into
  E14 after the reload), J13 empty headline + disabled Next, J14 helper, J15 digits. Not seen: L21 REFUSED copy
  (needs a non-network server refusal), the 0-player body, the E15 snackbar's Open auction being swiped away, the
  "Couldn't complete the league right now" refusal, the ground map 150 ms cross-fade (not filmed), TalkBack on the
  disabled row and the pill, CPH2487, the deployed backend.
- [ ] **(superseded) Auction settings (J): new optional "Auction date & time" field not on the design board**
  (2026-10-02, PHASE11.md D3). Built as a tap field (date dialog -> time dialog, phone's zone,
  stored as an instant) with a clear button. Needs a design pass. Not verified on-device (CPH2487
  wasn't connected): picking, clearing, the 12h display, and the instrumented
  `aScheduledTimeCanBeClearedBeforeSaving` test. iOS `DatePicker` section authored, not compiled.

- [ ] **Public web redesign (PHASE11.md) -- open items and unverified scenarios** (2026-10-02):
  - Store badges use the design's mock glyphs -- swap in the official Google Play / Apple artwork
    before release. Both render "Coming soon" until `NEXT_PUBLIC_PLAY_STORE_URL` /
    `NEXT_PUBLIC_APP_STORE_URL` are set; QR card and the mobile "Open in app" banner stay hidden
    until the Play URL exists (QR generation needs a dependency -- ask first).
  - Landing stats strip and the hero's "Bids placed tonight 412" card: removed (design update #4 H4 --
    no invented numbers on a public page; bring the strip back once there are real ones).
  - Footer: About dropped (H4). U5 B4 (2026-10-04): Contact comes from `NEXT_PUBLIC_CONTACT_EMAIL`
    (`hello@crichere.com`, hidden when unset); no `#` links. Privacy / Terms are built as a placeholder template
    (`/privacy`, `/terms`, noindex) but not linked until the owner supplies the text -- they must be real before
    launch (store listings need them).
  - U5 W5–W9 not verified: Safari / iOS (sticky TOC, `<details>` marker, container queries), a real
    over-purse auction on the real backend, the landing gap with the reveal animation finished at 1280,
    `NEXT_PUBLIC_CONTACT_EMAIL` set on the deployed site.
  - Auction top bar at 360px: fixed (H1) -- white wordmark 22/24px, "Live" / "Offline" / "Get app" below
    480px. Checked in Chromium at 360 and 768 against the mock backend.
  - Not verified against the real backend + phone: a full live auction driven from the app (bids,
    sold, unsold, undo, end) watched on the web, a real network drop, the live-now link with a real
    in-progress league, and Safari/iOS (`linear()` fallback, `backdrop-filter`, container queries).
    All states were verified in Chromium against the mock backend only.
  - The landing "nothing live" state (live-now 204 hides every Watch-live link) has no e2e (the
    mock always returns a live league); unit-level only via `fetchLiveNow` returning null.
  - "Between lots" card: now designed (H2) -- next lot number, last result, progress bar; and the
    "Bidding closed" card when nobody can bid (H3, `canAnyoneBid`). Unit-tested; not seen against a real
    backend or with the reconnect banner over it.

## Decisions made during implementation

- **App start no longer treats transient failures as signed out** (2026-10-01). A refresh that
  fails with a network error/timeout/5xx is retried 4 times (1s/2s/4s backoff), then the splash
  shows "Couldn't connect" + Try again. Only a real "no session" (no token or 401) goes to Sign in.
  Found on-device: ColorOS briefly blocks network for a just-updated app, and the old code dumped
  signed-in users on Sign in.
- **U5 L23 "Waiting for next player" dock overruled** (2026-10-04, owner). The board added a dock state for
  owners between lots; owners keep no dock between lots on Android and iOS, as before.
