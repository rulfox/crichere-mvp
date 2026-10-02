# Phase 10 — Android Navigation (Navigation 3) + Toolchain Upgrade

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions.

**Last updated:** 2026-10-02
**Status:** Part A (toolchain upgrade) implemented and verified on-device. Part B (Navigation 3
migration) implemented and verified on-device.

---

## 1. Overview

**The bug that started this:** the hardware back button closed the app on every screen, including
while the keyboard was open. `AuthNavHost.kt` navigated with two hand-rolled
`remember { mutableStateOf(destination) }` switchers, so there was no back stack, and only two
screens (League Creation, Ground map picker) registered a `BackHandler`. Everything else fell
through to `ComponentActivity`'s default, which finishes the app. The same design also lost the
current screen on rotation/process death, scoped every `ViewModel` to the Activity, and
hard-coded back targets (League Detail always returned to Dashboard).

The owner chose a real navigation library over per-screen `BackHandler` patches, and the latest
stable versions of everything. Navigation 3 1.2.0 requires `compileSdk 37`, which requires AGP 9,
so the work is split into two separately committed parts:

- **Part A** — toolchain and library upgrade only, no behavior change.
- **Part B** — the Navigation 3 migration on top.

---

## 2. Part A — Toolchain upgrade

### Versions (all checked against live sources 2026-10-02)

| Entry | From | To | Note |
|---|---|---|---|
| Gradle wrapper | 9.5.1 | **9.8.0** | AGP 9.4.1's minimum is 9.6.0 |
| AGP | 8.13.2 | **9.4.1** | |
| Kotlin / Compose compiler | 2.3.21 | **2.4.20** | |
| SKIE | 0.10.14 | **0.10.15** | first release supporting Kotlin 2.4.20 |
| Compose Multiplatform | 1.10.0 | **1.12.1** | bundles Jetpack Compose 1.12.1 |
| CMP Material 3 | 1.9.0 | 1.9.0 | already latest; own version line |
| compileSdk (3 modules) | 36 | **37** | `targetSdk` stays 36 (behavior change, out of scope) |
| Ktor | 3.5.2 | 3.6.0 | |
| kotlinx-coroutines (+test, +play-services) | 1.10.2 | 1.11.0 | |
| JetBrains lifecycle | 2.10.0 | 2.11.0 | dropped `iosX64`, see Decisions |
| activity-compose | 1.11.0 | 1.13.0 | |
| core-ktx | 1.17.0 | 1.19.1 | |
| Tink | 1.19.0 | 1.23.0 | |
| Firebase BOM / google-services | 34.17.0 / 4.4.4 | 34.19.0 / 4.5.0 | |
| maps-compose | 6.4.1 | **9.0.0** | old pin (PHASE2.md) only existed for compileSdk 37 / AGP 9.1 |
| Places | 5.3.0 | **6.0.2** | old pin (PHASE2.md) only existed for Kotlin 2.4 |
| Coil | 3.5.0 | 3.6.3 | |
| Robolectric | 4.16 | 4.17 | |
| androidx.test.ext:junit | 1.2.1 | 1.3.0 | the old `concurrent-futures` conflict didn't recur |
| Compose ui-test-junit4 / ui-test-manifest | 1.10.4 (hardcoded) | 1.12.1 (catalog) | |
| security-crypto | 1.1.0 | removed | unused catalog entry |

Unchanged because already latest: kotlinx-serialization 1.11.0, Koin 4.2.2, DataStore 1.2.1,
play-services-maps 20.0.0, androidx.test core/runner/rules 1.7.0, orchestrator 1.6.1.

### AGP 9 migration

- **Built-in Kotlin (androidApp).** `org.jetbrains.kotlin.android` removed from `androidApp` and the
  root build. The Kotlin version still comes from KGP on the classpath (root `kotlinMultiplatform`
  `apply false` = 2.4.20).
- **KMP library plugin (shared, testFakes).** `com.android.library` + `androidTarget {}` replaced by
  `com.android.kotlin.multiplatform.library` with `kotlin { android { namespace, compileSdk,
  minSdk, compilerOptions } }`.
  - `shared/src/androidUnitTest` renamed to `shared/src/androidHostTest`, enabled with
    `withHostTestBuilder {}` (`isIncludeAndroidResources = true`). The test task is now
    `:shared:testAndroidHostTest`.
  - `buildFeatures.buildConfig` dropped from `shared`: unsupported by the new plugin, and nothing in
    `shared` reads `BuildConfig` anymore.
- **Compose dependencies declared directly.** CMP 1.12 deprecated the plugin's `compose.runtime` /
  `compose.ui` / ... accessors; they're now catalog entries (`compose-runtime`, `compose-ui`, ...)
  at the same coordinates the accessors resolved.

