# e2e helpers for the local emulator auction test

Dev-machine only. Full procedure and results: `docs/PHASE15.md`.

- `seed.mjs` — 15 users with complete profiles (organizer = `+917293318484`) and one ground, straight into the local docker Postgres. Idempotent. Writes `out/cast.json` (gitignored).
- `join.mjs ["league name fragment"]` — claims 2 franchises and joins the 10 seeded players through the API.
- `bidder.mjs ["league name fragment"]` — watches the SSE stream and bids for both franchises whenever the organizer opens a player; skips every 4th lot (`SKIP_EVERY`, 0 = never) so the organizer can test Unsold; a franchise that is squad-full or out of purse drops out. `BID_PAUSE_MS` sets the gap between bids.
- `act.mjs "<league>" <action>` — one auction action (`state`, `start`, `next`, `sold`, `unsold`, `undo`, `end`, `exceed on|off`, `bid <franchise> [amount]`) to put the app into a given state.
- `lib/` — `crypto.mjs` (phone hash / encryption and JWT minting, `local`-profile secrets only), `db.mjs` (psql via `docker exec backend-postgres-1`), `api.mjs` (fetch + SSE reader).

Needs Node 18+, Docker, and the backend running with `--spring.profiles.active=local`. Override with `E2E_API`, `E2E_DB_CONTAINER`, `JWT_SECRET`, `PHONE_CRYPTO_SECRET`.
