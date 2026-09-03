package com.crichere.app.di

import com.crichere.app.network.HttpClientFactory
import com.crichere.app.reference.KtorReferenceRepository
import com.crichere.app.reference.ReferenceRepository
import com.crichere.app.reference.ReferenceViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The one `commonMain` DI module (Koin, per ARCHITECTURE.md -- confirmed KMP-standard, Hilt has
 * no KMP support). Registers everything platform-agnostic: the shared `HttpClient`,
 * repositories, and `ReferenceViewModel`.
 *
 * [ReferenceViewModel] is registered as a plain `factory { }`, not Koin's `viewmodel { }` DSL --
 * that DSL (`org.koin.core.module.dsl.viewModel`) turned out to live in the Android-only
 * `koin-android` artifact, not `koin-core`, so it's unresolvable from `iosMain` (caught by
 * actually compiling `:shared:compileKotlinIosSimulatorArm64` on this Windows machine's bundled
 * Kotlin/Native toolchain -- see task-5-report.md). A plain `factory` works identically for both
 * consumption paths: Android's `koinViewModel()` (from `koin-compose-viewmodel`) resolves any
 * `ViewModel`-typed definition through Koin's container regardless of which DSL registered it,
 * and iOS pulls the same instance via [KoinHelper]'s plain `get()`.
 */
val sharedModule: Module = module {
    single { HttpClientFactory.create() }
    single<ReferenceRepository> { KtorReferenceRepository(get()) }
    factory { ReferenceViewModel(get()) }
}

/**
 * Per-platform dependencies that need something `commonMain` can't provide directly (Android
 * `Context` for [com.crichere.app.storage.SecureStorage], for instance).
 */
expect val platformModule: Module
