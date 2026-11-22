# iOS redesign plan: make every iOS screen look like the Android app

Status: plan only (2026-11-22). Nothing in this doc is built yet.

## 1. Goal and what it reverses

**Goal:** every iOS (SwiftUI) screen looks the same as its Android (Jetpack Compose) counterpart:
same layout, spacing, colours, type, components, copy and states. The two apps should read as one
product side by side.

**Source of truth:** the current Android screens in
`mobile/androidApp/src/main/kotlin/com/crichere/app/ui/`. They were rebuilt from the design boards
(A–N series, commits `a1cf911` … `7a98f3a`, from 2026-10-01). The boards themselves are claude.ai
artifacts that tools can't open, so **Android is the spec**. Where Android and a board disagree,
Android wins (`docs/DESIGN-REVIEW.md`: "Android (Jetpack Compose) is canonical").

**This reverses an owner decision.** Design update #5 C (`docs/PHASE15.md` §8.4, 2026-10-04) chose
"native iOS structure (NavigationStack, inset-grouped List, system alert/dialog…), Crichere
colours", with "other iOS screens later". That never happened for 13 of the 16 screens, and the
owner has now asked for an Android look instead. `Theme.swift`'s header comment and
`docs/OPEN-ITEMS.md` line 37 should be updated when this work starts.

### What "the same" means here (default interpretation)

| Match exactly (visual) | Keep native iOS (platform behaviour) |
|---|---|
| Layout, spacing, corner radii, sizes in dp → pt 1:1 | Interactive swipe-back gesture |
| Colours (every `Color.kt` token) | Safe areas, Dynamic Island / home indicator |
| Fonts: Archivo, **Instrument Sans**, JetBrains Mono | Keyboard behaviour and keyboard types |
| Custom top bars, bottom tab bar, pills, fields, cards | System permission prompts (location, photos) |
| Dialogs and sheets drawn like Android's (custom) | `PhotosPicker` (system photo picker) |
| Snackbars, banners, empty/error/loading states | Share sheet (`ShareLink`), haptics |
| Icons (Android vector drawables, not SF Symbols) | Status bar (light/dark per screen) |
| Copy, word for word | Opening UPI apps / Settings via URLs |

Pickers are the one judgement call: Android uses Material date/time dialogs in board colours
(I6, J10, J11). Default plan: a custom card styled like the Android dialog, containing iOS's
graphical `DatePicker` / wheel time picker tinted Crichere green. If the owner wants the Material
calendar grid redrawn in SwiftUI, add ~1 day.

## 2. Current state (2026-11-22)

The app built and ran on the iPhone 17 Pro simulator for the first time on 2026-11-22, after the
compile fixes listed in §6. Sign in renders, but as a plain form.

