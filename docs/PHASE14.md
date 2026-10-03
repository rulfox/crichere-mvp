# Phase 14 — Railway cost cuts (backend memory) and move to Singapore

**Last updated:** 2026-10-03
**Status:** done. Backend memory ~0.81 GB -> ~0.47 GB; database, backend and web viewer now run in
Singapore (section 5). Old US-West database kept as rollback until the owner approves deleting it.

---

## 1. Why

Railway bills actual usage: about $10 per GB of memory per month and $20 per vCPU per month.
Production metrics on 2026-10-03, with no active users:

| Service | Memory | CPU | ≈ per month |
|---|---|---|---|
| backend (Spring Boot, Java 21) | ~0.80 GB, flat | ~0.01 vCPU | ~$8 |
| web-viewer (Next.js) | ~0.13 GB | ~0 | ~$1.3 |
| Postgres | ~0.08 GB | ~0 | ~$0.8 + volume |

About 75% of the bill was the backend JVM's **default** memory sizing, not load: the JVM sizes its
heap from the container limit and never gives memory back.

All services ran in US West (`sfo`) while users are in India: ~250 ms extra on every API call and
auction update. Same price, so the region move is a latency fix done in the same phase.

## 2. Decisions made

| Decision | Why |
|---|---|
| **Heap capped at 256 MB** (`-Xmx256m`), starting at 64 MB, shrinking when idle | Chosen 2026-10-03 (no active users yet). Measured live heap after load is ~70 MB, so 256 MB leaves ~3.5x headroom. |
| **JVM flags via `JAVA_TOOL_OPTIONS` env var on Railway**, not in the start command | Tunable from the dashboard without a code change; the JVM logs "Picked up JAVA_TOOL_OPTIONS" so it's visible. |
| **Serial GC** | One small heap on ~1 vCPU: lowest memory overhead. |
| **Virtual threads on** (`spring.threads.virtual.enabled`) | Each open auction stream (SSE) no longer holds a platform thread and stack. |
| **Hikari pool 5 / min idle 2** (default 10) | Requests hold a connection only for the query (open-in-view off). Smaller JVM and Postgres. |
| **springdoc off unless `SPRINGDOC_ENABLED=true`** (local profile turns it on) | Saves memory, and the API docs were **publicly reachable** on api.crichere.com, which they shouldn't be. |
| **No serverless / app sleeping** | Backend would never sleep (DB pool + SSE keep-alive count as traffic) and Spring takes ~11 s to start, which breaks OTP login. On the web viewer, a sleeping app makes WhatsApp's crawler time out, so shared links lose their preview. Saving would be ~$1. |
| **No Redis, no bundle analyzer, no profilers** (Railway's generic advice) | Redis is another billed service. The JS bundle runs in browsers, not on the server. Profilers are overkill at this size. |
| **Move all services to Singapore** | Chosen 2026-10-03. Closest Railway region to India. |

## 3. Implementation

- `backend/src/main/resources/application.yml`: virtual threads, Hikari pool size, springdoc toggle
  (off by default, on in the `local` profile).
- `SecurityConfig.kt`: comment only; the docs paths stay permitted but now 404 outside local.
- Railway backend env var:
  `JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m -Xss512k -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=80m -XX:MaxDirectMemorySize=64m`

## 4. Local verification (2026-10-03)

- Backend tests: 475/475 pass (Docker running, so the Testcontainers integration tests ran too),
  including the auction SSE flow with virtual threads on.
- Boot jar run against the local docker Postgres with the flags above, then 600 API requests:
  JVM committed memory **~310 MB** (heap 89 MB committed, of which ~65 MB live; metaspace 98 MB;
  code cache 37 MB). Without the flags, the same run committed ~500 MB with the heap fully committed.
- `/v3/api-docs` and `/swagger-ui.html` return 404 with springdoc off.
- Starts in ~11 s.

## 5. Production rollout

Backup before the region move (Postgres has a volume, so moving it means downtime while it copies):
`pg_dump` via `railway ssh` → `E:\crichere-backups\crichere-prod-20261003-pre-singapore.sql`
(outside the repo; it contains user data). Verified: dump-complete marker present, and the row
counts of all 17 tables in the dump match production exactly.

Results (2026-10-03):
- **Backend memory: done.** Deployed `63c11ad` with `JAVA_TOOL_OPTIONS` (log shows "Picked up
  JAVA_TOOL_OPTIONS", started in 7 s). Railway memory: **~0.81 GB -> ~0.47 GB** shortly after deploy
  (the 1.4 GB max in that window is the old and new containers overlapping during the deploy). API
  live; `/v3/api-docs` returns 404. Re-check after a day of real traffic and an auction.
- **Region move: done (2026-10-03), by a new database, not Railway's volume migration.**
  - Changing the old Postgres service's region (API, redeploy, `railway scale`) only changed its
    config: measured from inside, it still ran in US West (2 ms to `s3.us-west-1`, 178 ms to
    `s3.ap-southeast-1`). Railway keeps a volume where it was created.
  - Fix: workspace preferred region set to Southeast Asia (owner, dashboard), then a new Postgres
    (`Postgres-i8MW`, PG 18.6) created there; measured 3 ms to Singapore, 185 ms to California.
  - Final `pg_dump` of the old DB (`E:\crichere-backups\crichere-prod-20261003-final.sql`) restored into
    it. Verified: row counts **and** a per-table content checksum identical for all 17 tables.
  - Backend repointed with Railway reference variables (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD` =
    `${{Postgres-i8MW.…}}`, no password copied by hand) and moved to Singapore; Flyway validated 19
    migrations; backend -> DB 4 ms. Web viewer moved to Singapore (4 ms to Singapore; backend over the
    private network 48 ms).
  - From India: API ~140 ms per request on a warm connection via Railway's `sin1` edge.
  - Share flow still live: og tags, generated card (200, ~130 KB), assetlinks.json.
  - **Old Postgres (`Postgres`, US West) kept untouched as rollback.** Delete it, with its ~870 MB volume,
    once the owner approves; until then it still costs a little memory and storage.

Share card speed (2026-10-03, after the move):
- Measured inside the web viewer: logo from the US-East bucket 0.5–1.3 s; league via the public API
  ~250 ms cold vs ~25–40 ms over the private network; full card render 0.8–1.7 s.
- Fixes (`1c2b386` + follow-up): server-side API calls go over Railway's private network
  (`API_INTERNAL_BASE_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080`), retrying once over the
  public URL if the private call can't connect; rendered cards cached in a 100-entry LRU (1 h TTL,
  ~13 MB max) keyed by everything the card shows, so any edit re-renders at once.
- Result: a cached card is served in 43–260 ms inside the container (from 760–1,700 ms).
- Found live: right after the deploy, the first requests got the **default** card. The private
  network takes a few seconds to come up in a new container, and a chat app would have cached that
  default for the URL. That is what the public-URL retry fixes.

Open items found during the move:
- **Media bucket move to Mumbai: in progress (2026-10-03).**
  - Old `crichere-media-dev` (us-east-1): 13 objects, 9.7 MB; whole bucket public-read by bucket
    policy (payment screenshots included); BlockPublicAcls on; BucketOwnerEnforced; AES256; CORS only
    `http://localhost:8765` (dev leftover, unused: the app uploads by presigned POST, no browser CORS).
  - New `crichere-media-prod` in `ap-south-1`: same settings minus the stale CORS rule. All 13 objects
    copied with `Cache-Control: public, max-age=31536000, immutable` (safe: every stored URL carries a
    `?v=` that changes per upload). Verified per object: content type, size and ETag match the source.
    A first bulk copy reset content types to `binary/octet-stream` (found and fixed by per-object copy).
  - Logo from India: 0.28 s (Mumbai) vs ~1.5 s (Virginia).
  - Remaining: backend IAM user needs `s3:PutObject` on `crichere-media-prod/*` (owner, IAM console;
    `crichere-claude` can't read or edit other users), then `AWS_REGION=ap-south-1` /
    `AWS_S3_BUCKET=crichere-media-prod` on Railway, DB URL rewrite (backup first), test upload, then old
    bucket kept until the owner approves deleting it.
- **Media bucket in `us-east-1`** (original finding) (`crichere-media-dev`, checked via S3's response headers; the
  `crichere-claude` IAM user may not call GetBucketLocation/ListBuckets). A 100 KB logo takes ~1.5 s
  from India, and objects have no `Cache-Control`. Options: CloudFront in front, or a bucket in
  `ap-south-1` (Mumbai). Separate decision (AWS cost + data copy).
- **Backend outbound latency looks odd**: from the backend container, TCP to AWS takes ~170–340 ms
  in every region, while the DB and web viewer show it is in Singapore. Affects S3 uploads, Firebase
  and MSG91 calls. Investigate with Railway (egress routing).
- One `railway ssh` attempt reported "Host key for ssh.railway.com has changed"; not bypassed, the
  next attempt connected normally. Worth confirming with Railway that their SSH host key rotated.
