package com.crichere.app.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the current foreground [Activity] so [FirebasePhoneAuthClient] can hand one to
 * `PhoneAuthOptions.Builder.setActivity(...)` -- required by the real Firebase Phone Auth SDK for
 * its reCAPTCHA/Play-Integrity fallback UI, but something a KMP `expect`/`actual` class has no way
 * to receive through `commonMain`'s platform-agnostic [PhoneAuthClient] interface (which knows
 * nothing about `android.app.Activity`). `CricherApplication.onCreate()` calls [register] once at
 * startup; nothing else in the app needs to know this object exists.
 *
 * A [WeakReference] avoids ever pinning an `Activity` past its own lifecycle if something held
 * onto a stale reference longer than expected.
 *
 * Public (not `internal`): `CricherApplication.onCreate()` lives in the separate `:androidApp`
 * Gradle module, which cannot see `:shared`'s `internal` declarations the way `commonTest` can
 * see `commonMain`'s (module-scoped visibility, not source-set-scoped).
 */
object CurrentActivityTracker {

    private var current: WeakReference<Activity>? = null

    fun currentActivity(): Activity? = current?.get()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    current = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (current?.get() === activity) current = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
