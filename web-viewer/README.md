# Web Viewer

Crichere's public web (docs/PHASE6.md, redesigned in docs/PHASE11.md): the marketing landing page at
`/`, and the public spectator page for a league's live player auction -- no login, no app install,
one URL per league (`/leagues/{id}`). Next.js (App Router, TypeScript), consuming
the backend's already-public `GET /leagues/{id}`, `GET /leagues/{id}/auction/stream` (SSE), and
`GET /leagues/{id}/auction/results` endpoints directly from the browser.

## Getting started

```bash
npm install
npm run dev
```

Runs against the backend at `NEXT_PUBLIC_API_BASE_URL` (defaults to `http://localhost:8080`). The
backend's `local` profile already allows CORS from `http://localhost:3000` by default -- see
`crichere.web-viewer.origins` in `backend/src/main/resources/application.yml`.

### Environment

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Backend base URL |
| `NEXT_PUBLIC_PLAY_STORE_URL` | unset | Google Play listing. Unset -> "Coming soon" badge, no QR card, no mobile "Open in app" banner |
| `NEXT_PUBLIC_APP_STORE_URL` | unset | App Store listing. Unset -> "Coming soon" badge |

The landing page's "Watch live" links come from the backend's `GET /api/v1/auctions/live-now` and
are hidden while no auction is running.

## Tests

```bash
npm run test    # Vitest + React Testing Library
npm run build   # production build, also type-checks
npm run test:e2e  # Playwright against a mock backend (e2e/mock-server.mjs) -- no real backend needed
```
