# Crichere admin web

Private admin site for the Crichere team (docs/PHASE20.md). Next.js static export served by Caddy.

## Develop

```sh
cp .env.example .env.local     # API base, Google client ID, environment label
npm install
npm run dev                    # http://localhost:3001 (the backend's local ADMIN_ORIGIN)
```

Sign-in needs a Google account listed in the backend's `ADMIN_EMAILS` and the OAuth client's JavaScript origins.

## Check

```sh
npm run lint && npm run typecheck && npm test
# e2e against a local backend (see playwright.config.ts for the backend command):
NEXT_PUBLIC_API_BASE=http://localhost:8080 NEXT_PUBLIC_ENV_LABEL=Local npm run build && npm run test:e2e
```

## Deploy

`Dockerfile` builds with `npm run build:host`, which also writes `deploy/Caddyfile` with the CSP hashes for this
build. Build args: `NEXT_PUBLIC_API_BASE`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_ENV_LABEL`.
