# Technical Architecture

Cross-cutting backend + frontend architecture/coding patterns for the Crichere rewrite — sits alongside [OVERVIEW.md](OVERVIEW.md) (stack choices) and applies to every phase, starting with [PHASE1.md](PHASE1.md). Researched against 2026 sources (not training-data assumptions) on 2026-08-31 — this is a proposal for review, not yet locked. See "Flagged for Your Decision" at the end for the genuine forks; everything else is a research-backed recommended default.

**Last updated:** 2026-08-31
**Status:** locked — all flagged decisions resolved, ready for implementation planning

---

## Backend (Spring Boot Kotlin)

**Deployment target (updated 2026-08-31): local-first, Railway at go-live.** Backend runs via `./gradlew bootRun` against a local/Dockerized Postgres, tested through Testcontainers (automated) and manual API calls (curl/Postman/HTTP-client) during development. Once confirmed working, it deploys to **Railway** (existing account, reused) — Railway remains the settled hosting target, just not used until local testing is done. See OVERVIEW.md's Deployment Strategy section.

**Version/environment:** Spring Boot **4.1.x** (current GA line, shipped on Spring Framework 7). Requires Java 17+ (up to 26), Kotlin 2.2+, Jakarta EE 11, Servlet 6.1 (Tomcat 11 or Jetty 12.1 — **Undertow is dropped in this line**, don't reach for it). Two breaking-change traps to design around from day one, not discover later:
- **Spring Security 7 has CSRF protection on by default** — must be explicitly disabled for a stateless JWT API. Standard for token APIs, but do it intentionally in the security config, not as a surprise 403 during testing.
- **Jackson 3's default serialization behavior shifted slightly** (null handling, number formatting) — verify against tests rather than assuming Jackson 2 defaults still hold.

**Package structure: package-by-feature**, not package-by-layer — e.g. `com.crichere.backend.auth`, `.profile`, `.auction`, each a vertical slice (controller+service+repository together), not horizontal `controllers/`, `services/`, `repositories/` folders. Package-by-layer is only advised for small/throwaway projects. Rule: features don't reach into each other's internals directly — cross-feature calls go through an explicit interface.

**DTO/Entity separation:** never return JPA entities over the wire. **Manual mapping via Kotlin extension functions** (`fun ProfileEntity.toResponse(): ProfileResponse`) — confirmed. Revisit MapStruct only if mapping complexity grows past what's comfortable by hand.

**Error handling:** RFC 7807/9457 `ProblemDetail` — built into Spring, one `@RestControllerAdvice extends ResponseEntityExceptionHandler` returning `ProblemDetail`, never leaking stack traces/SQL/class names. Spring Security's own exceptions fire *before* `@ControllerAdvice` runs, so a custom `AuthenticationEntryPoint`/`AccessDeniedHandler` is needed in the security config too, so auth failures also return `ProblemDetail` instead of Spring's default HTML error page.

**Validation:** Jakarta Bean Validation (`jakarta.validation.constraints.*`) on request DTOs via `@Valid` on controller parameters.

**Serialization: Jackson** — confirmed, not kotlinx.serialization. Battle-tested with Spring, broadest ecosystem/tooling. (Note the Jackson 3 breaking-change caveat above still applies.)

**API versioning:** URL path (`/api/v1/...`) — most-adopted, simplest, most visible.

**Testing:** JUnit 5 + **MockK** (not Mockito — MockK is Kotlin-idiomatic, avoids Mockito's final-class-mocking friction) + **Testcontainers** for real-Postgres integration tests — confirmed.

**Config:** `application.yml` + `@ConfigurationProperties` on a Kotlin `data class` with `val` properties — constructor binding is automatic, no `@ConstructorBinding` needed.

**Logging:** Boot's native structured JSON logging (built-in since 3.4) — no extra library. Micrometer Tracing auto-injects `traceId`/`spanId` into logs when present.

**API docs:** springdoc-openapi — de facto standard, Boot 4.x + Kotlin compatible, actively maintained.

**Injection:** primary-constructor `val` injection only, no field injection — matches idiomatic Kotlin, already implied by the project's existing constructor-injection norm.

**Concurrency note:** Boot 4 supports virtual threads (`spring.threads.virtual.enabled=true`) — gives thread-per-request scalability *without* touching the reactive/WebFlux model at all, so it's compatible with CLAUDE.md's no-reactive rule. Not needed at Phase 1 scale; worth knowing about for later.

### Backend Code Skeleton

```kotlin
// --- Request/Response DTOs ---
data class CreateProfileRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotBlank val state: String,
    @field:NotBlank val city: String,
)

data class ProfileResponse(
    val id: UUID,
    val name: String,
    val state: String,
    val city: String,
)

// --- Entity (never returned directly) ---
@Entity
@Table(name = "profiles")
class ProfileEntity(
    @Id val userId: UUID,
    var name: String,
    var state: String,
    var city: String,
)

// --- Manual mapping (extension function; swap for MapStruct if mapping volume grows) ---
fun ProfileEntity.toResponse() = ProfileResponse(userId, name, state, city)

// --- Repository ---
interface ProfileRepository : JpaRepository<ProfileEntity, UUID>

// --- Service (constructor injection, val-based) ---
@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
) {
    fun getProfile(userId: UUID): ProfileResponse =
        profileRepository.findById(userId)
            .orElseThrow { ProfileNotFoundException(userId) }
            .toResponse()
}

class ProfileNotFoundException(userId: UUID) : RuntimeException("Profile not found: $userId")

// --- Controller ---
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(
    private val profileService: ProfileService,
) {
    @GetMapping("/{userId}")
    fun getProfile(@PathVariable userId: UUID): ProfileResponse =
        profileService.getProfile(userId)
}

// --- Global error handling (RFC 7807) ---
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ProfileNotFoundException::class)
    fun handleNotFound(ex: ProfileNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")
}
```

---

## Frontend (KMP — shared logic, native UI per platform)

**Package/namespace: `com.crichere.app`** — shared `commonMain` module namespace, Android `applicationId`, and iOS bundle identifier.

**Module structure:** standard `commonMain`/`androidMain`/`iosMain` source sets. Push everything possible into `commonMain` — repositories, use-cases, ViewModels — native layers stay thin rendering shells. `expect`/`actual` is the exception now, not the default: reserve it for things Kotlin genuinely can't abstract (platform crypto/keystore backends, a few system APIs).

**Dependency injection: Koin**, confirmed the standard — Hilt is Android-only, no KMP support. Koin 4.x + KSP annotations; module declared once in `commonMain`, Android injects via `koinViewModel()`, iOS pulls from the Koin container through a small helper.

**Networking: Ktor client**, confirmed standard — Retrofit can't run in `commonMain` (needs JVM reflection + OkHttp, unavailable on iOS). One shared API over per-platform engines: **OkHttp on Android, Darwin on iOS** (not the engine-agnostic CIO engine — we want real HTTP/2 and native system network integration).

**Serialization:** kotlinx.serialization — uncontested standard, no caveat.

**Shared ViewModel pattern — mainstream now, not a workaround.** AndroidX Lifecycle ships compiled for `commonMain`; a `commonMain` class extends `androidx.lifecycle.ViewModel` directly, `viewModelScope` and cancellation work out of the box. Android consumes it natively via `koinViewModel()`. iOS wraps it in a small `ObservableObject` mirroring the shared `StateFlow` into a `@Published var` — SwiftUI's binding model doesn't understand `StateFlow` directly.

**Flow → Swift bridging: SKIE**, still required, still standard — not superseded by native Kotlin/Native interop improvements. Converts `Flow` to Swift `AsyncSequence`, `suspend fun` to `async/await`, integrates with SwiftUI's `.task` cancellation. Don't stack it with KMP-NativeCoroutines — redundant/conflicting, pick one (SKIE).

**Secure token storage:** shared `Settings`-style interface across platforms — iOS backed by native Keychain. **Android backend: Preferences DataStore + Tink** — confirmed, not `EncryptedSharedPreferences`. More setup than the `multiplatform-settings` out-of-box default, but matches where Android/Jetpack is heading (Google's been steering apps off SharedPreferences generally) — avoids a migration later. Since `multiplatform-settings` ships `EncryptedSharedPreferences` by default, this means either a custom Android `Settings` implementation backed by DataStore+Tink, or evaluating whether `multiplatform-settings` has (or gets) a DataStore-backed variant — confirm at implementation time.

**Local DB:** not needed for Phase 1 (no offline requirement). Two mature 2026 competitors when it is needed — SQLDelight (longer track record, SQL-first) vs Room 3.0 KMP (shipped March 2026, Google-backed, annotation-based). Deferred, not decided now.

**Testing:** `kotlin.test` in `commonTest` — standard. MockK has no stable multiplatform support; **Mokkery** is the emerging Kotlin-native mocking library for `commonTest` (MockK stays fine for Android-only test code).

### Frontend Code Skeleton

```kotlin
// commonMain
class AuthRepository(private val client: HttpClient, private val settings: Settings) {
    suspend fun verifyOtp(phone: String, code: String): Result<Session> =
        runCatching { client.post("auth/verify") { setBody(OtpRequest(phone, code)) }.body<Session>() }
            .onSuccess { settings.putString("refresh_token", it.refreshToken) }
}

class LoginViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun verifyOtp(phone: String, code: String) = viewModelScope.launch {
        repo.verifyOtp(phone, code)
            .onSuccess { s -> _state.update { it.copy(loggedIn = true) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }
}
```

```kotlin
// androidMain — Compose
@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Button(onClick = { viewModel.verifyOtp(phone, code) }) { Text("Verify") }
}
```

```swift
// iOS — SwiftUI, via SKIE
@MainActor
final class LoginViewModelWrapper: ObservableObject {
    @Published var state: LoginState
    private let vm: LoginViewModel = KoinHelper().shared.get()
    init() {
        state = vm.state.value
        Task { for await s in vm.state { state = s } }   // SKIE: Flow -> AsyncSequence
    }
    func verifyOtp(phone: String, code: String) { vm.verifyOtp(phone: phone, code: code) }
}
```

---

## Decisions Resolved (2026-08-31)

## Testing (updated 2026-09-28)

**Automated:**
- Backend: JUnit5 + MockK + Testcontainers (real Postgres), see Decisions Made below.
- Mobile shared: `kotlin.test` in `commonTest`; Android-only test code may still use MockK.
- Web-viewer: Vitest + React Testing Library for component/unit tests (`*.test.ts(x)`, run via `npm test`); **Playwright** for end-to-end (`web-viewer/e2e/`, run via `npm run test:e2e`). The e2e suite runs against a small local mock backend (`e2e/mock-server.mjs`, fixtures in `e2e/fixtures.mjs`) via Playwright's `webServer` config, not the real Spring Boot backend — deterministic, no Postgres/Docker needed to run it, and it can exercise all three `AuctionStatus` states (`NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`) on demand instead of depending on whatever a real auction happens to be doing. The mock server sets permissive CORS since the SSE stream (`EventSource`) is fetched browser-side, cross-origin — unlike the plain JSON endpoints, which Next's server components fetch from Node and never cross a browser CORS boundary at all.

**Manual/interactive verification — prefer structured output over screenshots, for speed:**
- Web (Playwright MCP tools, used for exploratory/interactive checks outside the automated suite): use `browser_snapshot` (accessibility tree, text) as the default, not `browser_take_screenshot` (image, needs a vision read, much slower per turn). Reach for a screenshot only when the property under test is genuinely visual (colors, spacing, a rendered image) and can't be asserted from the accessibility tree.
- Android (no Playwright equivalent — it only drives browsers): `adb shell uiautomator dump` + parsing the resulting XML for exact tap coordinates/text stays the default for driving and verifying on-device state, same technique used through Phases 5–8. Screenshots stay a last resort for the same reason as web: visual-only properties, not general verification.

---

All four items previously flagged here are now locked, per user confirmation:

1. **Backend DTO mapping**: manual extension functions.
2. **Backend test framework**: JUnit5 + MockK + Testcontainers.
3. **Backend serialization**: Jackson.
4. **Android secure-storage backend**: Preferences DataStore + Tink (not `EncryptedSharedPreferences`).

No open forks remain in this document. Local DB choice (SQLDelight vs Room 3.0 KMP) stays deferred — not needed until a phase requires offline storage.

---

## Sources

Backend: https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/ · https://spring.io/blog/2025/12/18/next-level-kotlin-support-in-spring-boot-4/ · https://www.javacodegeeks.com/2026/05/spring-boot-4-migration-breaking-changes-new-defaultsand-what-actually-broke.html · https://rifaiio.medium.com/spring-boot-project-structure-best-practices-layer-based-vs-feature-based-explained-simply-4a9002f3cff0

Frontend: https://batteriesincluded.io/insights/kotlin-multiplatform-and-compose-multiplatform · https://blog.kotzilla.io/koin-for-kotlin-multiplatform · https://medium.com/@felix.lf/a-guide-to-modern-dependency-injection-in-kmp-with-koin-annotations-dcc086a976f3 · https://itnext.io/ktors-cio-engine-in-2026-can-it-finally-replace-okhttp-and-darwin-8f7b7e7e1553 · https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html · https://medium.com/@fredosuala/kmp-architecture-the-case-for-pure-kotlin-viewmodels-c85ce95499ee · https://skie.touchlab.co/features/flows · https://touchlab.co/encrypted-key-value-store-kotlin-multiplatform · https://medium.com/@math.perroud/from-encryptedsharedpreferences-to-datastore-tink-and-keystore-in-a-kmp-credential-store-e4aef3b6149d · https://docs.bswen.com/blog/2026-03-14-room-vs-sqldelight-kmp/ · https://itnext.io/understanding-unit-testing-in-kmm-with-mokkery-through-unit-tests-xd-7d041ccf53a2
