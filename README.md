# Crichere MVP

Fresh ground-up rewrite of Crichere (cricket league auction platform). This repo replaces the old `crichere` codebase — see project docs for why and how.

**Docs live in-repo:** [`docs/`](docs/) — start with [`OVERVIEW.md`](docs/OVERVIEW.md), then the relevant `docs/PHASEn.md` and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Every phase doc is a living record of decisions made and why — keep it updated as decisions happen, not as a retrospective write-up. (Previously lived outside the repo at `E:\Documentation\Crichere\`; moved in-repo 2026-09-04 so decisions and code stay versioned together.)

## Layout

- `backend/` — Spring Boot Kotlin API. Phase 1 (phone-OTP auth, profile CRUD, S3 photo upload, states/cities reference) implemented and tested.
- `mobile/` — KMP app, shared logic + native UI (Jetpack Compose on Android, SwiftUI on iOS). Phase 1 (Phone Entry, OTP Verify, Profile Setup, Own Profile View) implemented; Android verified live end-to-end, iOS authored and compiling, first real run pending Mac/cloud-CI access.
- `web-viewer/` — Next.js public realtime auction viewer (not yet scaffolded).

## Status

Phase 1 (login + profile setup) implemented, merged to `master`. Phase 2 (league dashboard + creation) scoped in [`docs/PHASE2.md`](docs/PHASE2.md), not yet built. See [`docs/OVERVIEW.md`](docs/OVERVIEW.md)'s Phase Index for current status per phase.
