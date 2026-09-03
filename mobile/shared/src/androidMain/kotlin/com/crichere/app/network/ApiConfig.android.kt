package com.crichere.app.network

// 10.0.2.2 is the special alias the Android emulator's virtual router maps to the host machine's
// loopback interface -- the emulator is its own network namespace, so plain "localhost" would
// resolve to the emulator itself, not the developer machine running `./gradlew bootRun`.
internal actual val backendBaseUrl: String = "http://10.0.2.2:8080"
