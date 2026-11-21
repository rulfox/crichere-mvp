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
- Decided 2026-11-21 (owner): the 15-minute limit is kept in a database row checked every minute (not in memory);
  bounces come from SES `GetSendStatistics` (v2 `GetAccount` has no bounce numbers); the sign-in email shows the
  admin's email, time in IST, IP address and browser.

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
| O4 | AWS IAM for the backend user: `ses:SendEmail` limited to that identity (policy in section 13, Step 4), `ses:GetSendStatistics`; confirm `s3:DeleteObject` on the media bucket | Before Step 11 deploy |
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
  which fits alerts to the owner). Domain `crichere.com` with Easy DKIM (RSA 2048); the three DNS records below were added in
  Hostinger by Claude with the owner's approval on 2026-11-21 and resolve publicly; `aranyventures@gmail.com` pending the owner's click on Amazon's verification email. Existing DNS kept: Hostinger
  MX and SPF, and a DMARC record. SES signs with DKIM, so DMARC passes through DKIM alignment; no SPF change needed.

  | Type | Name (host) | Value |
  |---|---|---|
  | CNAME | `dofopnodjpnlte5nbfdq2qjvmikhqvuz._domainkey` | `dofopnodjpnlte5nbfdq2qjvmikhqvuz.dkim.amazonses.com` |
  | CNAME | `k4mqhlt3muj2airwkdp7dlhzcygyghoq._domainkey` | `k4mqhlt3muj2airwkdp7dlhzcygyghoq.dkim.amazonses.com` |
  | CNAME | `2alxuxg4s2euzz63nlxytx2v7r3qkg33._domainkey` | `2alxuxg4s2euzz63nlxytx2v7r3qkg33.dkim.amazonses.com` |
- Done 2026-11-21: both SES identities verified (domain DKIM SUCCESS, owner clicked the address link). Test email sent
  from `Crichere Admin <alerts@crichere.com>` to aranyventures@gmail.com (SES message
  `010901a1fcf9287a-84841c13-b7d5-41c6-812e-029432776e7c-000000`). Sender for alerts: `alerts@crichere.com`.
  Owner removes `crichere-ses-setup-temp` next. Step 4 adds a permanent `ses:SendEmail` grant for the backend,
  limited to the `crichere.com` identity in ap-south-1.

## 13. Build progress

**Steps 1 and 2 done (2026-11-21), tests green (569 backend tests, 17 new).**
- V26 (`V26__admin.sql`): `moderation_actions.admin_email`, `admin_sessions`, `users.is_internal`,
  `user_activity_days`, `metrics_daily`, `release_markers`, `job_runs`, BRIN indexes on `users.created_at`,
  `leagues.created_at`, `league_players.joined_at`, `auction_bids.placed_at`, keyset indexes on users and leagues.
- Admin sign-in (`backend/.../admin/`): its own Spring Security chain for `/api/v1/admin/**`, ordered before the app
  chain; app bearer tokens are never read there and the admin cookie is never read on app routes.
  - `POST /api/v1/admin/session` takes the Google ID token (`credential`), checks RS256 against Google's JWKS
    (cached), issuer, audience = `GOOGLE_ADMIN_CLIENT_ID`, expiry, `email_verified`, then the `ADMIN_EMAILS`
    allowlist (case-insensitive). One refusal message for every failure. 10 attempts per IP per 15 minutes.
  - Session: an opaque 256-bit random token (decided over a JWT: nothing to sign or rotate, and revocation is a row
    update), stored only as SHA-256 in `admin_sessions`. Cookie `crichere_admin`: HttpOnly, Secure, SameSite=Strict,
    host-only, `Path=/api/v1/admin`, 30 minutes, no refresh. A session also stops working if its email leaves the
    allowlist.
  - `GET` current session, `DELETE` sign out, `POST /sign-out-everywhere`. Sign-in and sign-outs are audited in
    `moderation_actions` with the admin's email.
  - Every non-GET admin request needs `Origin` = `ADMIN_ORIGIN` and `X-Crichere-Admin: 1`. CORS with credentials only
    for `ADMIN_ORIGIN`.