| Screen (board) | Android file | Android LOC | iOS file | iOS LOC | iOS today | Gap |
|---|---|---|---|---|---|---|
| Sign in (A, T1) | `PhoneEntryScreen.kt` | 335 | `PhoneEntryView.swift` | 110 | Form | No hero, wordmark, phone field style, terms checkbox, lockout notice |
| Verify code (B) | `OtpVerifyScreen.kt` | 330 | `OtpVerifyView.swift` | 102 | Form | No 6-box code field, sent-to row with Edit, resend row, resent snackbar |
| Profile setup (C, C3, C5) | `ProfileSetupScreen.kt` | 542 | `ProfileSetupView.swift` | 245 | Form | No photo circle row, crop sheet, notched fields, dropdowns, location pill, save bar, back bar |
| Dashboard (D, D2) + tab bar | `LeagueDashboardScreen.kt`, `AuthNavHost.kt` (`MainBottomBar`), `AreaPickerSheet.kt` | 532 + 137 | `LeagueDashboardView.swift`, `AppRootView.swift` | 120 | Form + system `TabView` | No avatar header, near-me pill, area chips, picker sheet, league cards, FAB, empty/location-off states, tinted tab bar |
| League detail (E) | `LeagueDetailScreen.kt` | 1224 | `LeagueDetailView.swift` | 641 | Inset-grouped List (U5 iOS board) | Different structure: needs banner header, facts, capacity/fees card, roster cards, award cards, pills, organizer menu |
| Join as player (F) | `JoinLeagueScreen.kt`, `PaymentProof.kt` | 297 + 536 | `JoinLeagueView.swift` | 90 | Form | No summary row, pay-to card (UPI + copy), proof card, screenshot checklist sheet, free-league summary, join bar |
| Claim franchise (G) | `ClaimFranchiseScreen.kt`, `PaymentProof.kt` | 649 | `ClaimFranchiseView.swift` | 109 | Form | No name card + square logo crop, fee card, proof row, free note, claim bar |
| Screenshot viewer (H) | `ScreenshotViewerScreen.kt` | 338 | `ScreenshotViewerView.swift` | 106 | Custom (partial) | Top bar/back pill, zoom chip, minimap, failed state to align |
| Create / edit league (I) | `LeagueCreationScreen.kt`, `GroundMapPicker.kt` | 920 + 521 | `LeagueCreationView.swift`, `GroundMapPickerView.swift` | 312 + 111 | Form | No top bar with Save pill, "How it will look" preview, logo/16:9 banner crops, error summary, ground search rows, I5 map screen, award cards |
| Auction settings (J) | `AuctionSettingsScreen.kt` | 497 | `AuctionSettingsView.swift` | 201 | Form | No notched fields, squad warning note, pool card, save bar, schedule field + date/time dialogs |
| Co-organizers (K) | `ManageRolesScreen.kt` | 403 | `ManageRolesView.swift` | 150 | Form | No lookup row + pill, found card, co-organizer list with avatars, red banner, 314pt confirm dialog |
| Live auction (L) | `AuctionLiveScreen.kt` | 1513 | `AuctionLiveView.swift` | 918 | Custom dark (U5 L22–L26) | Closest already; align to Android's L1–L10/U4 geometry, dock, dialog, results cards |
| My leagues (M) | `MyLeaguesScreen.kt` | 246 | `MyLeaguesView.swift` | 100 | List | No section style, league rows with tiles, M2 empty state, M3 error card |
| My profile (N) | `OwnProfileScreen.kt` | 443 | `OwnProfileView.swift` | 93 | List | No photo + camera badge, profile rows, photo sheet (N2), initials avatar (N3), buttons |
| Account deletion (D1–D10) | `DeleteAccountScreen.kt`, `DeleteAccountConfirmDialog.kt`, `AccountDeletionUi.kt` | 429 + 209 + 98 | `DeleteAccountView.swift` | 389 | Custom (partial) | Align top bar, cards, bottom bar, dialog to Android |
| Moderation (R1–R13) | `ModerationUi.kt` | 654 | `ModerationViews.swift` | 403 | Forms/List (partial) | Row ⋮ menu, report sheet steps, block dialog + undo snackbar, blocked people, suspended, notice dialogs, neutral logos |
| App start / offline | `AuthNavHost.kt` (`AppStartRoute`) | – | `AppRootView.swift` | – | Basic | Match splash/offline state |

Line counts are only a rough size signal (SwiftUI is terser than Compose); the gap column is from
reading both sides.

### Foundation gaps (block every screen)

1. **Fonts.** Android uses three families: Archivo (headlines), **Instrument Sans (all UI text)**,
   JetBrains Mono (money, phone, OTP). iOS bundles only Archivo and JetBrains Mono and uses SF Pro
   for body. Add `instrument_sans_variable.ttf` (copy from `androidApp/src/main/res/font/`).
2. **Colour tokens.** iOS `Brand` has 8 colours; Android `Color.kt` has 31 plus the auction
   palette. Port all of them under the same names.
3. **Type scale.** Port `CrichereTypography` (display/headline/title/body/label → family + weight)
   so screens say `.crichere(.titleMedium)` rather than hand-picking sizes.
4. **Components.** iOS has only `NoticeBanner`. Android's kit (`CrichereComponents.kt`,
   `CrichereFields.kt`, `PaymentProof.kt`, `PhotoCropSheet.kt`, `AreaPickerSheet.kt`,
   `DropdownSelector.kt`) needs SwiftUI twins first.
5. **Icons.** Android uses Material Symbols vector drawables (`res/drawable/ic_*.xml`, ~90). iOS
   uses SF Symbols, which look different. Convert the used drawables to PDF/SVG assets in an
   `Assets.xcassets` (template rendering) so icons match.
6. **Navigation chrome.** Android draws its own top bars (no Material TopAppBar). iOS uses system
   large titles. Hide the system bar per screen and draw the Android bars, keeping swipe-back
   working (needs the `interactivePopGestureRecognizer` delegate fix when the bar is hidden).

### Tooling gaps (block verification)

