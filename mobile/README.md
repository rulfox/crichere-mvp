# Mobile

Kotlin Multiplatform app. Shared business logic/data layer in `commonMain` (Koin DI, Ktor client,
`androidx.lifecycle.ViewModel`s, secure storage), native UI per platform: Jetpack Compose on
Android, SwiftUI on iOS.

See `E:\Documentation\Crichere\ARCHITECTURE.md` for the coding pattern/architecture this project
follows (Koin DI, Ktor client, shared `commonMain` ViewModels, SKIE for Swift bridging, DataStore+Tink
for secure token storage on Android, native Keychain on iOS).

**Package/namespace: `com.crichere.app`** — used for the shared `commonMain` module namespace,
Android `applicationId`, and iOS bundle identifier.

## Structure

```
mobile/
├── shared/      (commonMain + androidMain + iosMain — ViewModels, repos, DTOs, Ktor, Koin, secure storage)
├── androidApp/  (Compose UI, applicationId com.crichere.app)
└── iosApp/      (SwiftUI sources, bundle id com.crichere.app — no .xcodeproj checked in; see iosApp/README.md)
```

## Building/running Android

```
cd mobile
./gradlew :androidApp:installDebug
```

Requires the Android SDK (`local.properties`'s `sdk.dir`, or `ANDROID_HOME`) and a running local
backend (`cd ../backend && ./gradlew bootRun`, against the Docker Compose Postgres). The Android
emulator reaches the host backend via `http://10.0.2.2:8080`.

## iOS

Swift sources are authored in `iosApp/` but there's no Mac/Xcode in this repo's primary dev
environment, so no `.xcodeproj` is checked in yet. See `iosApp/README.md` for how to stand one up.
The Kotlin `commonMain`/`iosMain` side is compile-verified via
`./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosX64`.
