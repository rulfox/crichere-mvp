# Phase 12 — MSG91 OTP (no DLT) alongside Firebase phone auth

Part of the Crichere full rewrite. See [PHASE1.md](PHASE1.md) for the original auth design this
extends (Firebase stays; nothing in it is removed).

**Last updated:** 2026-10-03
**Status:** backend + mobile **code complete, unit-tested, NOT enabled and NOT verified against real MSG91.**
`crichere.otp.provider` defaults to `firebase`, so merging changes nothing for users until it is
flipped. **Phase 0 (live MSG91 spike) has not been done** — it needs a real MSG91 account, which
this session did not have. Do not set the provider to `msg91` before it is done (section 6).

---

## 1. What this adds

A second way to prove phone ownership: the **backend** sends and checks the OTP through MSG91's OTP
widget APIs (default template/sender → no DLT registration). The app learns which mechanism to use
from `GET /api/v1/auth/config`, so switching needs a config change, not an app release.

| | Firebase (unchanged) | MSG91 (new) |
|---|---|---|
| Who sends the SMS | Firebase, called by the app | Backend, called by the app via our API |
| Who checks the code | Firebase (client-side) | Backend, via MSG91 |
| Backend entry | `POST /auth/session` (Firebase ID token) | `POST /auth/otp/send`, `/resend`, `/verify` |
| Session issued by | `/auth/session` | `/auth/otp/verify` directly |
| Account key | `HMAC(E.164 phone)` | same — same hash, same user row |

`/auth/session` and all Firebase code (backend verifier, Admin SDK, mobile Phone Auth clients,
iOS bridge, `ReusingPhoneAuthClient`, dependencies) are **untouched and always available**.

## 2. Decisions made

| Decision | Why |
|---|---|
| **B-server**: backend calls MSG91 widget APIs; the app never talks to MSG91 | In "pure" widget flow the app triggers the SMS directly, so the backend only sees a final token and cannot enforce send limits, India-only, attempt caps or App Check. Owner chose B-server (2026-10-03). |
| Provider chosen **server-side** (`crichere.otp.provider`, env `OTP_PROVIDER`), exposed at `GET /auth/config` | Flip without an app release. App falls back to Firebase if config can't be fetched. |
| Default provider = `firebase` | Merging is a no-op until deliberately enabled. |
| Backend OTP is **opt-in on `KtorAuthRepository`** (`backendOtpClient = null` → Firebase only, no extra network call) | Keeps every pre-existing caller/test on the old path; only `AppModule` wires the client. |
| OTP state in a DB table `otp_challenges` (V19), client holds only our challenge UUID | Counters survive restarts; MSG91's request id never reaches the client. |
| Phone used at verify comes from the challenge row, never the request | A code proven for number A cannot sign in number B. |
| Wrong-code budget spent **before** asking MSG91, via one conditional `UPDATE` | Parallel guesses each pay for an attempt; the DB, not a read-then-write, enforces the cap. |
| App Check token verified manually (JWKS) behind `app-check-required` (default **off**) | Admin SDK 9.9.0 has no App Check API. Mobile doesn't ship App Check yet (it was specced in PHASE1 but no appcheck dependency exists in `mobile/`). |

## 3. Firebase protections → what enforces them now

| Firebase gave | Now |
|---|---|
| SMS region policy (India only) | `PhoneNumberNormalizer`: only `+91` + `[6-9]\d{9}`; rejected before any MSG91 call |
| reCAPTCHA SMS fraud defence | server-side 60s cooldown; per-phone (5/h), per-IP (20/h) send caps; **global daily cap** (`otp-global-daily-cap`, default 2000/UTC day → 503 `OTP_UNAVAILABLE`, error logged) |
| Attempt limiting | 5 wrong codes per challenge then invalidated; single-use; 5 min expiry; max 3 resends. Server's `attemptsRemaining` is authoritative; the app shows it |
| App Check | `X-Firebase-AppCheck` verified on send/resend when `OTP_APP_CHECK_REQUIRED=true` + `FIREBASE_PROJECT_NUMBER` set; fails closed if misconfigured |
| ID-token verification | n/a; identity binding by challenge row (above) |
| Enumeration safety | send/verify behave identically for existing and new numbers; unknown/expired/spent/exhausted challenge = one 410 |

Also: no OTP or full phone number in logs; MSG91 error detail never reaches the client (generic 503);
`MSG91_AUTH_KEY` only in the environment.

**Known limitation:** two parallel first-sends for one number can both pass the cooldown check and
cost two SMS. Bounded by the per-phone budget (5/h) and the global cap; not worth a lock now.

## 4. Env vars (backend)

| Var | Purpose | Default |
|---|---|---|
| `OTP_PROVIDER` | `firebase` or `msg91` | `firebase` |
| `MSG91_TOKEN_AUTH` | Widget token (MSG91 dashboard: OTP > Tokens), sent as the `tokenAuth` header on every widget call (secret) | blank |
| `MSG91_WIDGET_ID` | OTP widget id | blank |
| `MSG91_AUTH_KEY` | Account Auth Key (secret). **Not used by the widget calls** (see section 6, finding A); kept for account-level APIs | blank |
| `OTP_GLOBAL_DAILY_CAP` | max OTP SMS per UTC day, all callers | 2000 |
| `OTP_APP_CHECK_REQUIRED` | require App Check on send/resend | `false` |
| `FIREBASE_PROJECT_NUMBER` | App Check issuer/audience (project *number*, not id) | blank |