### Decisions made

| Decision | Reasoning |
|---|---|
| **Full toolchain upgrade first, as its own commit (owner, 2026-10-02)** | AGP 9 is a breaking change; isolating it from the navigation rewrite keeps any regression easy to attribute. |
| **`iosX64` target dropped (owner, 2026-10-02)** | JetBrains lifecycle 2.11.0 no longer publishes `iosX64` (Intel-Mac simulator); it was the only library with that gap. Kept: `iosArm64` (devices) and `iosSimulatorArm64` (Apple Silicon simulator). Cost: an Intel Mac can't run the simulator build. |
| **`shared`'s Android namespace is `com.crichere.app.shared`** | AGP 9 fails the manifest merge when two modules share a namespace (was `com.crichere.app`, same as `androidApp`). The namespace only names generated R/BuildConfig classes and `shared` has neither, so Kotlin packages are unaffected. |

### Verification (2026-10-02)

- `:androidApp:assembleDebug` and `:androidApp:assembleDebugAndroidTest` green.
- `:shared:testAndroidHostTest`: 260/260 pass (260 `@Test` declared across `commonTest` +
  `androidHostTest`).
- `:shared:compileKotlinIosArm64`, `:shared:compileKotlinIosSimulatorArm64`,
  `:testFakes:compileKotlinIosArm64` green (Kotlin 2.4.20 + SKIE 0.10.15 klibs).
- `:androidApp:connectedDebugAndroidTest` on the CPH2487: 27/27 pass.
- Manual smoke test on the CPH2487: test-number login (Firebase), Dashboard, League Detail, Live
  Auction (SSE), Edit league → Register ground map picker (Maps Compose 9, camera-driven pin),
  discard dialog, My Profile (Coil photo). No crashes in logcat.

### Open gaps

- **iOS framework link and Swift build unverified** with Kotlin 2.4.20 + SKIE 0.10.15 (no Mac).
- **New deprecation warnings** from the bumped SDKs, left as-is (behavior-sensitive, not an
  upgrade-commit change): `FirebaseMessagingService.onNewToken` override and
  `FirebaseMessaging.token` (`CrichereFirebaseMessagingService.kt`, `FcmDeviceTokenProvider.android.kt`),
  Tink `KeysetHandle.getPrimitive(Class)` (`SecureStorage.android.kt`).
- **Places 6.0.2 autocomplete path not exercised**: the active search provider is `geocoder`
  (PHASE2.md); it compiles against 6.0.2.
- A Gradle 11 deprecation (`Configuration.setVisible`) comes from a third-party plugin, not this
  project's scripts.

---

## 3. Part B — Navigation 3 migration

### What changed