- Backend settings (Railway, at deploy): `ADMIN_EMAILS=aranyventures@gmail.com`,
  `GOOGLE_ADMIN_CLIENT_ID=111016291164-885leerfs3tqgc4g2mkh2c4k1pn5jlrj.apps.googleusercontent.com`,
  `ADMIN_ORIGIN=https://admin.crichere.com`. Local profile defaults: origin `http://localhost:3001`, non-secure cookie.
- Not verified yet: a real Google sign-in end to end (needs the admin site, Step 10); the sign-in email alert comes
  with Step 4.

**Step 3 done (2026-11-21), tests green (585 backend tests, 16 new in `AdminModerationFlowIntegrationTest`).**
- `AdminModerationService` / `AdminModerationController`: reports list (keyset, OPEN oldest first) and detail
  (evidence, target, private reporter, history), dismiss, resolve, remove image (S3 delete only when unused and
  under the row's own prefix, after the app change commits), suspend (live-auction guard, `duringAuction`
  override), unsuspend, suspended accounts and blocked numbers, release number, audit log. Every action needs a
  note and records the admin's email. The script now writes `admin_email = 'script'`.
- Tests cover: session and admin-site guard and required note on every action; report list order, filters and
  cursor paging; detail; dismiss/resolve once with audit; remove image for each stored-file outcome (deleted, still
  used, not owned, unknown location) plus league logo and owner notice; suspend/unsuspend (tokens revoked, device
  tokens dropped, listed with admin and note), live-auction guard and its override; release number; audit log
  filters (`script` rows) and paging.

**Step 4 done (2026-11-21), tests green (597 backend tests, 12 new).**
- `AdminAlerts.kt`: `AlertMailer` and `SesStatistics`, both served by `SesAlertGateway` on the classic SES API
  (`software.amazon.awssdk:ses`). Settings `crichere.alerts.*`: `SES_REGION`, `ALERT_FROM`, `ALERT_TO`
  (comma-separated). Any of them blank (local, CI) means alerts are logged and skipped, never sent.
- New-report alert (`ReportAlertService`, `ReportAlertJob` every minute): V27 `alert_state` holds the last send time
  and a watermark, locked with `for update skip locked`. Counts reports created after the watermark and up to 10
  seconds ago (so a report still committing is counted next run, not lost). The email goes out before the row's
  update commits: a failed send changes nothing and retries next minute. Body: new count, open total, link to
  `ADMIN_ORIGIN/reports`. No names, reasons or details. Starts from the migration time; older reports were
  handled with the script.
- Sign-in alert: `AdminSessionService.signIn` publishes `AdminSignedIn`; sent after commit on the async executor,
  so a refused sign-in sends nothing and a failed send never affects the sign-in. Browser string is cleaned
  (control and invisible formatting characters removed, 200 characters). Spring Security's firewall already
  rejects CR/LF in headers. The IP is `remoteAddr`, like the rate limiters: behind Railway's proxy it may be the
  proxy's address until `server.forward-headers-strategy` is set (existing note in `AuthRateLimitFilter`).
