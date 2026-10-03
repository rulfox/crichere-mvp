package com.crichere.app

/** The public web host whose `/leagues/{id}` links are verified Android App Links (docs/PHASE13.md). */
const val LEAGUE_LINK_HOST = "crichere.com"

/**
 * League id from a link the app was opened with, or `null` for anything else (including the plain
 * launcher intent). Takes the URI's parts rather than an `android.net.Uri` so it runs in plain JVM
 * unit tests. Two shapes are accepted:
 * - `crichere://leagues/{id}` -- the custom scheme (docs/PHASE3.md), still used by notification taps.
 * - `https://crichere.com/leagues/{id}` -- the shared watch link, opened in-app via App Links.
 */
fun leagueIdFromLink(scheme: String?, host: String?, pathSegments: List<String>): String? {
    val id = when {
        scheme == "crichere" && host == "leagues" -> pathSegments.firstOrNull()
        scheme == "https" && host == LEAGUE_LINK_HOST && pathSegments.firstOrNull() == "leagues" -> pathSegments.getOrNull(1)
        else -> null
    }
    return id?.takeIf { it.isNotBlank() }
}