1. **`xcodegen` clobbers Info.plist.** `project.yml` points `info:` at `iosApp/Info.plist` with no
   `properties`, so every `xcodegen generate` rewrites the file and drops `UIAppFonts`, the
   portrait lock, the `crichere://` URL scheme, `NSLocationWhenInUseUsageDescription` and the
   display name. Happened once on 2026-11-22 (restored with `git checkout`). Fix: move those keys
   into `project.yml` `info.properties`, or drop `info:` and set `INFOPLIST_FILE`. **Do this before
   anything else.**
2. **No Android reference images.** This Mac has no Android SDK, emulator, Compose `@Preview`s or
   screenshot tests. Pick one:
   - (recommended) install the Android command-line tools + an emulator image, run the Android
     app, and capture every state with `adb exec-out screencap`; or
   - add Roborazzi screenshot tests for each Android screen state (also needs the SDK; gives a
     permanent reference set); or
   - the owner captures Android screenshots on their phone into `docs/ios-parity/android/`.
3. **Login on the simulator.** States after Sign in need a session. The backend picks the OTP
   provider (`/api/v1/auth/config`, MSG91 or Firebase). With MSG91 a real phone gets an SMS; with
   Firebase the stub `GoogleService-Info.plist` won't work. Either way the owner logs in once per
   simulator (the session stays in the Keychain), or a backend test account / local backend
   (`docs/PHASE15.md` §3) is used.
4. **Test data.** Organizer views, live auction, completed league, blocked/suspended states need
   leagues in those states. Use one test league per state (or the local backend).

## 3. Approach

1. **Fix tooling (§2 tooling gaps 1–2).**
2. **Build the design system once** (`iosApp/iosApp/Design/`): tokens, type, icons, components.
   No screen work until it exists, so screens don't each re-invent a pill.
3. **Port screens in user-flow order**, one screen per commit. For each screen:
   - read the Android composable top to bottom; list every state (the board IDs in its KDoc);
   - rebuild the SwiftUI view using the design-system components, same structure and order;
   - keep the existing `*ViewModelWrapper` and shared ViewModel calls (behaviour already matches;
     this is a visual port). Only touch shared Kotlin if Android uses state iOS can't read;
   - build, run, capture the iOS state, compare with the Android capture, fix until they match.
4. **Side-by-side QA pass** over every state, then update the docs.

### Mapping rules

- 1 dp = 1 pt, 1 sp = 1 pt (both are density-independent; iPhone and Android phones are close).
- Compose `Modifier.padding` / `size` / `RoundedCornerShape` → same numbers in SwiftUI.
- `MaterialTheme.colorScheme.X` → the matching ported token, never an iOS system colour.
- Text: same font, size, weight, line height (`lineSpacing = lineHeight − fontSize`), letter
  spacing; same copy.
- `LazyColumn` → `ScrollView` + `LazyVStack` (not `List`/`Form`, which add iOS chrome).
- `AlertDialog` / custom dialogs → a custom overlay dialog component (not `.alert`).
- `ModalBottomSheet` → `.sheet` with custom content, matching detents and drag handle.
- Snackbars → the existing `NoticeBanner`, aligned to `CrichereSnackbar` (I12).

## 4. Phases and order

Sizes: S ≈ ½ day, M ≈ 1 day, L ≈ 2 days, XL ≈ 3+ days of focused agent work.

### Phase 0 — Tooling (S–M)

**Status (2026-11-22):** done — Info.plist is generated from `project.yml` `info.properties`
(verified: fonts, URL scheme, portrait lock, location string survive a regenerate); compile fixes
committed (`05844e2`); `iosApp.xcodeproj/` gitignored (stub `GoogleService-Info.plist` and `build/`
already were); Instrument Sans bundled. Android reference captures: the SDK, emulator, AVD and Gradle
cache live on an external SSD (`PARITY_TOOLS`, default `/Volumes/Aswin_SSD/crichere-android-tools`),
since the Mac's data volume has ~1 GiB free; `androidApp/scripts/parity.sh [boards]` builds there.

**Capture design:** don't log in to production. Android: the instrumented `ParityCaptureTest`
renders each screen composable with `testFakes` repositories in each board state and saves a
full-screen PNG, pulled with `adb` (no test orchestrator: it would clear the files between tests). iOS: each rebuilt screen is a stateless content
view (state + actions); a DEBUG-only parity gallery, opened with the launch argument
`-parity <board-id>`, renders it with the same fixture data built from the shared state classes.
- Fix the `project.yml` / Info.plist clobbering; regenerate; confirm fonts, URL scheme, location
  string survive.