- Bounces: `SesStatistics.lastTwoWeeks()` (delivery attempts, bounces, complaints, rejects); shown in Step 8.
- Owner, before the Step 11 deploy: add this inline policy to the backend IAM user. In the SES sandbox, sending
  also needs the verified recipient identity in `Resource`.

  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Sid": "SendAdminAlerts",
        "Effect": "Allow",
        "Action": "ses:SendEmail",
        "Resource": [
          "arn:aws:ses:ap-south-1:533848567884:identity/crichere.com",
          "arn:aws:ses:ap-south-1:533848567884:identity/aranyventures@gmail.com"
        ],
        "Condition": { "StringEquals": { "ses:FromAddress": "alerts@crichere.com" } }
      },
      { "Sid": "ReadSendStatistics", "Effect": "Allow", "Action": "ses:GetSendStatistics", "Resource": "*" }
    ]
  }
  ```

  Railway backend variables: `SES_REGION=ap-south-1`, `ALERT_FROM=Crichere Admin <alerts@crichere.com>`,
  `ALERT_TO=aranyventures@gmail.com`.
- Not verified yet: a real SES send from the backend (needs the policy above); checked at deploy (Step 11).

**Step 5 in review (2026-11-21): PR #4 to `master` (branch `privacy-admin-update`, commit `270b597`).**
- Decided with the owner: a dated update with a "What changed" note and `TERMS_VERSION` 2026-11-21 (new sign-ins
  record consent to this text), no in-app notice (closed test, users are testers); a separate PR so it is live
  without the rest of this branch; the owner changes Data safety in Play Console from `docs/legal/PLAY-CONSOLE.md`.
- Policy now covers: days the app is used (13 months), usage totals as a purpose, the team's admin site (Google
  sign-in on an allowlist, actions, screenshot views and phone lookups logged, phone numbers never shown), team
  access to payment screenshots for reports or disputes, SES (no user data), admin action logs kept 24 months
  (chosen to match moderation records; owner to confirm in the PR), activity erased with the account.
- Data safety: App interactions gains the Analytics purpose. Nothing else changes.
- Open for the owner: section 8 promises in-app or push notice for material changes; this update has only the
  dated note. Suggest telling testers directly.
- Carried into later steps: Step 6 adds `user_activity_days` to `AccountPurgeService` (the `users` row is kept, so
  the cascade never fires) and drops the purged id from the in-memory set; Step 7 purges admin-only audit rows
  (sign-ins, views, lookups) after 24 months. Step 6 deploys only after PR #4 is merged and live, and the Data
  safety change is submitted.
- Next: Step 6 (live data and activity). Merge `master` into this branch after PR #4.

**Step 6 done (2026-11-21), tests green (609 backend tests, 12 new in `AdminLiveIntegrationTest`). Not deployable
until PR #4 is live and the Data safety change is submitted.**
- Decided with the owner: an active day is any successful signed-in app request (backend only, no app release);
  feed lines are event type and district only (no names, league names or ids); internal accounts are left out of
  today's counters, matching the nightly history.
- `ActivityFilter` (app chain, after `SuspensionFilter`) marks the day only when the response is below 400, so
  refused, suspended or failed requests don't count. `ActivityTracker` keeps today's ids in memory and writes new
  ones every 5 minutes (and at shutdown) as one `insert ... select from users ... on conflict do nothing`; a failed
  flush keeps the ids for the next try. Today's ids reload from the table at startup.
- Account erase: `AccountPurgeService` calls `ActivityTracker.erase` (rows and in-memory trace).
- `LiveEvent`s published by the services (sign-up, OTP send, league created, player joined, report filed, bid,
  auction state from `saveAndBroadcast`) are counted by `LiveMetrics` after commit; counting errors are logged and
  never reach the request. Counters reseed from the database at startup and 00:00 IST; at the day change they reset
  first. Internal ids are loaded at reseed; Step 9's flag calls `LiveMetrics.setInternal`.
- Feed: last 50 of sign-up, league created, player joined, report filed, auction started, auction finished. Bids
  appear only as a count per minute (one line per bid would flood it).
- Snapshot: today (sign-ups, leagues, joins, reports, OTP sends, bids, active users), now (live auctions, bids in the
  last minute, auction viewers from `AuctionBroadcastService.subscriberCount`), feed.
- `GET /api/v1/admin/live` (SSE, `no-store`, `X-Accel-Buffering: no`): snapshot on connect, then every 5 s only if it
  changed, a `ping` event every 10 s otherwise (a named event, since EventSource never surfaces comments); nothing built with no subscriber. At most 3 streams per session
  (429 after). A stream times out at its session's expiry and closes at once on sign-out or sign-out everywhere
  (no database check per tick). An admin removed from `ADMIN_EMAILS` loses streams at the restart that applies it.
- Next: Step 7 (nightly job and retention).

**Step 7 done (2026-11-21), tests green (615 backend tests, 6 new in `AdminMetricsIntegrationTest`, plus an auction
completion-time check).** Decisions taken under the owner's "complete all steps" goal, for review:
- Metrics (`Metric` in `AdminMetricsJob.kt`), all leaving internal accounts out. Per day: `new_users`, `users_total`,
  `active_users_day/week/month` (distinct users over 1, 7 and 30 days), `leagues_created`, `active_leagues`, `joins`,
  `bids`, `auctions_completed`, `reports_opened`, `reports_closed`, `report_close_median_minutes_30d`,
  `otp_people_sent`, `otp_people_signed_in`. Splits, current night only (no history to rebuild them from):
  `leagues_by_status` (open, auction_live, auction_done, completed; the app has no draft state) and
  `users_by_district` (`State|District`).
- Funnel: code sent and signed in count distinct numbers per day (counted the night after, before the OTP rows are
  deleted); "signed in" means the number reached an account, so a returning user who never typed the code counts
  too. Profile done and joined a league are counted per sign-up day (`cohort_profile_done`, `cohort_joined`) and
  recounted every night for 35 days, since people finish later.
- Retention: weekly sign-up cohorts (Monday start, IST), `retention_cohort_size` and `retention_week` with dimension
  `w0..w11`, recounted nightly for the last 12 weeks.
- V28 `leagues.auction_completed_at`: set in `AuctionService.saveAndBroadcast` when an auction reaches COMPLETED,
  cleared when an undo reopens it; past auctions take their last bid time.
- `AdminMetricsJob` at 00:10 IST: yesterday, the rolling recounts, then the purges, in one transaction with a
  transaction-scoped advisory lock and a 120 s statement timeout; each run is a `job_runs` row (a run that finds the
  lock held is recorded as skipped).
- Purges (Privacy Policy section 4): activity days older than 13 months; closed reports 24 months after closing;
  moderation records older than 24 months, except a suspension on an account still suspended and actions on an open
  report; finished `job_runs` after 90 days. Run nightly rather than monthly (same result, simpler).
- Backfill: `POST /api/v1/admin/metrics/backfill {from, to}` (admin session, site guard, audited as
  `METRICS_BACKFILL`), at most 400 days, ending before today. Rebuilds daily flows and active-user counts only.
- Limit: OTP rows can be deleted by a send after 00:10 if they expired over a day earlier, so a few codes sent just
  after midnight two days back may be missed. Accepted.
- Next: Step 8 (insights and system health API).

**Step 8 done (2026-11-21), tests green (5 new in `AdminInsightsIntegrationTest`).**
- `GET /api/v1/admin/insights/history?from&to`: `metrics_daily` rows plus release markers, at most 400 days;
  `Cache-Control: private, max-age` until 00:15 IST (after the nightly run) and a SHA-256 ETag (304 on match).
- `GET /api/v1/admin/insights/today`: the live snapshot from memory plus a moderation summary (open reports by reason,
  oldest open, suspended accounts, images removed in 30 days), `no-store`; the summary is cached for 60 s.
- `GET /api/v1/admin/health` (cached 60 s): last nightly run; OTP codes sent today and the share that expired unused
  (labelled "failed" on the board: no way to tell a wrong code from a code never typed); SMS sends against the global
  daily cap, which resets at 00:00 UTC (05:30 IST), not IST midnight as the board says; SES 14-day sends, bounces and
  complaints when configured; MSG91 balance `null` until owner task O9 confirms an API.
- Release markers: list, add, delete (`ADD_RELEASE_MARKER` / `DELETE_RELEASE_MARKER` audited).
- The admin `NOT_FOUND` / conflict handler now covers every admin controller.
- Next: Step 9 (league and user browsers, phone lookup, internal flag, search).

**Step 9 done (2026-11-21), tests green (627 backend tests, 7 new in `AdminBrowseIntegrationTest`).**
- `AdminBrowseService` / `AdminBrowseController`, all `no-store`, read-only except the internal flag.
- Leagues: `GET /api/v1/admin/leagues` with search (league or organizer name, contains), state, district, status
  (open, auction_live, auction_done, completed: the app has no draft state, so the board's "Draft" is not offered),
  created range, has open reports, include internal; sorts newest, starts, name. Keyset cursors carry the sort value
  (timestamps as epoch microseconds, exact); total counted once per filter (first page only), capped at 1000.
  Per-row counts for the page in one query. Tabs: `/{id}` overview (every field, ground, followers, organizer,
  co-organizers), `/franchises`, `/players` (fee state NO_FEE / PROOF_UPLOADED / WAITING_FOR_PROOF: the app stores
  no "organizer marked paid" flag, so the board's "Paid" is shown as proof uploaded), `/auction` (settings and up to
  2000 bids), `/awards`, `/moderation`, `/timeline` (stored timestamps only).
- Payment proof: `POST /api/v1/admin/payment-proofs/{player|franchise}/{rowId}` returns a short-lived signed link and
  writes `VIEW_PAYMENT_PROOF` (POST because it writes the audit row; site guard applies).
- Users: `GET /api/v1/admin/users` with name search, state, district, status (active, profile_incomplete,
  suspended, deletion_pending), joined range, open reports, include internal; sorts newest, name, last active. Purged
  accounts are not listed; deleted ones show as "Deleted user". Tabs: `/{id}` profile, `/leagues`, `/auction`,
  `/activity` (days, 13 months), `/moderation` (block counts only), `/account` (push devices and live app sessions as
  counts). No response carries a phone number.
- Phone lookup: `POST /api/v1/admin/users/lookup {phone}` normalises to +91, hashes like sign-in, exact match only;
  30 lookups an hour per admin; audited as `PHONE_LOOKUP` with the match count, never the number.
- Internal flag: `PUT /api/v1/admin/users/{id}/internal {internal}`, audited (`MARK_INTERNAL` / `UNMARK_INTERNAL`),
  updates the live counters at once.
- Search: `GET /api/v1/admin/search?q=` five leagues and five people by name, or one of each by exact id.
- Not done: `EXPLAIN` checks from section 11 on production-sized data; name search uses `ilike` without a trigram
  index, fine at today's size.
- Next: Step 10 (admin web).

**Step 10 done (2026-11-21): `admin-web/`, 28 unit tests and 9 Playwright e2e tests green against a local backend.**
- Next.js 16.4 static export, React 19.3, visx 4 (scales and shapes), TypeScript 5.9 and ESLint 9 kept on the same
  majors as `web-viewer` (TypeScript 7 and ESLint 10 are toolchain jumps for their own phase).
- Styles ported from `docs/design/admin/admin-kit.js` (AK.css) as CSS variables, light and dark. Fonts self-hosted
  via `next/font` (Archivo, Inter, IBM Plex Mono) and the `material-symbols` package (rounded, 5.2 MB, cached).
- Routes: `/signin/`, `/` overview, `/reports/`, `/reports/view/?id=`, `/leagues/`, `/leagues/view/?id=`,
  `/users/`, `/users/view/?id=`, `/suspended/`, `/audit/`. Ids in the query string (static export can't pre-render
  ids).
- Sign-in: Google Identity Services draws its own button (Google's branding rules), so the board's custom button is
  replaced by Google's; the rest of S1 is as designed. `next` after sign-in accepts same-site paths only.
- API client: cookie credentials on every call, `X-Crichere-Admin: 1` on changes, any 401 opens "Your session
  ended". Lists keep the previous page faded while loading. Every chart has the Table switch.
- Overview summaries are generated from the same numbers as the charts (`lib/overview.ts`), counts instead of
  percentages under 20. Deviations from O1: per-auction live cards are three totals (no per-auction API); the State
  and District filter chips are left out (history isn't split by place except users by district); SMS card says the
  cap resets at 05:30 IST, which is when the backend resets it.
- Live stream: the backend's quiet keep-alive is now a `ping` event every 10 s (EventSource ignores comments); the
  site marks the stream stale after 15 s without an event and closes it when the tab is hidden.
- Host: `Dockerfile` (build, then Caddy). `npm run build:host` writes `deploy/Caddyfile` with a CSP that allows the
  exported inline scripts by SHA-256 only, plus Google's sign-in script and frame; `X-Frame-Options: DENY`, HSTS,
  `X-Robots-Tag: noindex`, `robots.txt` disallow, no referrer.
- e2e: Google sign-in can't run unattended, so `e2e/fixtures.ts` inserts an `admin_sessions` row for the local
  allowlisted e2e email and sets its cookie; no test-only path exists in the backend.
- Not verified: listed under DESIGN-REVIEW Follow-ups (Phase 20 admin web).
- Host checked 2026-11-21: the production Docker image (Caddy) answers every page and real 404s with the CSP,
  `X-Frame-Options: DENY`, HSTS and `X-Robots-Tag`; `robots.txt` disallows all; a browser pass over every route
  found no CSP violation (`e2e/host.spec.ts`, project `host`).
- Security review fixes: sign-in `next` is resolved with the URL parser, compared by origin, and the normalised path
  is checked again (`/	/evil`, `/.//evil`, `/%2e//evil` all fall back to `/`).

