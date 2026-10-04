package com.crichere.app.di

import com.crichere.app.auth.AppStartViewModel
import com.crichere.app.auth.AuthRepository
import com.crichere.app.auth.AuthTokenProvider
import com.crichere.app.auth.BackendOtpClient
import com.crichere.app.auth.KtorAuthRepository
import com.crichere.app.auth.OtpVerifyViewModel
import com.crichere.app.auth.PhoneEntryViewModel
import com.crichere.app.ground.GroundRepository
import com.crichere.app.ground.KtorGroundRepository
import io.ktor.client.HttpClient
import com.crichere.app.league.AuctionRepository
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.AuctionViewModel
import com.crichere.app.league.ClaimFranchiseViewModel
import com.crichere.app.league.FranchiseRepository
import com.crichere.app.league.JoinLeagueViewModel
import com.crichere.app.league.KtorAuctionRepository
import com.crichere.app.league.KtorFranchiseRepository
import com.crichere.app.league.KtorLeagueRepository
import com.crichere.app.league.KtorMyLeaguesRepository
import com.crichere.app.league.KtorPlayerRepository
import com.crichere.app.league.KtorRoleRepository
import com.crichere.app.league.LeagueCreationViewModel
import com.crichere.app.league.LeagueDashboardViewModel
import com.crichere.app.league.LeagueDetailViewModel
import com.crichere.app.league.LeagueRepository
import com.crichere.app.league.ManageRolesViewModel
import com.crichere.app.league.MyLeaguesRepository
import com.crichere.app.league.MyLeaguesViewModel
import com.crichere.app.league.PlayerRepository
import com.crichere.app.league.RoleRepository
import com.crichere.app.network.HttpClientFactory
import com.crichere.app.notification.DeviceTokenRepository
import com.crichere.app.notification.KtorDeviceTokenRepository
import com.crichere.app.profile.KtorProfileRepository
import com.crichere.app.profile.OwnProfileViewModel
import com.crichere.app.profile.ProfileRepository
import com.crichere.app.profile.ProfileSetupViewModel
import com.crichere.app.reference.KtorReferenceRepository
import com.crichere.app.reference.ReferenceRepository
import com.crichere.app.reference.ReferenceViewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the un-authenticated client `AuthRepository` uses -- see `HttpClientFactory.createAuthClient()`. */
private val AUTH_HTTP_CLIENT = named("authHttpClient")

/** Qualifier for the raw, no-base-URL client `ProfileRepository.uploadPhoto` uses for the absolute-URL S3 POST -- see `HttpClientFactory.createUploadClient()`. */
private val UPLOAD_HTTP_CLIENT = named("uploadHttpClient")

/**
 * The one `commonMain` DI module (Koin, per ARCHITECTURE.md -- confirmed KMP-standard, Hilt has
 * no KMP support). Registers everything platform-agnostic: the shared `HttpClient`s,
 * repositories, and ViewModels.
 *
 * [ReferenceViewModel]/[PhoneEntryViewModel]/[OtpVerifyViewModel] are registered as plain
 * `factory { }` calls, not Koin's `viewmodel { }` DSL -- that DSL
 * (`org.koin.core.module.dsl.viewModel`) lives in the Android-only `koin-android` artifact, not
 * multiplatform `koin-core` (see task-5-report.md's Deviations). A plain `factory { }` works
 * identically for both consumption paths: Android's `koinViewModel()` resolves any
 * `ViewModel`-typed Koin definition regardless of which DSL registered it, and iOS pulls the same
 * instance via [KoinHelper]'s plain `get()`.
 */