- Commit the 2026-11-22 compile fixes (still uncommitted) separately from the redesign.
- Set up Android references (SDK + emulator, or Roborazzi, or owner screenshots).
- Owner logs in once on the simulator; create/choose test leagues for each state.
- Keep the stub `GoogleService-Info.plist` out of git (gitignore it).

### Phase 1 — Design system (L)

**Status (2026-11-22):** done. `iosApp/iosApp/Design/`: `Tokens.swift`, `Typography.swift` (real
`wght` weights verified on screen), `LeagueUi.swift` (helpers incl. Java-compatible `tileColor`),
`Navigation.swift` (hidden nav bar + swipe-back, `lightStatusBarIcons`), `Icons.xcassets` (99
Android drawables via `scripts/convert_android_icons.py`), `Components/` (Basics, Dialogs, Fields,
Sheets, PaymentProof, PhotoCropSheet, AreaPickerSheet, TabBar), DEBUG gallery (`-gallery 1|2|3`,
`-gallery-dialog|-snack|-crop|-crop-banner|-area`). `DropdownSelector.kt` has no twin: no Android
screen uses it any more (`CrichereSelectField` replaced it).
- `Design/Tokens.swift`: every `Color.kt` colour + auction palette.
- `Design/Typography.swift`: three families + the `CrichereTypography` scale + mono style;
  bundle Instrument Sans; register in Info.plist (via `project.yml`).
- `Design/Icons`: the Material drawables used by the screens as template image assets.
- `Design/Components/`: `CrichereWordmark`, `PrimaryButton` (52pt pill, 85% loading),
  `OutlinedButton`, `NoticeCard`, `Snackbar`/`SnackHost`, `BackTitleBar`, `FlowTopBar`,
  `DiscardChangesDialog`, `DestructiveConfirmDialog` (+ generic dialog shell), `CrichereTextField`
  (notched label, mono, multiline, error), `CrichereTapField`, `DropdownField` (C5 menu),
  `FlowCard`, `InfoNote`, `Pill`/`SmallPill`, `Avatar` (photo or initials), `LeagueTile`,
  `RowDivider`, `LoadError`, `LocationPill`, `BottomActionBar`, custom bottom `TabBar`.
- `Design/Sheets/`: `PhotoCropSheet` (circle, rounded square, 16:9 banner), `AreaPickerSheet`
  (searchable), `ScreenshotCheckSheet`.
- Hidden-nav-bar + swipe-back helper; per-screen light/dark status bar helper.
- A debug-only gallery screen showing every component, to compare with Android quickly.

### Phase 2 — Sign-in flow (M–L)

**Status (2026-11-22):** done (A/T1/B6/D7, B, S1/S2, C).

1. **A Sign in** — hero (first launch, empty + unfocused) collapsing to wordmark header, phone
   field (10 digits, mono), T1 terms checkbox with links, lockout notice, send button states.
2. **B Verify code** — sent-to row with Edit, 6-box code field, error row, resend countdown row,
   resent snackbar.
3. **App start / offline splash.**
4. **C Profile setup** — back bar (edit mode), photo circle + C3 crop sheet, notched fields,
   C5 dropdowns (state, district, role, batting, bowling), location pill, save bar, discard dialog.

### Phase 3 — Main shell (L)

**Status (2026-11-22):** done (tab bar, D, M, N).

1. **Tab bar** — `MainBottomBar`: tinted bar, pill indicator, filled icon when selected.
2. **D Dashboard** — viewer avatar header, near-me pill (idle/locating/active), area chips +
   D2 picker sheet, clear filters / refresh row, league cards (tile, date, format, fee, players
   bar), empty state, location-off banner, extended "Create a league" FAB.
3. **M My leagues** — four sections, league rows, M2 empty state, M3 error card.
4. **N My profile** — photo + camera badge, N2 photo sheet, N3 initials, profile rows, Edit
   profile / Log out, blocked people link, delete account link, deletion-cancelled snackbar.

### Phase 4 — League pages (XL)

**Status (2026-11-22):** done (E incl. R1-R3/R9/D9, F, G, H; H takes an optional title, "Profile photo" from N).

1. **E League detail** — banner header + compact header on scroll, league facts, capacity/fees
   card, follow pill, join/claim block (E1), registered card (E2) + leave dialog, organizer menu
   (E4) incl. Mark completed states (E14), live-in-progress pill, roster cards with player /
   franchise rows, screenshot links, awards section, completed view, moderation end links,
   blocked-organizer notice/banner (R9).
