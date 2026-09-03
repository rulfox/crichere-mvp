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
}
