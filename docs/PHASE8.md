# Phase 8 — Push Notifications (FCM, Android)

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and
[PHASE3.md](PHASE3.md) for the deferred decision this phase resolves.

**Last updated:** 2026-09-13
**Status:** implemented (backend + Android), tested, and verified on-device with a real Firebase
Cloud Messaging round trip. Backend `./gradlew test` and mobile `:shared:testDebugUnitTest` are
green; `:androidApp:assembleDebug` succeeds. On-device: granted the notification permission,
signed in for real (a genuine FCM registration token was fetched from Google Play Services and
stored in `device_tokens`), then triggered a real notification-worthy event against the live
backend and confirmed a real system notification arrived with the correct title, body, channel,
and deep-link tap intent -- see this doc's own commit history for the exact verification. iOS gets
no Firebase Messaging wiring this phase (see Decisions Made) -- `FcmDeviceTokenProvider` there
always returns `null`, a real (not placeholder) implementation of the shared contract.

---

## 1. Overview

Every event in this app so far is "silent-first" — you find out something changed by opening the
app and looking, per `docs/PHASE3.md`'s explicit deferral. That's fine for most things, but a few
events are genuinely time-sensitive (an auction going live, a player being sold) or need a prompt
response (a leave request sitting unapproved). This phase adds real push notifications for those,
via Firebase Cloud Messaging — the same Firebase project already used for Phone Auth, provisioned
for this from day one per `docs/OVERVIEW.md`'s Infra Reuse Rule.

---

## 2. Features

- A signed-in user is asked (once, low-friction, no nagging if declined) for notification
  permission after login
- Five events push a real notification to the people who'd want to know right now:
  - An auction starts — everyone in that league (every player, every franchise owner)
  - A player is sold or goes unsold — that player
  - A leave request comes in — the organizer
  - A leave request is approved or dismissed — that player/franchise owner
  - Co-organizer access is granted or revoked — that person (not on a self-revoke)
- Tapping a notification opens the app straight to that league, reusing the same
  `crichere://leagues/{id}` deep link Phase 3's Share action already produces
- A device stops receiving notifications for an account the moment that device signs out of it —
  including when a different account signs in on the same device (this app's own tested
  account-switching behavior)
- Android only this phase — iOS needs an APNs key uploaded through the Apple Developer Portal and
  Firebase Console, entirely outside this codebase, and isn't buildable here regardless

---

## 3. Decisions Made

| Decision | Rationale |
|---|---|
| **A focused five-event subset, not every candidate event** | Confirmed explicitly (2026-09-13), over notifying on all ~15 candidates found (join/claim, every bid, etc.). Per-bid and join/claim notifications are skipped -- lower value, and already live on-screen via Phase 5/6's SSE stream for anyone actually watching an auction. |
| **A `device_tokens` table, multiple per user** | Confirmed explicitly (2026-09-13), over a single column. Matches that auth already supports multiple concurrent sessions (`refresh_tokens` table) -- someone signed in on a phone and a tablet should get pushed to both. |
| **Unique on the token itself, not `(user, token)`** | This app already supports signing out and into a *different* account on the same device (tested repeatedly in this project). A token upsert-by-token (reassign owner on conflict) means switching accounts on one device correctly moves that device's notifications to the new account, instead of leaving a stale row still pointing at whoever used to be signed in there. |
| **Android only** | Confirmed explicitly (2026-09-13). FCM-to-iOS needs an APNs key/cert uploaded via the Apple Developer Portal + Firebase Console -- outside the codebase, and iOS isn't buildable/testable in this environment regardless. The backend send path stays platform-agnostic either way, so iOS is a later mobile-only addition, not a backend rework. |
| **A send can never fail or block the real action it's attached to** | A push notification is best-effort by nature. Approving a leave request must succeed even if FCM is unreachable -- the send is `@Async` and swallows every failure, logging rather than propagating. |
| **A stale token is deleted the first time a send to it fails** | An uninstalled app or an expired token shouldn't accumulate forever in `device_tokens` -- cleanup happens organically as sends are attempted, not via a separate sweep job. |

---

## 4. Open Questions and Gaps

None outstanding for this phase's scope. iOS delivery is an explicit non-goal this phase (see
Decisions Made), not an open question.

---

## 5. Pure Technical Things (as built)

### Data model

- New `device_tokens` table (`V17__create_device_tokens_table.sql`): `id`, `user_id`, `token`,
  `platform`, `created_at`, `updated_at`. Unique index on `token` alone (see Decisions Made).
- `FirebaseAdminTokenVerifier`'s lazy `FirebaseApp` initialization (previously private to that
  class) was extracted into a shared `FirebaseAppProvider` component, so the new FCM sender uses
  the exact same app/credentials without duplicating the "tolerate a missing service account in
  dev/CI" logic.

### API surface (as built)

- `POST /api/v1/me/device-tokens` -- authenticated, self-scoped, `{ token, platform }`, upserts by
  token onto the caller.
- `POST /api/v1/me/device-tokens/unregister` -- authenticated, `{ token }`, deletes that row.
- No other new routes -- the five notification triggers are internal calls inside existing
  endpoints (auction start/sold/unsold, leave request/approve/dismiss, role grant/revoke), not new
  API surface of their own.

### Security

Analysis done 2026-09-13.

- **Token registration is self-scoped only** -- the endpoint takes no `userId`, always the caller's
  own JWT principal. No IDOR surface.
- **A token never appears in any response body other than its own register call's 200** -- no
  endpoint lists another user's tokens.
- **No new rate limit** -- registering/unregistering is bound to one's own account with no
  PII-probe angle (unlike Phase 7's phone lookup), same reasoning already used for
  leave-request/follow: no cost, no abuse vector worth the complexity.
- **The FCM `data` payload carries only a `leagueId`** -- already public per every phase's standing
  league-visibility posture, so no new disclosure via the notification itself.
- **Stale-token cleanup bounds table growth** -- pruned organically on the first failed send, not
  left to accumulate.
- **A notification send can never fail or block the real business transaction it's attached to** --
  `@Async` plus a blanket catch inside the sender is the actual enforcement; called out here as a
  correctness property, not just an implementation detail.