2. **F Join as player** — flow top bar, league summary row, pay-to card (Pay via UPI, F2 copy
   fallback, F9 no-UPI notice), proof card (dropzone / uploading / failed / attached),
   F3/F4 screenshot checklist sheet, F6 free league, F7 failure banner + join bar.
3. **G Claim franchise** — name card with square logo tile + crop, name field states (G1/G3),
   fee card, proof row, G4 free note, G6 claim bar.
4. **H Screenshot viewer** — top bar / back pill, zoom chip, H2 minimap, H3 load failed.

### Phase 5 — Organizer tools (XL)

**Status (2026-11-22):** done (I incl. I5/I11/I16 MapKit registration with place search, J incl. J10/J11 dialogs, K incl. K9). Date/time pickers: Material dialog shell + tinted native picker (`Design/Components/DateTimeDialogs.swift`).

1. **I Create / edit league** — top bar with Save pill (I1/I10/I12), error summary (I7/I9),
   "How it will look" preview with logo + 16:9 banner crops and upload progress (I3), basics,
   I9 location section, I4 ground search + rows, I5 map picker screen (align `GroundMapPicker`),
   I6 date picker, I8 award cards, discard dialog.
2. **J Auction settings** — five notched fields, J2 squad warning, J3 saving fade, pool card,
   schedule field + J10/J11 date/time dialogs (J9–J14 copy), save bar.
3. **K Co-organizers** — lookup row + pill (K1/K2/K4/K6), found card, co-organizer list with
   avatars and "You" tag, K8 red banner, K3/K5 confirm dialog.

### Phase 6 — Live auction (L)

**Status (2026-11-22):** done (rebuilt from `AuctionLiveScreen.kt`; boards L1-L21).

Already a custom dark canvas built to the iOS U5 boards. Align to Android: top bar, status chip
and connection pill, block card geometry (225/251), empty / dead-end cards with breakdown, recent
bids, results cards (L5, over-purse lines), dock (bid form, status tiles, amount field states,
organizer controls, exceed-purse switch, Sold / End buttons), end-auction dialog in dark tokens.

### Phase 7 — Account and moderation (L)

**Status (2026-11-22):** done (deletion boards keyed `AD*` because `D*` is the Dashboard; R4-R13).

1. **Account deletion** D1–D10 — top bar, heading/lead, can-delete / blocked / error states,
   handover rows, bottom bar, delete pill, confirm dialog (`DeleteAccountConfirmDialog.kt`).
2. **Moderation** R1–R13 — row ⋮ menu, report sheet (reason / details / outcome), block dialog
   (R7a–e) + undo snackbar, blocked people screen, suspended screen, moderation notice dialogs,
   neutral logo tiles.

### Phase 8 — QA and docs (M)

**Status (2026-11-22):** done. Side-by-side against 174 Android captures (see §5). Legacy theme removed; iPhone 16e small-screen pass; docs: `docs/PHASE21.md`, `docs/OPEN-ITEMS.md`, `iosApp/README.md`, `Theme.swift` header.