**Step 11 runbook (deploy and check).** Order matters: the backend must be live with Phase 20 before the site.
1. PR #4 (privacy) merged 2026-11-21. Owner: Data safety change in Play Console (docs/legal/PLAY-CONSOLE.md).
2. Owner: IAM inline policy for SES on the backend user (section 13, Step 4).
3. Backend variables on Railway (backend service): `ADMIN_EMAILS=aranyventures@gmail.com`,
   `GOOGLE_ADMIN_CLIENT_ID=111016291164-885leerfs3tqgc4g2mkh2c4k1pn5jlrj.apps.googleusercontent.com`,
   `ADMIN_ORIGIN=https://admin.crichere.com`, `SES_REGION=ap-south-1`,
   `ALERT_FROM=Crichere Admin <alerts@crichere.com>`, `ALERT_TO=aranyventures@gmail.com`.
4. Merge `admin-web` into `master` (PR). Railway redeploys the backend; Flyway runs V26 to V28 on production.
5. New Railway service `admin-web` from the same repo, root directory `admin-web`, Dockerfile build, build args
   `NEXT_PUBLIC_API_BASE=https://api.crichere.com`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (as above),
   `NEXT_PUBLIC_ENV_LABEL=Production · api.crichere.com`; custom domain `admin.crichere.com`.
