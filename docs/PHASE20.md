# Phase 20: Admin web (moderation and insights)

**Status:** planned; design approved by the owner 2026-11-21 (alignment pass done); no code yet. **Date:** 2026-11-21.

## 1. Scope

A private admin site at `admin.crichere.com` for the owner:
- Moderation: the reports queue and actions now done by `backend/tools/moderation/moderation.py`.
- Insights: live and historical charts of how Crichere is used.
- Email alerts for new reports and for every admin sign-in.

Also in scope (sections 6 to 8): league and user browsers, exact phone lookup, system health, global search.
Not in scope: editing league text, deleting accounts, broadcast pushes,
acting as a user. Installs, crashes and ratings stay in Play Console and Crashlytics.

## 2. Decisions (2026-11-21)

| # | Decision | Outcome |
|---|---|---|
| A1 | Admin login | Google Sign-In; backend checks the ID token's audience (admin client ID) and an `ADMIN_EMAILS` allowlist, empty means nobody |
| A2 | Admins | One: `aranyventures@gmail.com`. Owner turns on 2-Step Verification or a passkey on it |
| A3 | Hosting | Separate static Next.js export on its own Railway service, `admin.crichere.com`, `noindex`. Not a route in the public web viewer (section 5) |
| A4 | Session | Backend sets an httpOnly, Secure, SameSite=Strict cookie on `api.crichere.com`, path `/api/v1/admin`, 30 minutes, backed by an `admin_sessions` row so sign-out ends it at once (section 5, G3) |
| A5 | Backend | `/api/v1/admin/**`, ADMIN role, deny by default. Moderation logic moves from the script into one backend service; the script stays as a break-glass tool writing the same audit table |
| A6 | Audit | `moderation_actions` gains the admin's email (V26) |
| A7 | Report alerts | Amazon SES, count and link only, at most one email per 15 minutes; also an email on each admin sign-in |
| A8 | Insights | Charts are aggregates only; the league and user browsers (sections 6, 7) are separate, read-only, audited |
| A9 | Active users | `user_activity_days`, batched writes (section 4) |
| A10 | Timing | Start now, alongside the 14-day closed test. No effect on the mobile release |
| A11 | Layout (owner pick, 2026-11-21) | Board P15 direction 1a (sidebar, KPI strip, 12-column grid) with 1b's one-sentence summary at the top of each section. Phone layout from 1c |
| A12 | Theme | Follows the device setting, with a light/dark switch in the account menu that overrides it |
| A13 | Owner setup | AWS and Google Cloud setup done through their CLIs by Claude, each change confirmed by the owner first |

## 3. Facts found for insights (2026-11-21)

- `otp_challenges` rows are deleted 1 day after expiry (`OtpAuthService.RETENTION`). Sign-in funnel history is lost
  unless counted before deletion.
- Nothing records app use, so active users can't be measured today.
- Indexes exist for open reports, pending deletions and OTP lookup. None on `created_at` of `users`, `leagues`,
  `league_players` or `auction_bids`, which time-range charts need.
- One backend instance on Railway, Hikari pool of 5. Insight queries must not compete with app traffic.

## 4. Insights data design (decided 2026-11-21: lowest server cost without losing speed)

Rule: viewing the dashboard costs the database nothing per refresh. Reads come from memory or from a daily summary.

| Speed | Data | Source | DB cost while viewing |
|---|---|---|---|
| Live (5 s) | Live auctions, bids per minute, viewers, activity feed | Backend memory: auction engine state, SSE subscriber counts, a ring buffer of the last 50 anonymised events | None |
| Today | Signups, leagues, joins, reports, OTP sends since 00:00 IST | In-memory counters, bumped after commit; reseeded by one query at startup and at midnight | None (one query per restart) |
| History | Trends, funnel, retention, districts | `metrics_daily(day, metric, dimension, value)`, written once a night at 00:10 IST, idempotent upsert, Postgres advisory lock | One small read per range, then cached until midnight (`Cache-Control: private`, ETag) |

- One admin SSE stream sends a compact snapshot every 5 s only when it changed, and only while a tab is visible.
  With no subscriber, no snapshot is built.