- Side-by-side pass over every state in the inventory below; fix drift.
- Small-screen check (iPhone SE size) and largest iPhone; Dynamic Type at default only
  (Android doesn't scale beyond system font size either — confirm with owner).
- Update `Theme.swift` header, `docs/OPEN-ITEMS.md`, `iosApp/README.md`, and add a
  `docs/PHASE21.md` (or next number) write-up.

**Rough total:** 15–20 working days of agent time, most of it in Phases 1, 4 and 5.

## 5. Verification loop and definition of done

For each screen state:

1. Android capture → `docs/ios-parity/android/<board-id>.png` (e.g. `A1.png`).
2. iOS capture on the iPhone 17 Pro simulator → `docs/ios-parity/ios/<board-id>.png`
   (`xcrun simctl io booted screenshot`).
3. Compare side by side (same device width class). Differences allowed only where §1's
   "keep native" column says so, or where device size differs.

**Done when:**
- every state in the inventory has an Android and an iOS capture that match;
- no iOS screen uses `Form`, `List`, system `.alert`, `TabView`'s default bar or SF Symbols
  where Android has a custom equivalent;
- all text uses the three brand families via the type scale;
- the app builds with zero errors and no new warnings, and the existing flows still work
  (sign in, create league, join, claim, live auction, delete account);
- each screen is its own commit; docs updated.

### State inventory (capture list)

A1–A4, T1a/T1b, lockout notice · B (idle, error, resend cooldown, resent) · splash, offline ·
C (first time, edit, C3 crop, C5 menu open, errors, discard) · tab bar ×3 · D (list, filters,
D2 sheet, near me locating/active, empty, location off) · M1–M3 · N1–N5 · E1–E6, E14–E17,
completed, organizer menu, leave dialog, R9a/R9b · F1–F7, F9 · G1–G6 · H1–H3 · I1–I15 ·
J1–J14 · K1–K9 · L1–L10 + U4 A2–A6 + end dialog/failure · deletion D1–D10 · R1–R13.

**iOS captures (2026-11-22):** 174 boards in `docs/ios-parity/ios/`, rendered by the DEBUG parity
gallery from fixtures (no login needed). **Android captures: 174** in `docs/ios-parity/android/`, from
the instrumented harness (`androidApp/src/androidTest/.../parity/`, run with `androidApp/scripts/parity.sh`
on an emulator set to 402 dp wide, the iPhone 17 Pro's width in points). Screens that take only a
ViewModel are shown by setting its state (`ForcedBoards.kt`); dialogs and sheets a screen keeps to itself
are drawn over that screen (`DialogBoards.kt`); the rest are reached by tapping, scrolling or pinching the
real screen (`ActionBoards.kt`). Photo, logo, banner and proof fixtures load the same sample image on both sides.

Every iOS board has an Android capture. Payment-proof thumbnails (F5, F7, G3, G5), which Android only
draws from a photo the user just picked, are reached by picking one: the test answers the photo picker with
the sample image and accepts the check sheet (`PickedProofBoards.kt`).

**Allowed differences in the captures:** the native pickers inside the date / time dialogs (I6, J10,
J11: iOS calendar and wheel vs Material's), the keyboard (A2, B1, AD6), Android's clipboard chip after
Copy (F2, G3-noupi), the Google map with no Maps key on the emulator (I5, I11, I16), H2's zoom level
(a pinch reaches 2.0x where the iOS board starts at 2.4x), and lines that sit within a point of the width
wrapping one word differently (measured text widths agree to within 1%).

**Not reachable here (need the owner):**
- Anything needing a real `GoogleService-Info.plist` and a signed-in test account: live sign-in / OTP
  send and verify, photo / logo / screenshot uploads, UPI app hand-off, the live auction stream
  (reconnect / lost timing), ground registration and place search against real data, account deletion
  end to end, removed-image notices and suspension as served by the backend.
- Test leagues seeded in each state (announced, auction live, completed, dead end, over purse).
- An iPhone SE-size runtime (only the iPhone 16e, 390 pt, is installed; checked there).

## 6. Compile fixes (2026-11-22, committed in `05844e2`)

First successful iOS build:

- `LocationPermission.swift`: `ObservableObject` conformance.
- `iosAppApp.swift`: `KoinInitKt.doInitKoinIos()`.
- `OwnProfileView`, `PhoneEntryView`, `OtpVerifyView`, `AppRootView`: SKIE `onEnum(of:)` and
  `onReceive` instead of `onChange` on non-Equatable sealed types.
- `FirebasePhoneAuthBridge.swift`: `InvalidOtpCodeException(message:attemptsRemaining:)`.
- `project.yml`: deployment target 17.0 (two-parameter `onChange` is iOS 17+).
- `LeagueCreationView.swift`: `LeagueCreationNavigationEventSaved`, `KotlinDouble?.doubleValue`.
- `LeagueDetailView.swift`: `onHome` init parameter; body split for the type checker.
- `ProfileSetupView.swift`: body split; `.allCases` for SKIE-bridged Kotlin enums.
- `mobile/gradlew`: executable bit.
- Not to commit: `iosApp/iosApp/GoogleService-Info.plist` (stub), `iosApp/iosApp.xcodeproj/`,
  `iosApp/build/`.

## 7. Risks

- **Swipe-back with hidden nav bars** — easy to break; test on every pushed screen.
- **Variable fonts on iOS** — the `BrandFont` descriptor approach must resolve real weights
  (500/600/700/800), not synthesize bold. Verify with the component gallery.
- **Type-checker timeouts** — large SwiftUI bodies already failed twice; keep views split into
  small subviews from the start.
- **Login/test data** — without a logged-in simulator and seeded leagues, most states can't be
  captured; the owner must unblock Phase 0 items 3–4.
- **Board drift** — some Android screens carry fixes not on the boards; always copy Android, not
  memory of the board.