6. Hostinger DNS: CNAME `admin` to the target Railway gives.
7. Check: sign in with Google, alert email arrives, overview loads, live stream connects, a test report can be
   dismissed, audit log shows it, `curl -I https://admin.crichere.com` shows the headers.
8. Mark the owner and reviewer numbers internal (O7) from the user page.

Step 11 status (2026-11-21, 12:45 IST):
- PR #5 merged by the owner (`0205db9`). Backend deployed; Flyway applied V26, V27 and V28 on production.
  Checked: `/api/v1/admin/session` without a cookie is 401, the CORS preflight allows only
  `https://admin.crichere.com` with credentials (other origins 403), app API still answers.
- Railway service `admin-web` created by Claude with the owner's approval (id `ead9618b-97a2-46bd-a46b-de4c95e72b7a`,
  root `/admin-web`, Dockerfile, watch `/admin-web/**`, build variables `NEXT_PUBLIC_API_BASE`,
  `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_ENV_LABEL`), deployed. Custom domain `admin.crichere.com` attached.
- Still to do: Hostinger DNS records Railway asked for, CNAME `admin` to `32aoxk02.up.railway.app` and TXT
  `_railway-verify.admin` = `railway-verify=c3dd56b331237aed008810aaea6639bdba72beae0de5797b4efcfae10484c630`
  (Claude via the Playwright browser after a restart, owner signed in to hPanel); then certificate, headers, sign-in
  page, a real Google sign-in and its alert email, and the client IP the backend sees. Owner: Data safety change and
  SES IAM policy (until the policy is added, alert emails fail and are logged; nothing else is affected).