- Nightly job counts `otp_challenges` before they are deleted, and computes retention cohorts. Nothing is computed
  on request.
- Active users: `user_activity_days(user_id, day)`. Backend keeps today's seen ids in memory and writes new ones
  every 5 minutes as one multi-row `INSERT ... ON CONFLICT DO NOTHING`. Rows older than 13 months deleted monthly.
- Indexes: BRIN on `created_at` of append-only tables (`users`, `leagues`, `league_players`, `auction_bids`),
  near-zero write cost. No extra connection pool: the only heavy query is the nightly job, with a statement timeout.
- Districts as ranked bars; a map later only if needed. Release markers in a small table, added by hand.
- Limit: counters and buffers are per instance. Fine on one Railway instance; moving to several instances needs a
  shared store.

## 5. Admin site hosting (revises A3/A4 for cost)

- Static build (Next.js `output: 'export'`), no Node server: served as static files at `admin.crichere.com`.
- Google Sign-In in the page sends the ID token to the backend, which checks it against Google's cached keys and sets
  the session cookie itself: `HttpOnly`, `Secure`, `SameSite=Strict`, host-only on `api.crichere.com`, path
  `/api/v1/admin`, 30 minutes.
- CORS with credentials only for `https://admin.crichere.com` on `/api/v1/admin/**`. Every change also checks
  `Origin` and needs a custom header.
- Charts: visx (tree-shaken). Live data via `EventSource` with credentials.

## 6. League browser (owner request 2026-11-21, read-only)

Not in the first concept. Adds a list of every league, paged, and a detail page showing everything about one league.
It changes A8: insights stay aggregate, but this tool shows a league's people, as the app already does to members.

List:
- Columns: logo, name, organizer, district and state, start date, derived status (draft, open, auction live,
  auction done, completed), franchises and players against required, created date, open reports.
- Filters: name search, state and district, status, created range, has open reports. Sort: newest, start date, name.
- Keyset pagination on `(created_at DESC, id)` with a btree index, 25 a page; cursor kept in the URL. Per-row counts
  from one grouped query over the page's ids (no N+1). Total from a capped count (`LIMIT 1001`, shows "1000+"),
  run once per filter change, not per page.

Detail, one tab per area, each loaded only when opened:
- Overview: every league field, logo and banner (with removed flags), ground and pin, dates, format, fees,
  organizer UPI ID, organizer and co-organizers, handover, followers, created, updated, completed.
- Franchises: owner, logo, players bought, purse used and left, fee payment state.
- Players: profile summary, joined, payment state and screenshot, left or removed, auction outcome, price, buyer.
- Auction: settings, status, schedule, bid history per lot from `auction_bids`, results.
- Awards. Moderation: reports on the league and its people, actions taken, link to the moderation actions.
- Timeline: key events from stored timestamps.

Rules: read-only (changes only through moderation actions). Phone numbers never shown. Opening a payment screenshot
writes an audit row. Deleted accounts show as "Deleted user". Static export can't pre-render league ids, so the
detail page reads the id from the URL on the client (`/leagues/view?id=...`).

## 7. User browser (owner request 2026-11-21, read-only, same pattern as section 6)

List:
- Columns: photo, name, district and state, playing role, joined, last active day, leagues (organizes, owns, plays),
  status (active, profile incomplete, suspended, deletion pending), open reports against.
- Filters: name search, state and district, status, joined range, has open reports, internal accounts.
  Sort: newest, last active, name.
- Exact phone lookup: the admin types a full number, the backend hashes it and matches `phone_lookup_hash`. The
  number is never displayed, and each lookup writes an audit row. Corrects section 1: phone search is possible this
  way, as exact match only.
- Keyset pagination on `(created_at DESC, id)`, 25 a page, capped total, grouped per-row counts.

Detail, tabs loaded when opened:
- Profile: every profile field, photo (with removed flag), terms version and date, created, last active.
- Leagues: organizer, co-organizer, franchise owner, player, follower; with join and payment state.
- Auction: players bought as owner, own sale result as player.
- Activity: calendar of active days from `user_activity_days`.
- Moderation: reports against them, reports they filed, actions taken, notices. Blocks as counts only.
- Account: suspended, deletion requested and due date, number of devices registered for push (no token values).

