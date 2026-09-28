import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.skie)
}

kotlin {
    // expect/actual classes (SecureStorage, FirebasePhoneAuthClient) are still formally "Beta"
    // per KT-61573 despite being the standard, widely-used KMP pattern for this exact case
    // (platform crypto/keystore access) -- silence the per-file warning rather than let it
    // repeat for every actual declaration.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS framework export: builds an `ios<Arch>` binary framework per architecture, named
    // `Shared`, statically linked so iosApp doesn't need a separate dynamic-framework embed step.
    // SKIE (applied above) hooks into this export to translate Flow -> AsyncSequence and
    // suspend fun -> async/await for the generated Swift API surface. This target configuration
    // is verifiable here (Gradle accepts and models it); the actual framework build/link only
    // succeeds on macOS with Xcode's Apple SDKs, which this environment does not have -- see
    // task-5-report.md for what is and isn't verified.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            // SSE (io.ktor.client.plugins.sse) ships inside ktor-client-core itself, no separate
            // artifact -- confirmed against Ktor's own client-SSE docs before adding a dependency
            // that turned out not to exist for this release. Consumes
            // GET /leagues/{id}/auction/stream (see docs/PHASE5.md).

            implementation(libs.koin.core)

            implementation(libs.androidx.lifecycle.viewmodel)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.koin.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.tink.android)
            // ContextCompat.checkSelfPermission for DeviceLocationProvider.android.kt's runtime
            // permission check -- avoids adding the Play Services location dependency (see that
            // file's doc for why FusedLocationProviderClient wasn't used).
            implementation(libs.androidx.core.ktx)

            // Real Firebase Phone Auth SDK. The BOM/dependency resolve and compile against real
            // classes regardless of `google-services.json`'s presence -- that file only affects
            // *runtime* initialization (FirebaseApp reads generated resources from it), not the
            // compile classpath. See `androidApp/build.gradle.kts` for the conditional plugin
            // application, and `FirebasePhoneAuthClient.android.kt` for what is/isn't verifiable
            // without a connected Firebase project.
            // KT-58759: the KMP source-set `DependencyHandler`'s own `platform()` overload is
            // hard-deprecated (compile error, not just a warning) as of this Kotlin version --
            // `project.dependencies.platform(...)` is the documented replacement.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            // `Task<T>.await()` bridges Firebase's Play-Services-`Task`-based callback APIs
            // (`signInWithCredential`, `getIdToken`) into plain suspend functions.
            implementation(libs.kotlinx.coroutines.play.services)
            // Push notifications (docs/PHASE8.md) -- DeviceToken.android.kt's `actual` reads the
            // current FCM registration token to hand to KtorAuthRepository. Same BOM as firebase-auth.
            implementation(libs.firebase.messaging)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.robolectric)
                implementation(libs.junit4)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.core.ktx)
            }
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    // Namespace com.crichere.app everywhere per Task 5's brief -- shared has no Android
    // resources of its own, so sharing the namespace with androidApp doesn't risk an R-class
    // collision (there's no R class to collide).
    namespace = "com.crichere.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        // Generates BuildConfig.DEBUG, which PlatformModule.android.kt reads to decide between
        // the real FirebasePhoneAuthClient and the debug-only DebugFakePhoneAuthClient -- see
        // that file's doc (task-8-brief.md's "debug-only toggle" for on-device verification
        // without a connected Firebase project).
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.logging.enabled", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

skie {
    // Defaults (Flow -> AsyncSequence, suspend fun -> async/await, sealed class exhaustiveness)
    // are exactly what Task 5 needs -- no per-declaration tuning required for a toolchain proof.
}