Step 11 update (2026-11-21, 18:35 IST):
- DNS added in Hostinger by Claude (Playwright, owner signed in): CNAME `admin` -> `32aoxk02.up.railway.app`, TXT
  `_railway-verify.admin`. Both resolve; Railway issued the certificate.
- https://admin.crichere.com is live: CSP, `X-Frame-Options: DENY`, HSTS, `X-Robots-Tag`, nosniff present;
  `robots.txt` disallows all; unknown paths 404; every route loads with no CSP violation (`e2e/host.spec.ts`).
- Blocker found: Google's button fails with "The given origin is not allowed for the given client ID". The OAuth
  client does list `https://admin.crichere.com` and `http://localhost:3001`. Cause: `Referrer-Policy: no-referrer`
  (header and Next.js metadata), so the browser sends Google no Referer (seen as `referer: null` on the
  `/gsi/button` request) and Google can't check the origin. Fix on branch `phase20-deploy`: `strict-origin` in
  `admin-web/app/layout.tsx` and `deploy/Caddyfile.template` (origin only, never a path), plus `app/icon.svg`
  (favicon 404). Not yet proven: a local check on port 3001 timed out loading the page. Next session: confirm the
  `/gsi/button` request now carries a Referer and returns 200, open a PR, owner merges, then the real Google sign-in,
  the alert email (after the SES policy) and the client IP check.

