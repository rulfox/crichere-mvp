package com.crichere.app.network

// The iOS simulator shares the host Mac's network namespace directly, so "localhost" reaches
// whatever is listening on the Mac's loopback interface -- including `./gradlew bootRun`'s
// backend. (A physical device would need the Mac's LAN IP instead; not a concern for this
// toolchain-proof task, which only targets the simulator.)
internal actual val backendBaseUrl: String = "http://localhost:8080"
