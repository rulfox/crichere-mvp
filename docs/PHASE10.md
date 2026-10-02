# Phase 10 — Android Navigation (Navigation 3) + Toolchain Upgrade

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions.

**Last updated:** 2026-10-02
**Status:** Part A (toolchain upgrade) implemented and verified on-device. Part B (Navigation 3
migration) in progress.

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

In progress.