Step 11 update (2026-11-21, late):
- Fix proven before merge. The static build (live client ID) was loaded in Chromium under the real origin
  `https://admin.crichere.com` (Playwright route serving `out/`, so no deploy needed): `/gsi/client`, `/gsi/style`
  and `/gsi/button` all carry `referer: https://admin.crichere.com/` and return 200, no GSI console error, and the
  "Sign in with Google" button renders. Same check on the live (old) build: `referer: null`, `/gsi/button` 400,
  "The given origin is not allowed for the given client ID".
- A check on `http://localhost:3001` still gets 403 even with the Referer present: Google wants `http://localhost`
  (no port) listed as well for local testing. Not needed for production; add it to the OAuth client only if local
  sign-in testing is wanted.
- The Playwright MCP browser hangs on navigation in this environment; a standalone `@playwright/test` script works.
- Next: PR `phase20-deploy` to master, owner merges (Railway redeploys admin-web), then the real Google sign-in, the
  alert email (after the SES policy) and the client IP check.

Step 11 update (2026-11-21, 21:25 IST):
- PR #6 merged (`8f6790e`); admin-web redeployed. Live `https://admin.crichere.com/signin/` now sends
  `Referrer-Policy: strict-origin`; `/gsi/client`, `/gsi/style`, `/gsi/button` all 200 with
  `referer: https://admin.crichere.com/`, no GSI console error; `/icon.svg` 200; CSP, HSTS, `X-Frame-Options` unchanged.
