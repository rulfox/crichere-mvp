import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

/**
 * In-memory test doubles for every `shared` repository interface (`AuthRepository`,
 * `LeagueRepository`, etc.), used by `shared`'s own unit tests (`commonTest`) *and* `androidApp`'s
 * Compose instrumented tests (`androidTest`).
 *
 * A dedicated module, not a source-set trick, because Gradle doesn't expose one module's test
 * sources to another module's test sources -- `shared`'s `commonTest` and `androidApp`'s
 * `androidTest` are unrelated compilations. Shipping these as a small module instead of
 * duplicating ~550 lines of hand-written fakes in each consumer (the choice `web-viewer/e2e/`
 * made for its own, much smaller, ~100-line backend-shape fixtures) keeps one definition per
 * repository interface.
 *
 * **Full KMP, mirroring `shared`'s target list, not Android-only.** `shared`'s `commonTest`
 * compiles against every target `shared` itself targets (Android + 3 iOS), so a dependency of
 * `commonTest` must resolve for all of them too, or Gradle can't build the dependency graph at
 * all -- an Android-only module here would break `commonTest` resolution outright, not just the
 * already-known, unrelated iOS Kotlin/Native test-name-mangling issue. These fakes have no
 * platform-specific code (plain Kotlin + kotlinx.coroutines), so living entirely in `commonMain`
 * costs nothing.
 *
 * Never a dependency of `androidApp`'s or `shared`'s main/release source sets -- only
 * `androidTestImplementation` (androidApp) and `commonTest` (shared).
 */
kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // AGP 9's KMP library plugin, same shape as shared/build.gradle.kts.
    android {
        namespace = "com.crichere.app.testfakes"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
