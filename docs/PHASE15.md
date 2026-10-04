# Phase 15 — Local end-to-end auction test (emulator + local backend)

**Last updated:** 2026-10-04
**Status:** done. One full run: organizer on an Android emulator logs in, creates a league, sets auction
settings, adds a co-organizer, runs a live auction to completion and marks the league completed, against a
local backend and a fresh local Postgres. It found and fixed a real app bug (SSE cut after 15 s, section 4).

---

## 1. Why / scope

Nothing had driven the real backend end to end from the app (DESIGN-REVIEW "Live auction (L)" was only checked
against mocks). Scope chosen 2026-10-04: **organizer UI on an AVD**; the other 14 users, the ground, franchise
claims, player joins and every bid are done by scripts through the real API. Franchise-owner and player
*screens* are not covered (one login only).

## 2. Decisions made

| Decision | Why |
|---|---|
| **`-Penv=local` Gradle property** (`prod` default) sets `BuildConfig.BACKEND_BASE_URL`; `CricherApplication` passes it to `configureBackendBaseUrl()` | A named environment instead of editing `ApiConfig.kt`. `local` = `http://10.0.2.2:8080` (emulator's alias for the host). A release build with `-Penv=local` fails the Gradle build. iOS stays on prod. |
| **`LocalFakeOtpSender`**, active only under Spring profile `local` | Production OTP is MSG91 and its keys exist only in the deployed env. The fake lets the real `/auth/otp/send` + `/verify` flow run locally: fixed code `123456`, only for numbers in `crichere.otp.local-fake.numbers` (default `+917293318484`). `local` profile defaults `crichere.otp.provider` to `msg91` so the app uses that flow. No prod secret is copied to the dev machine. |
| Users seeded straight into the DB, tokens forged with the committed `local` JWT secret | No login backdoor added to the backend. `backend/e2e/lib/crypto.mjs` re-implements `PhoneCryptoService` (HMAC lookup hash + AES-GCM) and `JwtService`; a seeded organizer row was matched by the real login, which proves the hash matches. |
| Bids come from `bidder.mjs` (forged owner tokens) | The app lets only franchise owners bid; only the organizer login exists. |

## 3. How to run

```
cd backend && docker compose down -v && docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=local"          # port 8080
node backend/e2e/seed.mjs                                           # 15 users + ground "E2E Test Ground"
emulator -avd Pixel_9_Pro; cd mobile && ./gradlew :androidApp:installDebug -Penv=local
# in the app: log in with 7293318484 / 123456, create the league (2 franchises, 10 players, ground E2E Test Ground)
node backend/e2e/join.mjs "<league name>"                           # 2 franchises + 10 players via API
# in the app: Auction settings, Manage co-organizers (9000000001), Live Auction -> Start
node backend/e2e/bidder.mjs "<league name>"                         # bids for both franchises while you drive the UI
```
Emulator quirks: the on-screen keyboard's autofill can fill fields you didn't touch (it set Format and a UPI id),
and a swipe that ends on a picker row selects it. Photos/banners can't upload locally (no S3), so a banner or
logo picked by accident must be removed before Save.

## 4. Results (2026-10-04)

Verified on the emulator, with DB cross-checks:
- OTP login, seeded profile accepted (dashboard directly), create league with seeded ground, Back -> "Discard changes?" dialog.
- Auction settings save (100 / 1000 / squad 2-5 / increment 50), co-organizer lookup by phone, grant confirm, listed, `league_roles` row.
- Live auction: start, Next Player, live bids on screen, **Undo of a bid** (leader reverts, bid marked reversed),
  **Sold**, **Undo of a sale** (player back to pending, lot reopened), **Unsold** (player re-queued), Allow exceeding
  purse on/off, 10 players sold -> auto-complete, Results screen, Mark completed. DB: Pune Panthers 5 players / 500,
  Mumbai Mavericks 5 / 1,250; 26 bids, 1 reversed; `live-now` 204 after the end.
- Engine rules seen live: `SQUAD_FULL` (Pune at 5/5) and `PURSE_EXCEEDED` (Mumbai) came back as 409 with the
  right codes; the last player could only be sold after turning on exceed-purse (the intended escape hatch).

**Bug found and fixed — live auction stopped updating after ~15 s.** `HttpClientFactory` applies a 15 s
`requestTimeoutMillis` to every request, including the SSE stream, so the stream died 15 s after the screen opened
and the app neither showed "connection lost" nor reconnected (reopening the screen showed the correct state).
Fix: the stream request opts out of the timeouts (`AuctionRepository.streamAuctionState`), and
`AuctionViewModel` now reconnects with 2/4/8/15 s backoff (same as the web viewer) and sets `connectionLost`
while down. Reconnect is off by default in the ViewModel (tests with a completing fake stream would loop under
`advanceUntilIdle`); `AppModule` turns it on. Verified live: updates arrive 25 s and 50 s after opening.

**Backend log noise fixed:** every client disconnect from the SSE stream logged a 500 plus
`No converter for ProblemDetail ... text/event-stream`. `GlobalExceptionHandler.handleClientDisconnected` now
handles `AsyncRequestNotUsableException` quietly.

Unit tests added: `LocalFakeOtpSenderTest`, `GlobalExceptionHandlerDisconnectTest`, three reconnect cases in
`AuctionViewModelTest`.

## 5. Not verified / open

- Reconnect after a real drop (kill the backend while the screen is open) was only unit-tested, not run live.
- Franchise-owner and player screens, claim/join UI, the End Auction button (the run ended by selling out), the
  "league completed" state's other screens, and the scheduled-time picker were not exercised.
- Cosmetic: after exceeding the purse the Results card reads "₹-250 left".
- Only one run, one device (Pixel_9_Pro AVD, API 36); no Firebase path (the app used the MSG91-style flow).
- Scripts are not a regression suite yet: they seed, join and bid, but assertions were made by hand with
  `psql`/screenshots. A scripted assertion pass is a possible follow-up.
