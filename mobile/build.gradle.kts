// Root build file: declares plugin versions once (resolved via pluginManagement's
// gradlePluginPortal/google/mavenCentral) without applying them here. Each module
// (`shared`, `androidApp`) applies the ones it needs.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.skie) apply false
    // `apply false` here puts the plugin on every subproject's buildscript classpath without
    // applying it anywhere -- `androidApp/build.gradle.kts` applies it imperatively, and only
    // when `google-services.json` actually exists (see that file for why: the plugin fails the
    // build hard otherwise, and no real Firebase project is connected in this environment).
    alias(libs.plugins.googleServices) apply false
}
