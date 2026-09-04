package com.crichere.app.network

/**
 * The backend's base URL, as reached from each platform's app process -- not a single shared
 * hardcoded string, because "localhost" means something different on each side:
 *  - Android emulator: `10.0.2.2` is the special alias the emulator's virtual router maps to the
 *    host machine's `localhost` (see [androidMain]'s `actual`).
 *  - iOS simulator: shares the host's network namespace directly, so `localhost` reaches the
 *    Mac's locally running backend (see [iosMain]'s `actual`).
 *
 * Port 8080 matches the backend's default (`server.port` is unset in `application.yml`, so Spring
 * Boot's default applies) under the `local` profile Task 1 wired up.
 */
internal expect val backendBaseUrl: String
