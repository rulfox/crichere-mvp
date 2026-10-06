# Crichere — Full Rewrite Overview

Living document. Updated continuously as decisions are made — check `Last updated` per section, not just the top.

**Last updated:** 2026-08-31

## What This Is

Cricket league auction platform. Full ground-up rewrite decided 2026-08-31. This is a clean design — no reference to any prior Crichere codebase, specs, or patterns during design/build. A prior app exists but is out of bounds as a design input; it's only brought back in for a side-by-side comparison after a given feature is fully finalized here.

## Stack Decisions (project-wide, apply to every phase)

| Layer | Choice | Why |
|---|---|---|
| Mobile app | **KMP (Kotlin Multiplatform)** — shared business logic/data layer, native UI per platform: Jetpack Compose on Android, SwiftUI on iOS | Superseded an earlier Flutter decision (2026-08-31, same day) — user chose the highest per-platform quality ceiling over Flutter's faster single-UI-codebase tradeoff. Costs more UI build/maintenance effort (two UI layers to build and keep in sync) in exchange for fully native rendering on both platforms. |
| Backend | **Spring Boot Kotlin**, from scratch | Typed, mature, well-suited to an event-sourced domain like an auction ladder. Local-only during dev/testing, Railway at go-live — see Deployment Strategy below. |
| Public web viewer (realtime auction link, spectators) | **Next.js**, separate app | Thin SSE-consuming broadcast view — doesn't need to share code with the mobile app regardless of app stack. |
| Auth / OTP | **Firebase Phone Auth** | Reuses the existing Firebase project (no new provisioning), avoids owning India DLT SMS-template compliance that AWS SNS/Cognito would require. **Not free** — requires the Blaze (pay-as-you-go) plan (already enabled), billed per SMS sent (~$0.01–$0.07/SMS in India, confirm exact current rate before launch). |
| Media storage | **AWS S3**, bucket `crichere-media-dev` (us-east-1) | Existing bucket, already provisioned — reused, not reprovisioned. |
| Hosting | **Railway** (backend, at go-live) — local-only during dev/testing | Existing account/setup, reused at go-live. See Deployment Strategy below. |

## Deployment Strategy (updated 2026-08-31)

**Local-first, Railway at go-live.** Backend is developed and tested entirely locally for now — run via `./gradlew bootRun` (or IDE) against a local/Dockerized Postgres, exercised through local API testing (Testcontainers for automated integration tests, manual tools like curl/Postman/HTTP-client for exploratory testing).

**Go-live only after local confirmation.** Once the API is confirmed fully working locally, it deploys to **Railway** (existing account, reused, as originally planned) — AWS and Firebase remain in use for media storage and auth respectively, but they are not backend hosting candidates; Railway is the settled hosting target, just not used until local testing is done.

## Infra Reuse Rule

No new cloud provisioning for this rewrite. Reuse: AWS (S3 `crichere-media-dev`), Firebase (Auth + FCM project), Railway (hosting, at go-live). Confirm against current state before assuming any of these still match — this doc is a snapshot, not live state.

## Repository Plan (updated 2026-08-31, supersedes the earlier branch-based plan)

**New directory, fresh repo — not a branch in `E:\crichere`.** Reversed from an earlier same-project plan (archive `master`, clean the tree, work in-place) once the "zero reference to old code" rule was weighed against it: a branch strategy still leaves old code reachable via `git log`/history search in the same repo, relying on discipline to not look; a physically separate directory makes that impossible by construction. There's also no code continuity worth preserving via shared git history — the mobile stack changed completely (Flutter → KMP) and the backend is rewritten from scratch, so nothing carries over line-by-line. KMP's and Spring Boot's own project-root conventions (Gradle multi-module layouts) also want a clean root, not old Flutter/Spring directories sitting nearby.