- Left for Step 11 (owner needed): real Google sign-in with the admin account, SES IAM policy (O4; the local CLI user
  can't read IAM, so not checkable here) then the sign-in alert email, client IP shown in the session/audit log, a
  test report dismissed and seen in the audit log, owner and reviewer numbers marked internal (O7), Play Data safety
  change (O8).

Step 11 update (2026-11-21, 21:30 IST): first real sign-in.
- Owner added the SES inline policy (`ses:GetSendStatistics` checked from the CLI, same IAM user). Owner signed in
  with Google at 15:57:52 UTC: `POST /api/v1/admin/session` 200, then session, insights, health and live all 200.
  The Google popup opened as a background tab once in the owner's Chrome and as a normal popup later; the site
  asks for a proper popup (features + user activation, checked in Chrome), so that was the browser, not the site.
- Bug found in production logs and fixed: when an admin live stream (SSE) ends, the container re-dispatches the
  request (`DispatcherType.ASYNC`); `AdminAuthenticationFilter` (a `OncePerRequestFilter`) doesn't run on it, so the
  dispatch was anonymous and `hasRole("ADMIN")` threw "Access Denied ... response is already committed" (ERROR
  log with stack trace on every stream end). Fix: the admin chain permits `DispatcherType.ASYNC` (it only finishes
  an already-authorized request). Test: `AdminLiveIntegrationTest` "a closed stream's async dispatch is not denied"
  (failed with the production message before the fix). Backend 628/628 tests pass.
- Follow-up, not reproduced: two CORS preflights `OPTIONS /api/v1/admin/session` got 403 (15:53:38 and 15:57:13
  UTC, Chrome UA, owner's IP). Preflights from `https://admin.crichere.com` for GET/POST/DELETE with
  `content-type, x-crichere-admin` all return 200; only `Origin: null` gets 403. A normal page load sends no
  preflight (CDP check). Sign-in unaffected. Railway HTTP logs don't show the Origin, so the sender is unknown
  (possibly a browser extension).
- Still to check: alert email in the inbox, client IP the backend recorded, a test report dismissed and seen in the
  audit log, O7, O8.
- Alert email for the 15:57 sign-in arrived (owner confirmed).
- Client IP was wrong: the admin site showed `100.64.0.5`, Railway's proxy, not the owner's `103.182.167.128`. So in
  production every per-IP limit (OTP send 20/h, OTP verify 60/h, auth 20/h, admin sign-in 10/15 min) was shared by
  all users. Decided with the owner (2026-11-21): fix in the same PR as the stream fix.
  Fix: `server.forward-headers-strategy: native` and `server.tomcat.remoteip.internal-proxies` = 100.64.0.0/10 only
  (application.yml). Tomcat's RemoteIpValve takes the rightmost X-Forwarded-For entry that isn't a trusted proxy, so
  an address a client puts in the header itself is ignored; requests not from that range keep their socket address.
  Test: `ClientIpIntegrationTest` (real Tomcat, loopback as the proxy) fails with the setting off. 629/629 pass.
- After deploy: the admin session should show the owner's real IP; spoof check = 11 sign-in POSTs from one machine
  with a different `X-Forwarded-For` each must reach 429 (if Railway passed client headers through unchanged and
  appended nothing, they wouldn't).

Step 11 update (2026-11-21, 21:50 IST): client IP, second try.
- PR #7 merged (`5f51233`), backend deployed. Production check failed: 25 refused admin sign-ins from one machine
  (11 with a different made-up `X-Forwarded-For` each, 11 with one constant made-up value, 3 with none) never
  reached the 10-attempt limit. So the resolved IP differed on every request: Railway's `X-Forwarded-For` evidently
  ends with an address outside 100.64.0.0/10 that changes per request. Per-IP limits were effectively off from this
  deploy until the next (per-phone limits and the daily OTP cap still applied).
- Railway's docs (Networking > Specs & Limits, Request Headers) name `X-Real-IP` "for identifying client's remote
  IP" and say nothing about `X-Forwarded-For`. Decided with the owner: read `X-Real-IP` instead
  (`server.tomcat.remoteip.remote-ip-header: x-real-ip`, still trusted only from 100.64.0.0/10), and log the IP on
  every refused or rate-limited admin sign-in so production is observable. `ClientIpIntegrationTest` now also
  checks that `X-Forwarded-For` is ignored. 630/630 pass.
- After deploy: 11 refused sign-ins, each with a different made-up `X-Real-IP`, must reach 429 (shows Railway
  overwrites a client-sent `X-Real-IP`), and the logged IP must be the owner's 103.182.167.128.
- PR #8 merged (`553df9f`), backend deployed. Checked on production (2026-11-21, 17:20 UTC): 11 refused admin
  sign-ins from one machine, each with a different made-up `X-Real-IP` and `X-Forwarded-For`, got 401 x10 then 429,
  so Railway overwrites a client-sent `X-Real-IP` and the limit is per client. Every refusal logged
  "Admin sign-in refused from 157.51.238.232", which is this machine's public IP at that moment (three lookup
  services agree; it had changed from 103.182.167.128 earlier in the day). Client IP fixed.
- Step 11 left: dismiss a test report and see it in the audit log (owner signed in), O7 internal numbers, O8 Play
  Data safety. The two 403 CORS preflights stay a follow-up (not reproduced).
- Moderation check on production (2026-11-21): a test account (signed in with OTP) filed report `4c8a9bf1` from
  the app at 17:34:04 UTC (201); the owner signed in at 17:34:34 and dismissed it at 17:35:39 (204); owner confirmed
  the audit log entry. The owner's sign-in came from the real IP. Still to confirm: the new-report alert email.