Rules: read-only; deleted accounts are gone after purge and do not appear.

## 8. Gaps found in review (2026-11-21, owner: include all in the first version)

| # | Gap | Proposed answer |
|---|---|---|
| G1 | Test, demo and owner accounts skew every metric | `users.is_internal`, set from the admin site; insights exclude internal users and their leagues |
| G2 | Privacy Policy doesn't cover activity days, admin access or SES | Update policy and Data safety before activity tracking ships; 13-month retention stated |
| G3 | A stolen admin cookie can't be revoked for 30 minutes | Small `admin_sessions` table: sign-out and "sign out everywhere" end it at once |
| G4 | Nothing warns when the system itself is failing | System card: nightly job last run, OTP failure rate, SMS sends against the daily cap, MSG91 balance, SES bounces |
| G5 | Grounds are user-created but unseen | Grounds in league overview now; a grounds list later if needed |
| G6 | Closed reports and old evidence are kept forever (runbook: not automated) | Scheduled deletion after the policy's retention period |
| G7 | Suspended-number release after purge is manual SQL | Admin action with audit |
| G8 | No quick way to jump to a person or league | Global search (name or id) in the top bar |
| G9 | Payment screenshot links may be public in S3 | Checked 2026-11-21: not public. The bucket policy denies `s3:GetObject` on `leagues/*/payments/*` to anyone outside the account, and the backend serves them as short-lived signed URLs (`PaymentScreenshotUrlSigner`, since 2026-10-04). Other images stay public read by design |
| G10 | Admin-only security not tested | Tests: app token refused on admin routes, admin token refused on app routes, allowlist empty denies, CORS and Origin checks |
| G11 | Static host must send security headers | Host chosen must set CSP, `X-Frame-Options: DENY`, HSTS, `noindex` |
| G12 | Owner setup outside code | Google OAuth client, SES domain and DKIM at Hostinger, IAM for SES and S3 delete, Railway service and DNS |

## 9. Build plan

Separate commit per step, tests with each backend step. Order keeps the app safe: nothing collects new data before the
Privacy Policy says so.

**Step 0: design (Claude Design, before any code).**
- Check the chart palette (brand green plus categorical, status, sequential) with the dataviz validator, light and dark.
- Write `docs/design-prompts/ADMIN-WEB-PROMPT.md`; create the boards in Claude Design: sign-in, overview dashboard
  (KPI row, live, growth, engagement, leagues and auctions, trust and safety, system health), reports queue, report
  detail with actions, suspended accounts, audit log, league list and detail tabs, user list and detail tabs, global
  search, empty, loading, stale and error states, phone width, dark mode.
- Owner reviews; build matches the export (exact CSS read from the boards).
- Done 2026-11-21: Claude Design project "Crichere - V2", `P15 Admin Overview.dc.html` (three directions) and
  `P16 Admin Screens.dc.html` (39 boards: O overview, S sign-in and account, M moderation, L leagues, U users,
  X states), generated by `admin-kit.js` and `p16-*.js` in that project. Waiting for owner review. The Claude Design
  MCP server stops working by 2026-12-14: read the CSS from these boards before then. Approved 2026-11-21 after an
  alignment pass (full-width button, KPI rows, menu control, equal frame heights, chart width).
- Sign-in redesign (owner pick 2026-11-21, direction A "floodlit ground"): boards S1, S1d, S1w, S2, S6, S6d. Split
  screen: an always-dark scene (CSS ground plane, floodlight glows and beams, grain, brand lockup and splash-stumps
  illustration; no real data, the page is public) beside a sign-in panel that follows the device theme. Production
  chip and session note in the footer on the form column; the crichere.com link top right at the standard 32px edge
  padding (16px on phones), "Waiting for Google" state. One load animation, off under reduced motion. Every board
  now uses the real Crichere mark (`assets/logo/crichere-mark.svg`, reverse on dark) and the lowercase wordmark.

