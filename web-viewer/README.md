# Web Viewer

Crichere's public spectator page for a league's live player auction (docs/PHASE6.md) -- no login,
no app install, one URL per league (`/leagues/{id}`). Next.js (App Router, TypeScript), consuming
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

## Tests

```bash
npm run test    # Vitest + React Testing Library
npm run build   # production build, also type-checks
```
