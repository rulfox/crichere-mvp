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
}