- `E:\crichere` (this repo) stays **untouched** — it remains the reference target for the later post-hoc feature comparisons (see "What This Is" above), and nothing else.
- **New repo location: `E:\crichere-mvp`** — created and git-initialized 2026-08-31, skeleton committed (`.gitignore`, `README.md`, empty `backend/`, `mobile/`, `web-viewer/` top-level folders each with a placeholder README). No remote/CI wired up yet. No actual project scaffolding yet (no Gradle/KMP template, no Spring Initializr, no `create-next-app`) — that's a separate, larger step.
- `E:\Documentation\Crichere\` (this docs folder's original location) was unaffected by the repo split, since it was external to any repo either way. **Superseded 2026-09-04**: these docs now live in-repo at `E:\crichere-mvp\docs\` instead, so decisions and code stay versioned together — see the note in the repo's root `README.md`.

**Status:** skeleton set up. Next: actual project scaffolding for each of the three subprojects, and remote/CI setup, both still pending.

## Phase Doc Template

Every `PHASEn.md` follows this structure, in order:

1. **Overview** — plain language, non-technical, explains what the phase delivers
2. **Features** — plain-language feature list
3. **Screens** — what content lives on each screen, not layout/placement
4. **Decisions Made** — locked choices with rationale
5. **Open Questions and Gaps** — unresolved items
6. **Pure Technical Things** — implementation detail, security, data model
7. **Design Prompts** *(optional, added when ready to generate screens)* — derived from Section 3's Screens content, one prompt per screen, written to feed directly into Claude Design. Keep in sync with Section 3 — if Screens content changes, update the prompts too.

## Related Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) — backend (Spring Boot Kotlin) and frontend (KMP) coding patterns/architecture, applies project-wide. Status: locked.
- [DESIGN-REVIEW.md](DESIGN-REVIEW.md) — screen-by-screen gap analysis between the Claude Design comp and the implemented app (2026-09-30). Organizer-facing screens unverified (design's Tweaks panel didn't work in the shared view) — needs a follow-up pass.

## Phase Index

- [Phase 1](PHASE1.md) — OTP login (Firebase Phone Auth, India-only for now) + profile setup (Name, Photo, State, District, cricket attributes; City removed 2026-10-06). **Status: implemented, merged to master 2026-09-04.**
- [Phase 2](PHASE2.md) — league dashboard (list/filter by state, district, nearest-GPS; City removed and ground required 2026-10-06) + league creation, discovery-only (no auction mechanics yet — deferred to a later phase). **Status: implemented (backend + Android), iOS deferred.**
- [Phase 3](PHASE3.md) — joining a league as a player, claiming a franchise, UPI-screenshot-proof fee collection (peer-to-peer, no payment gateway), following a league, shareable deep link to League Detail. Still not the live auction. **Status: implemented (backend + Android), iOS screens authored but unwired.**
- [Phase 4](PHASE4.md) — auction setup: base price, purse, squad min/max, the auction pool (derived from Phase 3's joined players). Pure CRUD, no bidding. **Status: implemented (backend + Android), iOS screen authored but unwired.**
- [Phase 5](PHASE5.md) — the live auction engine itself: real-time bidding (SSE), undo/override, unsold-retry rounds, post-auction squad/results views. **Status: implemented (backend + Android, tested and manually verified on-device), iOS screen authored but unwired.**
- [Phase 6](PHASE6.md) — public web viewer: a no-login spectator page per league, consuming the existing public auction SSE stream. **Status: implemented (backend + web-viewer + mobile share addition), not yet deployed to Railway.**
- [Phase 7](PHASE7.md) — co-organizer role delegation: grant full organizer authority to another registered user by phone number, multiple per league, revocable anytime. **Status: implemented (backend + Android, tested and manually verified on-device), iOS screen authored but unwired.**
- [Phase 8](PHASE8.md) — push notifications (FCM, Android): auction start, sold/unsold, leave request/approval, role grant/revoke. **Status: implemented (backend + Android), tested and verified on-device with a real FCM round trip.**
- [Phase 9](PHASE9.md) — iOS wiring: the missing login flow (Phone Entry/OTP Verify) and main hub (League Dashboard/Creation, with a real MapKit ground-picker), plus a real navigation host wiring every previously-standalone screen together, and an XcodeGen `project.yml` for a real, automatable `.xcodeproj`. **Status: implemented (iOS only — authored, not yet compiled/run; needs a Mac to verify).**
- [Phase 10](PHASE10.md) — Android toolchain upgrade to latest stable (AGP 9.4.1, Kotlin 2.4.20, CMP 1.12.1, compileSdk 37) and migration of Android navigation to Navigation 3 (real back stack, hardware back, keyboard-aware back). **Status: Part A (toolchain) implemented and verified on-device; Part B (Navigation 3) implemented and verified on-device.**
- [Phase 11](PHASE11.md) — public web redesign (Claude Design handoff): marketing landing page + rebuilt Live Auction viewer, with backend parity fields (lot counter, scheduled auction time, batting/bowling style, live-now endpoint) and a mobile scheduled-time picker. **Status: implemented (backend + mobile + web), verified in-browser against the design; mobile picker not yet verified on-device; deployed to Railway production 2026-10-02.**
- [Phase 13](PHASE13.md) — branded link sharing on crichere.com: per-league Open Graph share card (Claude Design), default Crichere card, https Share link, Android App Links (iOS Universal Links deferred). **Status: implemented (web + Android), tested locally; not yet deployed or verified on-device.**
- [Phase 14](PHASE14.md) — Railway cost cuts: backend JVM right-sized (256 MB heap, Serial GC, virtual threads, smaller DB pool, API docs off in production) and all services moved to Singapore (new database, verified restore). **Status: done; old US-West database kept as rollback.**

## Open Questions

- Whether `E:\crichere-mvp` gets its own GitHub remote/CI now, or stays local-only until closer to go-live.
- When to run actual project scaffolding (Gradle/KMP template for `mobile/`, Spring Initializr for `backend/`, `create-next-app` for `web-viewer/`) — not yet done, skeleton-only so far.