val sharedModule: Module = module {
    // Un-authenticated client: AuthRepository's own calls to /auth/session|refresh|logout. Must
    // stay separate from the authenticated client below -- see HttpClientFactory.kt.
    single(AUTH_HTTP_CLIENT) { HttpClientFactory.createAuthClient() }

    // The raw, no-defaultRequest-base-URL client: ProfileRepository.uploadPhoto's absolute-URL
    // POST straight to S3's presigned uploadUrl -- see HttpClientFactory.createUploadClient()'s
    // doc for why this must stay separate from both clients below.
    single(UPLOAD_HTTP_CLIENT) { HttpClientFactory.createUploadClient() }

    // The shared, authenticated client used by every other repository. `loadTokens`/
    // `refreshTokens` defer to AuthTokenProvider, which is itself unit-tested directly
    // (AuthTokenProviderTest) -- this factory call only wires that up, it doesn't contain the
    // logic.
    single {
        HttpClientFactory.create(
            loadTokens = { get<AuthTokenProvider>().loadTokens() },
            refreshTokens = { get<AuthTokenProvider>().refreshTokens() },
        )
    }

    single { AuthTokenProvider(secureStorage = get(), authRepositoryProvider = { get() }) }

    single<AuthRepository> {
        KtorAuthRepository(
            authHttpClient = get(AUTH_HTTP_CLIENT),
            phoneAuthClient = get(),
            secureStorage = get(),
            authenticatedHttpClientProvider = { get<HttpClient>() },
            deviceTokenProvider = get(),
            deviceTokenRepository = get(),
            // Backend-driven OTP (docs/PHASE12.md). Which provider is live is the backend's call
            // (GET /auth/config); Firebase stays wired and is the fallback.
            backendOtpClient = BackendOtpClient(get(AUTH_HTTP_CLIENT)),
        )
    }

    single<ReferenceRepository> { KtorReferenceRepository(get()) }

    single<ProfileRepository> { KtorProfileRepository(httpClient = get(), uploadClient = get(UPLOAD_HTTP_CLIENT)) }

    single<GroundRepository> { KtorGroundRepository(get()) }

    single<LeagueRepository> { KtorLeagueRepository(httpClient = get(), uploadClient = get(UPLOAD_HTTP_CLIENT)) }

    single<AuctionRepository> { KtorAuctionRepository(httpClient = get()) }

    single<PlayerRepository> { KtorPlayerRepository(httpClient = get()) }

    single<FranchiseRepository> { KtorFranchiseRepository(httpClient = get()) }

    single<MyLeaguesRepository> { KtorMyLeaguesRepository(httpClient = get()) }

    single<RoleRepository> { KtorRoleRepository(httpClient = get()) }

    single<DeviceTokenRepository> { KtorDeviceTokenRepository(httpClient = get()) }

    factory { ReferenceViewModel(get()) }
    factory { PhoneEntryViewModel(get()) }
    factory { (phoneNumber: String, verificationId: String, resendToken: Any?) ->
        OtpVerifyViewModel(
            phoneNumber = phoneNumber,
            initialVerificationId = verificationId,
            initialResendToken = resendToken,
            authRepository = get(),
        )
    }
    factory { AppStartViewModel(authRepository = get()) }
    factory { (isEditMode: Boolean) ->
        ProfileSetupViewModel(
            isEditMode = isEditMode,
            profileRepository = get(),
            referenceRepository = get(),
            locationProvider = get(),
        )
    }
    factory { OwnProfileViewModel(profileRepository = get(), authRepository = get()) }
    factory {
        LeagueDashboardViewModel(
            leagueRepository = get(),
            referenceRepository = get(),
            locationProvider = get(),
            profileRepository = get(),
        )
    }
    factory { (leagueId: String) ->
        LeagueDetailViewModel(
            leagueId = leagueId,
            leagueRepository = get(),
            authRepository = get(),
            playerRepository = get(),
            franchiseRepository = get(),
        )
    }
    factory { (editingLeagueId: String?) ->
        LeagueCreationViewModel(
            editingLeagueId = editingLeagueId,
            leagueRepository = get(),
            groundRepository = get(),
            referenceRepository = get(),
            locationProvider = get(),
        )
    }
    factory { (leagueId: String) ->
        JoinLeagueViewModel(leagueId = leagueId, leagueRepository = get(), playerRepository = get(), profileRepository = get())
    }
    factory { (leagueId: String) -> ClaimFranchiseViewModel(leagueId = leagueId, leagueRepository = get(), franchiseRepository = get()) }
    factory { MyLeaguesViewModel(myLeaguesRepository = get()) }
    factory { (leagueId: String) -> AuctionSettingsViewModel(leagueId = leagueId, leagueRepository = get()) }
    factory { (leagueId: String) ->
        AuctionViewModel(
            leagueId = leagueId,
            leagueRepository = get(),
            auctionRepository = get(),
            authRepository = get(),
            reconnectDelaysMs = AuctionViewModel.STREAM_RECONNECT_DELAYS_MS,
        )
    }
    factory { (leagueId: String) -> ManageRolesViewModel(leagueId = leagueId, leagueRepository = get(), roleRepository = get()) }
}

/**
 * Per-platform dependencies that need something `commonMain` can't provide directly (Android
 * `Context` for [com.crichere.app.storage.SecureStorage], for instance). Also where the real,
 * per-platform `PhoneAuthClient` (backed by [com.crichere.app.auth.FirebasePhoneAuthClient]) is
 * bound to its interface type.
 */
expect val platformModule: Module
