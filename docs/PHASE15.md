# Phase 15 — Local end-to-end auction test (emulator + local backend)

**Last updated:** 2026-10-04 (design update #4, section 7)
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

---

## 6. Fix pass (2026-10-04, same day)

Scope chosen by the owner: the auction findings above plus the older mobile UI defects from DESIGN-REVIEW
Follow-ups. Items that need a design decision were skipped on purpose (below).

| Item | Result |
|---|---|
| **A franchise cannot outbid itself** | Decision: block it. `AuctionService.placeBid` throws `AlreadyLeadingException` (409 `ALREADY_LEADING`) under the league row lock; the app also refuses locally ("You already have the leading bid.") without a request. The bid-ticker integration test used a self-raise and now alternates franchises. |
| **Dead-end hint for the organizer** | Decision: hint, not auto-end. `AuctionStateResponse.canAnyoneBid` is `false` while the auction runs, players are pending and no active franchise can open a bid (squad full, or purse below base price with exceeding off). The between-players card reuses the existing notice text slot. **Copy is mine, not from Claude Design: send it for review.** Verified live. |
| **Silent dead stream** | Found while verifying reconnect. The SSE client does not surface comment-only frames, so the old `: keep-alive` was invisible to the app, and Ktor's OkHttp engine ignores a per-request socket timeout for SSE: a connection that died without an error hung forever, with no banner. Decision: the 15 s heartbeat now re-sends the league's latest state as a normal `auction-state` event (no protocol change; the web viewer already ignores repeats), and the app treats 40 s of silence as a drop (`Flow.timeout`). Because that timeout is a `CancellationException`, the ViewModel's loop now stops only when its *own* scope is cancelled (`ensureActive`). Verified live: banner about 24 s after killing the backend, gone and updating again after it restarts. |
| Amount field shows `15,500` | Indian grouping drawn by a visual transformation (`AmountGrouping.kt`, tested incl. caret mapping); the typed text stays plain digits. Verified on the emulator. |
| My leagues: long city no longer hides the start date | Place and date are separate texts; only the place can ellipsize. Not seen with a real long city (needs one). |
| Co-organizer who revokes themselves leaves the screen | `ManageRolesViewModel` gets the signed-in user; if the response no longer lists them as organizer or co-organizer, `accessRevoked` pops the screen. Optional constructor parameter (tests and the instrumented tests unchanged). Unit-tested; not run live (needs a second login with a granted role). |
| Screenshot viewer: system-bar icons on a white image | While zoomed, a soft dark fade sits behind the status and navigation bars. Compiles; not seen on a device. |
| Emitter cleanup in `AuctionBroadcastService` | Read, no bug: dead emitters are removed on send failure and on complete/error/timeout. One note: an emptied per-league list stays in the map (removing it would race with a new subscriber). |

**Skipped, needs Claude Design / a decision from the owner**
- "₹-250 left" on Results (item: show "over purse"?) and a confirm dialog on **Mark completed** (the owner chose to skip; both need design).
- **On the block card ~10 dp taller than the board**: needs measuring against the design source, which isn't available here.
- **Register-ground map: keyboard covers the map**: any real fix changes the sheet layout while the keyboard is open (hide the coordinates, collapse the sheet, or move the pin area), which is a design call.
- Data-integrity group (uploads live before Save, stale refresh token 401, orphaned S3 uploads) and the design/content group were not part of this pass.

**Not verified:** the amount transformation was seen on the emulator only (one device); iOS untouched (nothing compiles here).

---

## 7. Design update #4 (2026-10-04)

Claude Design answered the skipped items with `Crichere Update 4.dc.html` (project "Crichere KMM Mobile App
Design"). Values were taken from the rendered file's DOM (computed styles), not from screenshots.

### 7.1 Live auction (A1-A6)

| Item | Built |
|---|---|
| A1 over purse | Results card: line 2 "⚠ ₹250 over purse" in coral, coral border, Purse / Spent footer when expanded. Dock: "₹250 over purse" may wrap to 2 lines. `overPurseAmount()` never lets a negative number reach the screen. |
| A2 nobody can bid | Backend `AuctionStateResponse` gains `franchisesTotal`, `squadsFull`, `purseBelowBase` (counted under the same rules as `placeBid`; zero unless `canAnyoneBid` is false). Organizer card: headline, body variant (all full / all purse / mixed), breakdown rows; End Auction filled coral; switch highlighted gold only when purses block. Owner / spectator: neutral "Bidding has closed"; the owner's dock shows their own reason (tile). Live dot holds still. Replaces the interim hint from section 6. |
| A3 dock tiles | `AuctionState.dockMode`: squad full > leading > purse can't cover (base price between players). Tile replaces the Amount field at the same 54 dp; buttons inert. Outbid: "Outbid · ₹X" inside the field for 2 s + one haptic tick. |
| A4 connection | Pill beside the status chip (no layout shift). `AuctionViewModel.connectionPhase`: 1 s grace, Reconnecting, Connection lost + Retry 30 s after the drop, Back online 1.5 s. Content at 50% and actions disabled while down. "updated Ns ago" counts from the last stream event (heartbeats included). |
| A5 block card | Rebuilt to the stated geometry: explicit line heights, no font padding, fixed 40 dp bid row, name 22/26. Measured 225 dp on the emulator (was ~235). |
| A6 Amount field | Idle / focused / error / disabled styles; digits only, max 9. |

Decision: the whole "Connection lost" pill is the Retry target (the design asks for 48 dp via padding;
padding would grow the 26 dp row).

Tests: `AuctionServiceTest` (counts), `AuctionPresentationTest`, `AuctionViewModelTest` (dock mode, dead-end
owner reason, pill timeline, Retry). `viewModelTest` now prints a failing body's real error: a coroutine
still pending after a failure resumes after `resetMain()` and runTest reports only that DispatchException.
`backend/e2e/act.mjs` drives single auction actions/bids for checking screens.

Verified on the emulator and not verified: see DESIGN-REVIEW Follow-ups, "Live auction (L)".
