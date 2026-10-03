# Phase 14 — Railway cost cuts (backend memory) and move to Singapore

**Last updated:** 2026-10-03
**Status:** backend memory changes implemented and tested locally; production rollout and region
move in progress (see section 5).

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

Results are recorded below as each step is done.
