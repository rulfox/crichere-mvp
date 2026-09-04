# Phase 1 — Login + Profile Setup

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions that apply here.

**Last updated:** 2026-09-04
**Status:** implemented, merged to master 2026-09-04. **Retrofit pending (decided 2026-09-04, during Phase 2 scoping): adding a District tier (State → District → City) to the location model** — see the note at the end of Section 6. Not yet built; scheduling/implementation is a Phase 2 kickoff decision, not done as part of Phase 1's original build.

---

## 1. Overview

In plain terms: this phase lets a cricket player create an account using just their phone number — no email, no password to remember. They get a one-time code by SMS, enter it, and they're in. The first time, they're asked for a few basics: their name, a photo, where they're from (state and city), and how they play (batsman, bowler, all-rounder, or wicketkeeper — plus which hand they bat with, and how they bowl if that applies to them).

Once that's done, they land on their own profile page, where they can see everything they entered and fix anything that's wrong. That's the whole phase — there's no league browsing or team-joining yet, that comes next. Think of Phase 1 as "the front door": get people signed up and identified as cricket players, nothing more.

---

## 2. Features

- Log in with phone number (no password)
- Receive and enter a one-time SMS code, with the ability to ask for a new code if it doesn't arrive
- Stay logged in between app opens (no re-entering the code every time)
- Log out
- Fill in a first-time profile: name, photo, home state, home city, playing role, batting hand, bowling style (if relevant)
- Pick up where they left off if they close the app mid-signup
- View their own completed profile
- Edit their own profile afterward

---

## 3. Screens

Describing what belongs on each screen — not layout, spacing, or component choice, that's a separate design pass.

