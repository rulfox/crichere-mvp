package com.crichere.app.network

import com.crichere.app.BuildConfig

// Debug builds (emulator, local `./gradlew bootRun`) hit the host machine via the emulator's
// 10.0.2.2 alias; release builds point at the deployed Railway backend.
internal actual val backendBaseUrl: String =
    if (BuildConfig.DEBUG) "http://10.0.2.2:8080" else "https://backend-production-f74e7.up.railway.app"
