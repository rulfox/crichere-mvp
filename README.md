# Crichere MVP

Fresh ground-up rewrite of Crichere (cricket league auction platform). This repo replaces the old `crichere` codebase — see project docs for why and how.

**Docs live outside this repo:** `E:\Documentation\Crichere\` — start with `OVERVIEW.md`, then the relevant `PHASEn.md` and `ARCHITECTURE.md`. This repo tracks code only; planning/spec docs are maintained there, not here.

## Layout

- `backend/` — Spring Boot Kotlin API (not yet scaffolded)
- `mobile/` — KMP app, shared logic + native UI (Jetpack Compose on Android, SwiftUI on iOS) (not yet scaffolded)
- `web-viewer/` — Next.js public realtime auction viewer (not yet scaffolded)

## Status

Skeleton only — directories are placeholders. Actual project scaffolding (Gradle/KMP template, Spring Initializr, `create-next-app`) not yet run.
