package com.crichere.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crichere.app.ui.AuthNavHost
import com.crichere.app.ui.theme.CrichereTheme

class MainActivity : ComponentActivity() {

    /**
     * Deep-link target from a `crichere://leagues/{id}` intent, read once at `onCreate` (cold
     * start) or updated by [onNewIntent] (already running) -- see [AuthNavHost]'s own doc for how
     * this is consumed once `Main` is reached. Backed by `mutableStateOf` (not a `ViewModel`)
     * since `MainActivity` itself is the natural owner of "what intent did we start/resume with".
     */
    private var pendingDeepLinkLeagueId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The theme is light-only, so system bars always get dark icons -- the default "auto" style
        // follows the phone's dark mode and put white icons on light screens. Sign in's green hero
        // flips them to light itself.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        pendingDeepLinkLeagueId = leagueIdFrom(intent)
        setContent {
            CrichereTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthNavHost(pendingDeepLinkLeagueId = pendingDeepLinkLeagueId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leagueIdFrom(intent)?.let { pendingDeepLinkLeagueId = it }
    }

    /** `crichere://leagues/{id}` -> `{id}`, or `null` for any other intent (including the plain launcher intent). */
    private fun leagueIdFrom(intent: Intent): String? {
        val uri = intent.data ?: return null
        if (uri.scheme != "crichere" || uri.host != "leagues") return null
        return uri.pathSegments.firstOrNull()
    }
}
