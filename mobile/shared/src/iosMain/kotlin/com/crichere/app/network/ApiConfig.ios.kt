package com.crichere.app.network

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Debug builds: the iOS simulator shares the host Mac's network namespace directly, so
// "localhost" reaches whatever is listening on the Mac's loopback interface -- including
// `./gradlew bootRun`'s backend. (A physical device would need the Mac's LAN IP instead.)
// Release builds point at the deployed Railway backend. `Platform.isDebugBinary` (Kotlin/Native
// stdlib) reflects the Xcode scheme's Debug/Release configuration used to compile this framework.
@OptIn(ExperimentalNativeApi::class)
internal actual val backendBaseUrl: String =
    if (Platform.isDebugBinary) "http://localhost:8080" else "https://backend-production-f74e7.up.railway.app"
