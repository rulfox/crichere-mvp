# Phase 21: iOS redesign (iOS matches the Android app)

Date: 2026-11-22. Plan and rules: `docs/IOS-REDESIGN-PLAN.md`. Android is the source of truth; every
iOS screen was rebuilt from its Android composable (dp/sp/colours/copy copied exactly), not from the
older iOS "native structure" direction (design update #5 C), which this reverses.

## What changed

- **Tooling (Phase 0).** `Info.plist` is generated from `project.yml` `info.properties`, so
  `xcodegen generate` no longer drops the fonts, URL scheme or location string. `iosApp.xcodeproj/`
  is gitignored. `scripts/sim.sh build | run | shot | parity <boards…>` builds, launches and captures.
- **Design system (Phase 1)** in `iosApp/iosApp/Design/`: tokens, the three font families with
  Android's line heights, 99 Android drawables as template images, and components mirroring
  `CrichereComponents.kt` / `CrichereFields.kt` / `PaymentProof.kt` / `PhotoCropSheet.kt`. Dialogs,
  sheets and menus are drawn (an overlay presenter), not `.alert` / `.sheet`. New in this round:
  `DateTimeDialogs.swift` (Material dialog shell around a tinted native `DatePicker`), the
  `swipeBackBlocked` guard (an edited form intercepts Back like Android's `BackHandler`).
- **Screens (Phases 2-7), one commit each:** Sign in, Verify, splash/offline, Profile setup, tab bar +
  Dashboard, My leagues, My profile, League detail, Join (F), Claim franchise (G), Screenshot viewer
  (H, with the "Profile photo" title), Create/edit league (I, incl. the full-screen MapKit ground
  registration with place search), Auction settings (J), Co-organizers (K), Live auction (L),
  Account deletion (D2-D6), moderation (R4-R13: report sheet, block dialog + Undo bar, Blocked
  people, Suspended, removed-image notices).
- **Cleanup (Phase 8).** The pre-redesign `Brand` / `AuctionPalette` / `BrandFont` theme, the
  navigation-bar appearance, the old notice banner and an unused JPEG downscaler are gone.

## Kept native (by design)

Edge-swipe back (blocked only while a form is dirty), safe areas, the keyboard, system permission
prompts, `PhotosPicker`, the share sheet, haptics, the status bar, `SFSafariViewController` for the
legal pages (Android: Custom Tabs), MapKit for the ground map (Android: Google Maps), and the
calendar / time wheel inside the Material-style date and time dialogs.

## How it was verified

- Builds with no errors after every commit (`scripts/sim.sh build`, iPhone 17 Pro simulator).
- A DEBUG parity gallery (`Design/Debug/ParityGallery.swift`, launch argument `-parity <board>`)
  renders each screen's stateless `<X>Content` view from fixtures, so every board state is reachable
  without signing in. 174 captures are in `docs/ios-parity/ios/` (half resolution).
- Small screen: the dense boards (Sign in, League detail, Claim, Live auction dock) re-checked on the
  iPhone 16e (390 pt; no SE runtime installed). Nothing truncates or overlaps.

## Not verified

- **Android reference captures** (`docs/ios-parity/android/`): blocked, the Mac has ~1.6 GB free and
  the Android SDK + emulator needs 5-8 GB. Comparison was against the Android source, not pixels.
- **Live flows** need a real `GoogleService-Info.plist` and a signed-in test account with seeded
  leagues: sign in / OTP, uploads, UPI hand-off, the auction stream, ground registration against the
  backend, account deletion. The views are the same ones the gallery renders, wired to the shared
  ViewModels.
- Dynamic Type beyond the default size (Android doesn't scale past the system size either).

## Board ids

Gallery keys follow the design boards. Two exceptions: account deletion uses `AD2`-`AD6e` (the
Dashboard owns `D*`), and extra states carry a suffix (`G3-uploading`, `L16-lost`, `I4-selected`).