**Step 1: data (V26).**
- `moderation_actions.admin_email`; new action kinds for views and lookups (payment proof, phone lookup, sign-in,
  internal flag, release number).
- `admin_sessions`, `users.is_internal`, `user_activity_days`, `metrics_daily`, `release_markers`, `job_runs`.
- BRIN on `created_at` of `users`, `leagues`, `league_players`, `auction_bids`; btree `(created_at DESC, id)` on
  `users` and `leagues` for paging.

**Step 2: admin sign-in.**
- `POST /api/v1/admin/session` with a Google ID token: signature against Google's cached keys, audience, issuer,
  `email_verified`, `ADMIN_EMAILS`. Rate limited.
- Session cookie and `admin_sessions`; sign-out and sign-out everywhere. ADMIN role only from a live session.
- CORS with credentials for `ADMIN_ORIGIN` on `/api/v1/admin/**` only; `Origin` check and custom header on changes.
- Tests: app token refused on admin routes, admin cookie useless on app routes, empty allowlist denies, wrong audience,
  expired and revoked sessions, CORS.

**Step 3: moderation API.**
- Reports list and detail, dismiss, resolve, remove image, suspend (live-auction guard), unsuspend, release a
  suspended number, suspended list, audit log. One service; the script writes `admin_email = 'script'`.

**Step 4: email (SES).**
- New-report alert, at most one per 15 minutes, count and link only. Sign-in alert. Bounce count for system health.

**Step 5: Privacy Policy and Data safety.** Activity days, admin access, SES, retention. Live on the web before
Step 6 deploys.

**Step 6: live data and activity.**
- Activity: today's ids in memory, batched insert every 5 minutes, skip internal users at read time.
- In-memory today counters (after commit), reseed at start and midnight; ring buffer of 50 anonymised events.
- `GET /api/v1/admin/live` SSE: snapshot every 5 s when changed, only with a subscriber.

**Step 7: nightly job and retention.**
- 00:10 IST, advisory lock, upsert into `metrics_daily` (users, profiles, leagues by status, joins, auctions, bids,
  reports, OTP funnel before deletion, district splits, retention cohorts), excluding internal users; `job_runs` row.
