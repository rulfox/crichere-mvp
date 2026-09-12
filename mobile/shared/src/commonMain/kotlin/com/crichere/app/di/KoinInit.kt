package com.crichere.app.di

import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.ClaimFranchiseViewModel
import com.crichere.app.league.JoinLeagueViewModel
import com.crichere.app.league.LeagueDetailViewModel
import com.crichere.app.league.MyLeaguesViewModel
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.reference.ReferenceViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.KoinAppDeclaration

/**
 * Android calls this from `CricherApplication.onCreate()`, passing `androidContext(this)` via
 * [appDeclaration] so `platformModule`'s `get()` calls resolve a real `Context`.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(sharedModule, platformModule)
    }
}

/**
 * Swift-callable entry point -- Swift can't invoke `initKoin { androidContext(...) }`'s trailing
 * Kotlin-DSL lambda directly, and iOS has no `Context` to register anyway. Called once from
 * `iosApp/iosApp/iosAppApp.swift` at app launch.
 */
fun initKoinIos() {
    initKoin()
}

/**
 * Swift-facing pull-through for Koin dependencies: Koin's Kotlin API (`KoinComponent`, reified
 * `get<T>()`) doesn't bridge cleanly to Swift, so this exposes one typed accessor per dependency
 * Swift actually needs, per ARCHITECTURE.md's "KoinHelper wrapper class" note. SKIE exports this
 * as a plain Swift class Swift code can instantiate directly (`KoinHelper().referenceViewModel`).
 */
class KoinHelper : KoinComponent {
    val referenceViewModel: ReferenceViewModel
        get() = get()

    val ownProfileViewModel: OwnProfileViewModel
        get() = get()

    /**
     * [isEditMode] mirrors the Android `koinViewModel(parameters = { parametersOf(isEditMode) })`
     * call site (`AuthNavHost.kt`'s `ProfileSetupRoute`) -- a function, not a `val`, since this
     * dependency is parameterized (see `ProfileSetupViewModel`'s own doc).
     */
    fun profileSetupViewModel(isEditMode: Boolean): ProfileSetupViewModel =
        get { parametersOf(isEditMode) }

    // Phase 3 (see docs/PHASE3.md) -- authored alongside the Android screens per the project's
    // current "keep authoring iOS, verify later" posture, even though no iOS League screen has
    // ever been wired into a nav graph (Phase 2's own iOS League Detail/Dashboard/Creation
    // screens were skipped entirely, see docs/PHASE2.md Section 5). These accessors exist so the
    // unwired Swift views below have a real, compiled entry point to call.
    fun leagueDetailViewModel(leagueId: String): LeagueDetailViewModel =
        get { parametersOf(leagueId) }

    fun joinLeagueViewModel(leagueId: String): JoinLeagueViewModel =
        get { parametersOf(leagueId) }

    fun claimFranchiseViewModel(leagueId: String): ClaimFranchiseViewModel =
        get { parametersOf(leagueId) }

    val myLeaguesViewModel: MyLeaguesViewModel
        get() = get()

    fun auctionSettingsViewModel(leagueId: String): AuctionSettingsViewModel =
        get { parametersOf(leagueId) }
}