- **Libraries:** `androidx.navigation3:navigation3-runtime`/`-ui` 1.2.0 and
  `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0 (Android-only, `androidApp`), plus
  the kotlinx-serialization plugin on `androidApp` for the route keys.
- **`ui/navigation/AppRoute.kt`:** one `@Serializable sealed interface AppRoute : NavKey` for every
  destination, replacing `AuthDestination` + `MainDestination`. `MainTab` and its back rule live
  here too.
- **`ui/navigation/AppNavigator.kt`:** the four stack operations the app uses (`navigate`, `back`,
  `replaceAll`, `replaceTop`), Compose-free and unit-tested.
- **`ui/navigation/ImeBackGuard.kt`:** a `NavEntryDecorator` that turns back into "close the
  keyboard" while the IME is visible.
- **`AuthNavHost.kt`:** one `rememberNavBackStack(AppRoute.Starting)` rendered by `NavDisplay`, with
  three entry decorators: saveable state, per-entry `ViewModelStore`, and the IME guard. The
  per-screen `*Route` composables are unchanged.

### Back behavior

| Where | Hardware back |
|---|---|
| Any pushed screen (League Detail, Creation, Join, Claim, Auction settings/live, Co-organizers, Screenshot viewer, Edit Profile) | pops to the screen it was opened from |
| My Leagues / My Profile tab | Dashboard tab |
| Dashboard, Phone Entry, first-time Profile Setup (stack roots) | exits the app (system default) |
| OTP Verify | Phone Entry, number kept |
| Keyboard open, anywhere | closes the keyboard only |
| After login / logout | can't return across it: the stack is replaced |

### Decisions made

| Decision | Reasoning |
|---|---|
| **Navigation 3 over per-screen `BackHandler`s (owner, 2026-10-02)** | Fixes back, state restore, ViewModel scoping and hard-coded back targets together; its back stack is a plain list, close to the old sealed-interface pattern. |
| **One back stack, tabs inside the Main entry (owner, 2026-10-02)** | Back from My Leagues / My Profile goes to Dashboard, then exits (standard Material pattern). Matches iOS's single `NavigationStack` (PHASE9.md). The tab is `rememberSaveable` in the entry, so `initialTab` and `screenshotBackTarget` are gone: popping back restores the tab. |
| **Edit Profile back discards silently (owner, 2026-10-02)** | MVP scope; no dirty-state tracking in `ProfileSetupViewModel`. |
| **Auth transitions use `replaceAll`** | Login, logout and the OTP lockout clear the stack, so back can never cross an auth boundary. |
| **OTP's on-screen back / "Edit" pops like hardware back** | Phone Entry keeps the typed number. Only the 5-wrong-attempts lockout replaces the stack with a fresh `PhoneEntry(lockedOut = true)`. |
| **Created league `replaceTop`s its form; edited league pops** | Back from a new league's detail goes where Create was opened from, not into a stale form. An edit returns to the existing detail, which refetches. |
| **Screens still refresh on return** | `NavDisplay` composes only the top entry, so each screen's existing `LaunchedEffect(Unit) { retry() }` runs again when returned to. |
| **`OtpVerify.resendToken` is `@Transient`** | Firebase's `ForceResendingToken` isn't serializable; after a process-death restore, Resend starts a fresh verification. |
| **Notification permission request moved to the host** | Keyed on `Main` being on the stack. Inside the Main entry it would re-fire every time Main came back into composition. |
| **IME guard registered after entry content** | The most recently registered back handler wins, so the guard takes priority over the entry's own handlers (League Creation's discard dialog, map picker) and over `NavDisplay`'s pop, only while the keyboard is visible. Needed because the ColorOS secure keyboard (phone/OTP fields on the CPH2487) doesn't consume back. |

### Security notes

- The back stack is saved in the Activity's saved-instance state. While on OTP that includes the
  phone number and `verificationId`; both stay on the device, and `verificationId` is useless
  without the SMS code. No tokens are put in route keys.
- **Accepted:** restoring an authenticated screen after process death skips the app-start check.
  The session still comes from secure storage, and API 401s go through the existing handling.

### Verification

- `:androidApp:testDebugUnitTest`: `AppNavigatorTest` 9/9 (push/pop, root not handled, duplicate
  push ignored, login/logout `replaceAll`, create vs edit, tab back targets).
- `NavigationBackTest` (instrumented, CPH2487): 3/3. Covers pushed-screen back, root not claimed,
  and keyboard-open back (sent straight to the Activity, like the ColorOS keyboard does) closing
  the keyboard and staying on screen. Full suite 30/30.
- Manual checks on the CPH2487 (3-button nav, real nav-bar back taps), 2026-10-02:
  - Keyboard open on Phone Entry, OTP and the League Creation name field: back closes only the
    keyboard. At the Phone Entry root, the next back exits.
  - OTP -> back -> Phone Entry with the number kept.
  - After login, back from Dashboard exits; it never returns to OTP. After logout, back exits to
    the launcher.
  - My Leagues -> League Detail -> back -> My Leagues -> back -> Dashboard.
  - League Detail -> Auction settings / Co-organizers / Live Auction -> back -> League Detail.
  - Edit league: unedited back pops to Detail; edited back shows the discard dialog, and Discard
    returns to Detail (previously Dashboard).
  - My Profile -> Edit profile -> back -> My Profile tab; photo sheet back closes the sheet only;
    photo viewer -> back -> My Profile; My Profile -> back -> Dashboard.
  - Rotation (forced landscape) on Auction Settings keeps the screen.
  - Process death (Home, `am kill`, relaunch) restores Auction Settings, and back walks the
    restored stack (Detail, then Dashboard).
  - No crashes in logcat.

### Open gaps

- **Re-sending a code to the same number within 60s hangs on "Sending code…"** (pre-existing, now
  easier to reach). Firebase fires no callback for a second `verifyPhoneNumber` on a number with
  a verification still in its timeout window when no force-resend token is passed, and
  `FirebasePhoneAuthClient` has no timeout. Hit on-device: OTP -> back -> Send code again. The
  OTP screen's "Edit" arrow reached the same state before this phase. Fix candidates: pass the
  previous `ForceResendingToken` when re-sending the same number, and/or a client-side timeout.
- adb-injected `KEYCODE_BACK` right after adb-injected text sometimes did nothing while Gboard was
  up. Real nav-bar taps always worked. Treated as an adb injection quirk, not an app bug.
- Not exercised on-device: predictive-back *gesture* animation (the phone uses 3-button nav), deep
  link (`crichere://leagues/{id}`) cold and warm, creating a new league (`replaceTop`), Join /
  Claim completion, the OTP 5-wrong-attempts lockout, and Edit profile *save*.