- Backfill command for past days (OTP funnel can't be backfilled).
- Purges: activity over 13 months, closed reports past the policy period.

**Step 8: insights and system health API.**
- History by range with `Cache-Control: private` until midnight and ETag; today snapshot from memory; release markers.
- Health: last job run, OTP failure rate, SMS against the daily cap, MSG91 balance if its API allows, SES bounces.

**Step 9: browsers and search.**
- Leagues and users: keyset lists, capped totals, grouped counts, detail tabs (sections 6, 7).
- Exact phone lookup, internal flag, payment-proof view audit, global search by name or id.

**Step 10: admin web.**
- New `admin-web/` (Next.js static export, TypeScript), Google Sign-In, API client with credentials, routes per design.
- Charts with visx following the dataviz rules: hover readouts, table view, no dual axis, refetch keeps the frame.
- Security headers on the host: CSP, `X-Frame-Options: DENY`, HSTS, `noindex`.
- Unit tests and Playwright e2e against a local backend.

**Step 11: deploy and check.**
- Railway static service, `admin.crichere.com`, backend variables. Verify sign-in, every action, alerts, live stream
  and nightly job on production.

**Step 12: payment screenshot exposure (G9).** Done 2026-11-21: already private (see G9).

## 10. Owner tasks

| # | Task | When |
|---|---|---|
| O1 | Turn on 2-Step Verification or a passkey for `aranyventures@gmail.com` | Before Step 2 |
| O2 | Google Cloud (Firebase project): create an OAuth client, type Web, origins `https://admin.crichere.com` and `http://localhost:3001`. Send me the client ID (no secret needed) | Before Step 2 |
| O3 | AWS SES, same region as the bucket: verify domain `crichere.com` (Easy DKIM, 3 CNAMEs), verify `aranyventures@gmail.com` as recipient (sandbox is enough) | Before Step 4 |
| O4 | AWS IAM for the backend user: `ses:SendEmail` limited to that identity, `ses:GetAccount`; confirm `s3:DeleteObject` on the media bucket | Before Step 4 |
| O5 | Hostinger DNS: SES DKIM CNAMEs; DMARC TXT if missing; later CNAME `admin` to Railway | Step 4, Step 11 |
| O6 | Railway: new service for `admin-web`, custom domain; backend variables `ADMIN_EMAILS`, `GOOGLE_ADMIN_CLIENT_ID`, `ADMIN_ORIGIN`, `SES_REGION`, `ALERT_FROM`, `ALERT_TO` | Step 11 |
| O7 | List the internal accounts (your numbers, reviewer number, test numbers) | Step 9 |
| O8 | Approve the Privacy Policy changes; update Play Data safety if asked | Step 5 |
| O9 | Check whether your MSG91 plan exposes a balance API | Step 8 |
| O10 | S3 access check: give me read access or run a command I send | Step 12 |
| O11 | Review the Claude Design boards | Step 0 |

## 11. Verification

- Backend unit and integration tests per step; security tests in Step 2.
- `EXPLAIN` for every list and history query: index scans only, no sequential scan on growing tables.
- Load check: dashboard open for 10 minutes makes no database query apart from the first history read.
- Playwright e2e on the admin site; render each screen beside its Claude Design board.

## 12. Cloud setup check (2026-11-21, read-only)

- AWS CLI runs as `crichere-claude` (account 533848567884), the same key the backend uses (OPEN-ITEMS). It has no SES
  or IAM rights, so SES setup and permission changes need the owner. Media bucket `crichere-media-prod` is in
  `ap-south-1`; SES goes in the same region.
- Google Cloud project `crichere` (number 111016291164) is active; `gcloud` is signed in as the owner's
  aswincodename47 account. Google offers no command-line or API way to create an OAuth web client for Google
  Sign-In (the IAP OAuth APIs were shut down in March 2026), so the client is made in the Cloud Console.
- Done 2026-11-21 (owner approved, Claude in the Console): Google Auth Platform on `crichere`: app name "Crichere
  Admin", support and contact email aswincodename47@gmail.com, audience External in Testing mode (only listed test
  users can sign in), Google API Services User Data Policy accepted by the owner. Test user: aranyventures@gmail.com
  only (added by the owner). Web client "Crichere admin web", JavaScript origins `https://admin.crichere.com` and
  `http://localhost:3001`, no redirect URIs. Client ID (public, goes in `GOOGLE_ADMIN_CLIENT_ID`):
  `111016291164-885leerfs3tqgc4g2mkh2c4k1pn5jlrj.apps.googleusercontent.com`. The client secret is not used (ID-token
  sign-in only) and was not copied anywhere.
- AWS (owner choice): a temporary inline policy `crichere-ses-setup-temp` on `crichere-claude`, SES identity setup only,
  region ap-south-1; removed after setup. Backend sending permission comes with Step 4.
- Done 2026-11-21: SES identities in ap-south-1 (account in sandbox: 200 a day, 1 a second, verified recipients only,
  which fits alerts to the owner). Domain `crichere.com` with Easy DKIM (RSA 2048), pending the three DNS records
  below; `aranyventures@gmail.com` pending the owner's click on Amazon's verification email. Existing DNS kept: Hostinger
  MX and SPF, and a DMARC record. SES signs with DKIM, so DMARC passes through DKIM alignment; no SPF change needed.

  | Type | Name (host) | Value |
  |---|---|---|
  | CNAME | `dofopnodjpnlte5nbfdq2qjvmikhqvuz._domainkey` | `dofopnodjpnlte5nbfdq2qjvmikhqvuz.dkim.amazonses.com` |
  | CNAME | `k4mqhlt3muj2airwkdp7dlhzcygyghoq._domainkey` | `k4mqhlt3muj2airwkdp7dlhzcygyghoq.dkim.amazonses.com` |
  | CNAME | `2alxuxg4s2euzz63nlxytx2v7r3qkg33._domainkey` | `2alxuxg4s2euzz63nlxytx2v7r3qkg33.dkim.amazonses.com` |