**Sequence:** Phone Entry → OTP Verify → Profile Setup (only if the profile isn't already complete) → Own Profile View. A returning user with a complete profile skips Profile Setup entirely and lands straight on Own Profile View after OTP Verify.

**Phone Entry**
- A single field for phone number.
- A button to request the code.
- Nothing else — this is the very first thing a new visitor sees.

**OTP Verify**
- A field (or set of digit boxes) to enter the 6-digit code.
- A "resend code" action, disabled for a short cooldown after each send.
- An error message when the code is wrong, with attempts remaining before they're bounced back to request a fresh code.

**Profile Setup** (may be one screen or a short multi-step sequence — that's an implementation choice, content is the same either way)
- Name field.
- Photo — pick from gallery or take a new one.
- State selector, pre-filled automatically if location permission is granted (device GPS → reverse-geocode), always editable by hand regardless.
- City selector (scoped to the chosen state), same auto-fill behavior.
- No visible Country field — the platform is India-only for Phase 1, set internally, not shown to the user.
- Playing Role selector: Batsman / Bowler / All-Rounder / Wicketkeeper.
- Batting Style selector: Right-hand / Left-hand.
- Bowling Style selector: only shown when Playing Role is Bowler or All-Rounder.
- A save/continue action. Disabled until every required field for the chosen role is filled.

**Own Profile View**
- Everything the user entered: photo, name, state, city, playing role, batting style, bowling style.
- An edit action leading back into the same fields as Profile Setup, pre-filled.
- A logout action.

---

## 4. Decisions Made

| Decision | Rationale |
|---|---|
| Phone-number login only, no email/password | User's original request — phone-first is the natural identifier for this audience. |
| Single country, fixed country code (no picker) | Simplifies input UI and OTP configuration; scope is domestic. |
| Firebase Phone Auth for OTP | Reuses the existing Firebase project (no new provisioning), avoids owning SMS-template compliance directly. |
| Firebase PNV (SIM-based instant verification) considered for Android, then reverted — OTP-only for both platforms in Phase 1 | Investigated 2026-08-31 as a potential Android cost-saver, but the premise didn't hold up: PNV is **not free** (per-verification pricing, India rate unconfirmed, plausibly *higher* than OTP's ~$0.01–$0.07/SMS), and it does **not** integrate with Firebase Auth UID — Firebase's own docs say Auth support for PNV tokens is Beta and "does not accept the Firebase PNV token as a sign-in token," meaning it would need a fully separate backend reconciliation path, not the "downstream logic unchanged" simplification first assumed. Revisit only once India's PNV rate and non-Beta Firebase Auth integration are both confirmed. |
| One account per phone number, enforced unique | Straightforward identity model — no reason to allow duplicates. |
| Playing Role = single-select: Batsman / Bowler / All-Rounder / Wicketkeeper | Matches standard cricket-auction categorization. "Wicketkeeper" already implies wicketkeeper-batsman — WKs virtually always bat, essentially never bowl — so no separate "keeps wicket" flag and no bowling-style question for that role. |
| Bowling Style asked only for Bowler / All-Rounder | Follows from the role model above. |
| Photo required at signup | Per user's explicit request, despite the drop-off-friction tradeoff noted during review. |
| Onboarding is resumable | Partial profile progress is saved; a user who quits mid-signup resumes at the first missing field, not from scratch, next time they log in. |
| Account creation is idempotent on Firebase UID | Re-verifying the same phone number always resolves to the same account — never creates a duplicate. |
| Profile is editable after completion | Users will make entry mistakes (wrong city, bad photo) — basic edit capability ships in Phase 1, not deferred. |
| Terminal screen = Own Profile View | Both a first-time completer and a returning already-complete user land here. Serves as Phase 1's defined "done" state since there's no dashboard yet (that's Phase 2), and doubles as the edit-profile screen. **Superseded 2026-09-04 (Phase 2 scoping): once Phase 2 ships, League Dashboard becomes the post-login landing screen instead — Own Profile View becomes reachable via a nav tab/menu, not the first screen after login.** See PHASE2.md's Decisions Made. |
| Logout included | Missing from the initial draft — added as a basic, expected capability. |
| State/City backed by a fixed dataset, dependent dropdowns | Clean, consistent data for Phase 2's location filters — avoids free-text mismatches (e.g. spelling variants of the same city). |
| GPS auto-fill for State/City via device's native reverse-geocoder | Free (no third-party geocoding API), works on both platforms, always overridable by hand. |
| Country scoped to India for Phase 1, field exists in data model but not shown in UI | Data model stays extensible for multi-country later (cheap now, expensive to retrofit) without cluttering Phase 1's UI with a one-option dropdown. |
| OTP resend cooldown: 60s, with a visible countdown; 3 resends max; 5 wrong-code attempts before forcing a fresh OTP request | 30s risked users spamming resend before a slow-delivering SMS arrived; 2 minutes was too long a wait for genuine failures — 60s is the middle ground. |
| Multiple concurrent device sessions allowed | Standard for consumer apps — each device gets its own `refresh_tokens` row, all valid simultaneously, individually revocable. Single-device-only fits high-security apps, not this one. |
| `profile_complete` is derived on read, not stored | Computed from the required fields (with the role-conditional Bowling Style rule) at the one low-frequency point it's checked — login redirect. Avoids a stored flag drifting out of sync with an edit path that forgets to update it; no performance cost at this scale. |

---

## 5. Open Questions and Gaps

(None currently open — all resolved into Decisions Made above.)

---

## 6. Pure Technical Things

### Session Strategy

- **Access token**: short-lived JWT (15 min), used for all API calls.
- **Refresh token**: long-lived (30 days), rotated on every use (old one invalidated the moment a new one is issued — limits replay if one leaks).
- Refresh tokens are stored server-side (hashed, not plaintext) in a `refresh_tokens` table so they can be individually revoked — this is what makes logout actually terminate the session, not just clear the client's copy.
- On app restart: client uses the stored refresh token to silently obtain a new access token. No re-entry of the OTP.
- **Logout**: revokes (deletes/marks invalid) the refresh token server-side, then clears both tokens from device storage.
- `profileComplete` is included in **both** the `/auth/session` (OTP verify) and `/auth/refresh` response payloads, not just one — the client needs it after either call to know whether to route to Profile Setup or straight to Own Profile View.

### Phone Number Storage

- **No plaintext phone column.** Two columns instead:
  - `phone_lookup_hash`: `HMAC-SHA256(phone, server-side secret)` — deterministic, so it can carry a unique index for login lookup and duplicate-prevention, without the phone number itself being derivable from the database.
  - `phone_encrypted`: AES-GCM encrypted phone number (random IV per row), decryptable only by the backend, for the rare case the actual number needs to be shown/used (e.g. support lookup).
- **Key management**: encryption/HMAC secret held as a backend environment secret (not AWS KMS) — KMS customer-managed keys cost ~$1/month each, avoidable at this stage given the stated free-tier constraint. Revisit if/when a managed-key rotation story becomes worth the cost.

### OTP / Auth Abuse Protection

- **Firebase App Check** enabled on the client (Play Integrity API on Android, App Attest on iOS) — blocks bots and scripted clients from triggering SMS sends, which is both a cost control and an abuse control.
- **SMS region policy**: restrict Firebase to only send OTP SMS to India numbers — blocks abuse via international number spam.
- **reCAPTCHA SMS defense**: Firebase/Identity Platform's specific fraud-scoring feature for SMS auth — invoked automatically on every OTP request, blocks sends above a risk threshold.
- **Backend rate limits** on the token-verify/session-issuance endpoint: capped per phone number and per IP (e.g. N requests per hour) using an in-memory limiter (Bucket4j) — acceptable for a single-instance Railway deployment; would need a shared store (Redis) if the backend ever scales to multiple instances.
- Every session-issuance call **verifies the Firebase ID token's signature via the Firebase Admin SDK** server-side — the backend never trusts a client-supplied UID directly.

### Cost Note — Firebase Phone Auth Is Not Free

Confirmed via research (2026-08-31): phone auth requires the **Blaze (pay-as-you-go) plan** — not available on the free Spark plan, no free SMS quota. Billed per SMS sent, India rate in the ~$0.01–$0.07 range depending on source — confirm the exact current rate in the Firebase console before launch. Blaze is already enabled on the reused project (confirmed by user), so no setup blocker, but this is an ongoing per-login cost to budget for, not a one-time setup cost.

### PNV — Considered and Deferred

Investigated 2026-08-31 as an Android-only cost-saving alternative to OTP (SIM-based, no SMS). Reverted before implementation — keeping the findings here so a future revisit doesn't repeat the research:

- **Not free.** Firebase's own pricing page: billed per successful verification, on Blaze, region-priced (samples seen: Spain $0.031, France $0.053, Germany $0.088, Indonesia $0.135). India's rate wasn't in the sample — needs checking directly — but the spread means PNV is not safely assumed cheaper than OTP's India SMS rate (~$0.01–$0.07). The entire cost-saving rationale was unconfirmed.
- **No Firebase Auth UID integration.** Firebase's own docs: Auth support for PNV tokens is Beta and "does not accept the Firebase PNV token as a sign-in token." A working integration needs a custom backend token-verification and account-reconciliation path, keyed on the phone number rather than a Firebase UID — meaningfully more backend work than first assumed, built against a Beta surface.
- **Revisit condition:** confirm India's actual PNV rate is lower than OTP's, and confirm Firebase Auth's PNV integration has left Beta, before reopening this.

### Photo Upload (S3)

- Presigned POST, short expiry (~5 min) — the backend returns an upload URL plus a set of form fields (including a signed policy document) that the client submits together as a multipart form; not a presigned PUT.
- Content-Type restricted to `image/*` via a policy condition.
- Object key namespaced per user (`users/{userId}/profile.jpg`) — the presign endpoint only ever issues keys under the calling user's own prefix.
- Size cap enforced via a `content-length-range` condition in the same policy document (a plain presigned PUT can't hard-cap size on its own).

### Data Model

- **Enums as VARCHAR + CHECK constraint** in Postgres (Playing Role, Batting Style, Bowling Style) — not free text, not a separate lookup table for a fixed small set.
- **New tables via Flyway migration**, each with a test.
- `users`: id, phone_lookup_hash (unique, indexed), phone_encrypted, created_at
- `profiles`: user_id (FK), name, photo_url, country (default `IN`, not user-facing in Phase 1), state, city, playing_role, batting_style, bowling_style — no `profile_complete` column, derived on read (see Decisions Made)
- **`PUT /api/v1/profiles/me` is full-replace, not partial-merge.** Every field omitted from the request body is stored as null/cleared, not left as its previous value. The mobile client must always resend the complete accumulated profile snapshot (everything collected so far across the onboarding wizard's steps, with not-yet-collected fields as null) on every save — never just the field the user most recently edited. `GET /api/v1/profiles/me` returns HTTP 200 with all fields null and `profileComplete: false` for a user with no profile row yet (not 404), so the client always has a snapshot to build the next PUT from.
- `refresh_tokens`: id, user_id (FK), token_hash, issued_at, expires_at, revoked_at (nullable)
- State/City data is served to the client via a backend reference endpoint (`GET /api/v1/reference/states`, `GET /api/v1/reference/states/{state}/cities`) backed by the fixed dataset above — not a dataset bundled into the client.

### Retrofit: District tier (decided 2026-09-04, not yet implemented)

During Phase 2 scoping, decided to add **District** as a real third location tier: `State → District → City`, everywhere location is captured — this profile's Location step included, not just leagues. Reasoning: District is a location-model question, not a per-feature one, so profile and league should stay consistent rather than one having finer granularity than the other.

What this touches when implemented:
- New `districts` reference table + migration, seeded alongside states/cities; `cities` gains a `district_code` FK (replacing or supplementing its direct `state_code` FK, depending on whether a city's district is always inferable or needs its own seed data).
- Reference endpoints become a 3-level chain: `GET /api/v1/reference/states/{state}/districts`, `GET /api/v1/reference/districts/{district}/cities` (replacing the current direct `states/{state}/cities` call, or keeping both if a direct state→city lookup is still useful elsewhere).
- `profiles` table gains a `district` column; `ProfileUpdateRequest`/`ProfileResponse` DTOs and `ProfileService`'s validation gain the field.
- Mobile: Profile Setup's Location step becomes three dependent dropdowns (State → District → City) instead of two; GPS auto-fill gains a District match using `Geocoder`'s `subAdminArea` (Android) — already returned by the same reverse-geocode call Phase 1 built, just not read yet — alongside the existing `adminArea` (state) and `locality` (city) fields.
- This is real implementation work on already-shipped, merged code — not a doc-only change. Timing (a dedicated small patch vs. bundled into Phase 2's own implementation, since Phase 2 needs District reference data anyway) is an open scheduling decision, not made yet.

### Transport

- HTTPS everywhere (Railway terminates TLS).
- CORS: not needed for the mobile app; scoped to known origins only if/when this backend is ever called from a browser context.

---

## 7. Design Prompts

Derived from Section 3's Screens content, one prompt per screen — feed these directly into Claude Design to generate mockups. No visual design system is locked yet, so each prompt only constrains content/purpose (matching Section 3's "not layout" rule) plus a light tone steer; let the design pass make the visual calls. **Keep in sync with Section 3** — if Screens content changes, update these too.

**Phone Entry**
> Mobile app screen, first thing a new user sees. Purpose: collect a phone number to start login. Content: a single phone number input field, a button to request a verification code. Nothing else on this screen — it's the very first impression, keep it minimal and welcoming. Tone: clean, modern, trustworthy — this is a cricket league auction app, sports-adjacent but not garish.

**OTP Verify**
> Mobile app screen, shown right after Phone Entry. Purpose: let the user enter the 6-digit SMS code they just received. Content: a 6-digit code input (digit-box style is common for this pattern), a "resend code" action that's disabled with a visible cooldown countdown after each send, an error state for a wrong code showing attempts remaining. Tone: same as Phone Entry — clean, minimal, reassuring during a moment that can feel anxious (did the code even arrive?).

**Profile Setup**
> Mobile app screen (or short step sequence), shown once after first-time verification, before the user can use the app. Purpose: collect a first-time cricket player profile. Content: name field, photo picker (gallery or camera), state selector, city selector (dependent on state), a playing-role selector with four options (Batsman / Bowler / All-Rounder / Wicketkeeper), a batting-style selector (Right-hand / Left-hand), a bowling-style selector that only appears when the role is Bowler or All-Rounder, and a save/continue action that's disabled until required fields for the chosen role are filled. Tone: feels like joining a cricket community — a bit of sport/team energy is welcome here, more than on the login screens.

**Own Profile View**
> Mobile app screen, the landing point after login (both first-time completion and returning-user login). Purpose: let the user see and manage their own profile. Content: their photo, name, state, city, playing role, batting style, bowling style, an edit action (leads back into Profile Setup's fields, pre-filled), and a logout action. Tone: this is "home base" for the user — should feel personal and settled, a calmer register than the onboarding screens before it.