## 5. Code map

Backend (`backend/.../auth/`): `OtpAuthController`, `OtpAuthService`, `OtpSender` +
`Msg91OtpSender`, `OtpChallengeEntity/Repository` (+ `V19__create_otp_challenges_table.sql`),
`PhoneNumberNormalizer`, `AppCheckVerifier` (`JwksAppCheckVerifier`), `OtpProperties`,
`AuthRateLimiter` (+ OTP buckets, global daily cap), `AuthRateLimitFilter` (+ `/otp/*` paths),
`AuthService.signInVerifiedPhone` (extracted so both providers share account resolution), new
exception handlers in `GlobalExceptionHandler`.

Mobile (`mobile/shared/.../auth/`): `BackendOtpClient`, `OtpVerification` (+ `OtpProvider`,
`BackendResendToken`, `OtpExpiredException`, `OtpRequestFailedException`), `KtorAuthRepository`
(provider routing; `onSessionEstablished` shared by both paths), `OtpVerifyViewModel` (branches on
`OtpVerification`, trusts server attempts, expired → Phone Entry). `AuthRepository.verifyOtp` now
returns `Result<OtpVerification>`.

## 6. NOT verified — must be done before enabling (Phase 0)

**Findings from the first live attempts (2026-10-03, production):**

- **A. The account Auth Key is rejected by the widget endpoints.** `POST /api/v5/widget/sendOtp`
  with the Auth Key in an `authkey` header returned `403 {"type":"error","message":"Invalid request"}`
  from Railway (static IPs whitelisted) and `{"message":"AuthenticationFailure","type":"error","code":"207"}`
  from a developer PC. So IP whitelisting was not the cause. The sender now sends the **widget token**
  as a `tokenAuth` header instead (`MSG91_TOKEN_AUTH`). That is the credential the dashboard points to
  (OTP > Tokens: "recommended to use a token in OTP Widget") but it is **still unconfirmed with a
  live send**.
- **B. "Retry Time 15 min" on the widget is the OTP lifetime (expiry).** Our challenge expires after 5
  minutes regardless, so a code is never accepted after 5 minutes, but MSG91 itself would still honour
  it for 15. Set the widget expiry to 5 minutes or less if the dashboard allows.
- **C. Demo phone numbers on the widget (fixed OTP `123456`) are a login backdoor** for those accounts
  if that widget is the production one. Keep them on a separate dev/test widget.
- **D. Ops:** production `backend` has static outbound IPs enabled (3 shared IPs, sfo). `OTP_PROVIDER=msg91`
  was set on production while sends still fail closed (503); installed app builds ignore `/auth/config`.

1. **`Msg91OtpSender` is written against an assumed contract.** MSG91 does not publish the widget
   endpoints' shapes. Assumed: `POST {base}/api/v5/widget/sendOtp|retryOtp|verifyOtp`, `authkey`
   header, body `{widgetId, identifier|reqId, otp}`, response `{"type":"success","message":"<reqId>"}`.
   `Msg91OtpSenderTest` checks the sender against a stub of *that assumption only*. With a real
   account, confirm or correct: are these calls allowed server-side with the authkey; request/response
   shapes; OTP length/expiry configuration; MSG91's own attempt/retry limits.
2. **Delivery without DLT** on Jio / Airtel / Vi: arrival time, sender id and message text shown
   (MSG91-generic, not "Crichere"), price per SMS, wallet requirements; and MSG91's written
   confirmation that the default route is allowed for production long-term.
3. **Fallback:** if widget APIs turn out to be client-only, B-server is impossible — stop and revisit.
4. `OtpFlowIntegrationTest` (real Postgres via Testcontainers) is written and compiles but **has not
   been run** — no Docker daemon was available. This includes the atomic attempt-spend test and
   Flyway/JPA validation of V19, and Spring context wiring of the new beans. Existing Firebase
   integration tests were also not run for the same reason (the base test class now also mocks
   `OtpSender`).
5. **App Check** end to end (needs the mobile client to ship App Check first).
6. **On-device** (CPH2487): full MSG91 flow; SMS **autofill likely does not work** (default template
   has no app hash) — a UX regression vs Firebase to check and accept; flipping `OTP_PROVIDER` back
   to `firebase` and confirming login works with no app change.
7. iOS: shared Kotlin compiles for Android only here; iOS build not run (Windows).

## 7. Verification actually done (2026-10-03)

- Backend unit tests: `AuthServiceTest` (19, unchanged, still green), `OtpAuthServiceTest` (20),
  `PhoneNumberNormalizerTest` (5), `Msg91OtpSenderTest` (6) — all pass.
- Mobile `:shared:testAndroidHostTest`: 286 tests, 0 failures (incl. `BackendOtpAuthTest` 13,
  `OtpVerifyViewModelBackendTest` 5; the 4 pre-existing `OtpVerifyViewModelTest`/`PhoneEntryViewModelTest`
  groups unchanged and passing).
- `:androidApp:compileDebugKotlin` and `:compileDebugAndroidTestKotlin` succeed.
