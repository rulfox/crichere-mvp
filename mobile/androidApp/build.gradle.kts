@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Task 6's Firebase Phone Auth wiring needs the real `com.google.gms.google-services` plugin to
// process a real `google-services.json` into the resources FirebaseApp reads at startup -- but
// that plugin fails the build hard (`File google-services.json is missing`) when the file isn't
// present, and no real Firebase project is connected client-side in this environment (same
// category of missing external dependency as the backend's still-absent Firebase Admin SDK
// service account credentials). Applying it conditionally, only when the real file exists, keeps
// the build green here while still doing the real thing the moment someone drops a real
// `google-services.json` into this directory (the location Google's tooling expects it in).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Ground location picker (Phase 2) needs a Google Maps API key, which -- like
// google-services.json above and the backend's Firebase/AWS credentials -- doesn't exist in
// this environment. Read from local.properties (gitignored, per-machine, same file
// `sdk.dir` already lives in) rather than committing a real key; blank means the manifest
// placeholder resolves to an empty string, which lets Maps SDK initialize (and fail
// gracefully at runtime -- a blank/watermarked map, not a crash) instead of failing the build.
val mapsApiKey: String = Properties().apply {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) localProperties.inputStream().use { load(it) }
}.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.crichere.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crichere.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-toolchain-proof"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Runs each @Test in its own instrumentation process -- an app crash or leaked static
        // state in one test can't take the rest of the suite down with it. Real device/emulator
        // only (see docs/ARCHITECTURE.md's Testing section); this is Android's actual equivalent
        // to Playwright's own test-runner orchestration, not a loose analogy.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Push notifications (docs/PHASE8.md). CrichereFirebaseMessagingService lives in this module
    // (not shared/androidMain, where firebase-auth lives) since it's a real Android Service class,
    // not KMP-shareable logic -- see that file's own doc.
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Compose UI instrumented tests (docs/ARCHITECTURE.md's Testing section). Real ViewModels
    // wired to `:testFakes`' Fake*Repository doubles, driving the actual screen composables --
    // no Koin/DI override needed (see each Route composable's `viewModel: X = koinViewModel()`
    // default-argument seam).
    androidTestImplementation(project(":testFakes"))
    androidTestImplementation(compose.uiTest)
    // `compose.uiTest` (Compose Multiplatform's cross-platform test umbrella) doesn't carry the
    // JUnit4 Android rule (`createComposeRule`) -- that's Android-only, published separately as
    // AndroidX's own artifact. Pinned to the same Compose UI version the rest of the graph
    // already resolves to (1.10.4, per `androidApp:dependencies`) so it can't drift.
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-android:1.10.4")
    // Supplies the placeholder ComponentActivity `createComposeRule()` launches into. Must be
    // `debugImplementation` (the app itself), not `androidTestImplementation` (the separate test
    // APK) -- the instrumented app process (com.crichere.app) is what resolves the launch intent,
    // so the Activity has to live in *its* manifest. Got this wrong on the first attempt: with it
    // on androidTestImplementation, the real device failed with "Intent in process
    // com.crichere.app resolved to different process com.crichere.app.test".
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.4")
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestUtil(libs.androidx.test.orchestrator)
}
