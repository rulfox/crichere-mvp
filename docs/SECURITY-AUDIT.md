# Security audit (2026-10-04)

A review of the whole backend, plus the web viewer's server-side fetch, for vulnerabilities that can
actually be exploited. Findings are fixed one at a time; each section records the decision and what
is still open.

**Scope covered:**
- Auth: JWT, refresh tokens, Firebase session, MSG91 OTP and the rate-limit filter.
- Authorization: league, role, player, franchise and auction services.
- Profile, uploads, device tokens, repository queries, error handling and CORS.
- Web viewer: the share card's server-side fetch.

**Not covered yet:** the mobile apps (token storage, deep links) and the live AWS bucket and IAM
settings.

**Reviewed and sound:**
- **JWT:** HS256 is pinned before verification, and issuer, expiry, `iat` and `token_use` are all checked.
- **Refresh tokens:** 256-bit random, only a SHA-256 hash is stored, single-use rotation.
- **OTP:** attempts are spent before the check, challenges are single-use, the phone is bound to the
  challenge, and there are no oracles.
- **Authorization:** organizer checks on every write, and IDs from another league are rejected.
- **Queries:** parameters only.
- **Error bodies:** no exception messages.
- **CORS:** allowlist only, GET only, no credentials.

## Finding 1: payment screenshots were public and their URLs were easy to guess (High), fixed in code

**Problem:**
- Screenshots are stored at `leagues/{leagueId}/payments/{userId}.jpg`.
- The bucket policy made the whole bucket public-read (PHASE14).
- Both IDs are public in `GET /api/v1/leagues/{id}` (`players[].userId`, `franchises[].ownerUserId`).

So anyone, with no account, could download every payment screenshot. A UPI receipt shows the
payer's name, UPI ID or phone, bank and transaction ID. The API's hiding of `paymentScreenshotUrl`
protected nothing.

**Decision (owner, 2026-10-04): a private prefix plus presigned GET.** Unguessable keys were rejected
because a leaked URL would stay public forever.

**How it works:**
- The response mapping already decides who sees a screenshot: the organizer and the row's own user.
  For those callers it now returns a one-hour presigned GET (`S3PaymentScreenshotUrlSigner`), never
  the stored URL. Other callers still get `null`.
- **The key comes from the row, not the URL.** The stored URL is client-supplied. A player could
  submit someone else's key as their own proof and then, since they may see their own row, get a
  signed link to it. So only `leagues/{row.leagueId}/payments/{row's payer}.jpg` is ever signed. Any
  other key in our bucket returns `null`, and URLs outside our bucket pass through unchanged, as before.
- **Fails soft:** with no bucket or credentials configured (dev/CI), or if presigning fails, the
  stored URL is returned, as before.
- **No app change:** the apps already open whatever `https` URL the response carries, and the
  join/claim screens never load the uploaded image back.

**Open: owner AWS steps, in this order, after the backend deploy:**
1. Give the backend IAM user `s3:GetObject` on `arn:aws:s3:::crichere-media-prod/leagues/*/payments/*`.
   Signed links are checked against that user's permissions once the prefix is private.
2. Change the `crichere-media-prod` bucket policy so the public-read statement no longer covers
   `leagues/*/payments/*`. For example, add `"NotResource"`, or add a `Deny` for `Principal: *` with
   a condition that excludes the backend user.
3. Verify:
   - a plain screenshot URL returns 403;
   - the URL from `GET /leagues/{id}`, called as the organizer, returns 200 `image/jpeg`;
   - a logo or profile photo still returns 200 without signing.
4. Apply the same exclusion to the old `crichere-media-dev` bucket, or delete that bucket. It still
   holds copies of the screenshots.

**Verified locally (2026-10-04)** against the local backend and the real `crichere-media-prod`
bucket (read-only). The local league and payer were given the ids of the one real screenshot in the
bucket. 10 of 10 checks passed:
- **Before the fix:** the plain object URL answered 200 to anyone.
- **Who gets a link:** the payer's own join response and the organizer's league view return a signed
  link. Other users and anonymous callers get `null`.
- **Borrowed key:** an attacker who submits the payer's key as their own proof (player or franchise)
  gets `null`.
- **The link itself:** it opens the real object (200 `image/jpeg`, 62,933 bytes), and S3 rejects a
  tampered signature with 403.

**Bucket policy applied (2026-10-05, by the owner's command):** a `Deny` on `s3:GetObject` for
`leagues/*/payments/*` unless `aws:PrincipalAccount` is our account. The existing public-read
statement is unchanged.
- No IAM change was needed: the backend's signed links come from our own account, and the
  public-read `Allow` already covers them.
- The previous policy is backed up in the session scratchpad.

Checked right after applying it:
- the plain screenshot URL returns 403 to an anonymous caller;
- a signed URL from our account returns 200 `image/jpeg` (62,933 bytes);
- a league banner and a profile photo still return 200 anonymously.

**Still open:**
- The old `crichere-media-dev` bucket still serves its copy of the same screenshot publicly (200).
  - It was checked against the new bucket before deletion: every object is there, and the one
    profile photo that differs is newer in `-prod`.
  - Deleting it (and, as a fallback, removing its policy) failed: `crichere-claude` has no
    `s3:DeleteObject` or `s3:DeleteBucketPolicy` permission. The owner must delete it in the console.
- The production backend's own signed link hasn't been opened yet. That needs an organizer login on
  a league with a paid join. The local test signed with another IAM user in the same account, which
  the same policy treats identically.

**Previously unverified:** the signed link once the prefix is private. Locally it was signed with the
`crichere-claude` IAM user while the bucket was still public. Check this in step 3 above.

**Known limits:**
- A signed link works for one hour for whoever holds it.
- An organizer who leaves the league screen open for more than an hour must refresh it before opening a proof.
- Co-organizers still can't see screenshots. That's an existing rule (`callerId == organizerUserId`),
  unchanged here.

## Finding 2: a removed franchise could still bid (Medium), fixed

**Problem:**
- `AuctionService.placeBid` looked the franchise up by id and league, then checked ownership.
- A removed franchise keeps its row and its `ownerUserId`; only `removedAt` is set, either by the
  organizer's Remove or by an approved leave.
- So its owner could call `POST /auction/bids` with the old `franchiseId` and bid. The app hides
  this option, but the API accepted it.
- If that bid led when the organizer clicked Sold, the player went to a franchise that isn't in the
  league.

**Fix:** `placeBid` now rejects a franchise whose `removedAt` is set, with the same 404 as an unknown
franchise. The check runs before the ownership check and before anything is saved. No other auction
path takes a franchise id from the client: `sold` uses the leading bid, which can now only come from
an active franchise.

**Verified:**
- **Unit test:** a new case in `AuctionServiceTest` failed before the fix and passes after it.
- **Full suite:** 504 of 504 backend tests pass.
- **Locally against the backend:** the removed owner's bid is refused with 404 and no bid row is
  stored, and an active franchise still bids normally (200).

## Finding 3: the share card could be pointed at internal addresses (Low), fixed

**Problem:** `LeagueSaveRequest.logoUrl` and `bannerUrl` had no validation, and the web viewer
fetches `logoUrl` from its own server, inside Railway's private network, and followed redirects. An
organizer could point the share card at an internal address. The request was mostly blind, because
only image responses were used.

**Fix:**
- **Web viewer (done, 2026-10-05):**
  - The share card only fetches a logo whose exact origin is allowed. That's the production media
    bucket, or `SHARE_CARD_LOGO_ORIGINS` (comma-separated), which the Playwright e2e sets to its
    mock server.
  - Redirects are refused.
  - Anything else renders the monogram.
  - **Tests:** 12 new unit cases (internal host, http, look-alike host, userinfo, other port,
    metadata IP, `file:`). All 74 web unit tests and all 10 share-card e2e tests pass, and the
    typecheck is clean.
- **Backend (done, 2026-10-05):**
  - `logoUrl`, `bannerUrl` and the profile `photoUrl` must be `https://`, the same rule the payment
    and franchise-logo fields already had.
  - Two new integration tests cover it. All 506 backend tests pass.

## Mobile audit (2026-10-05)

**Reviewed and sound:**
- **Tokens:** Android stores them in DataStore, encrypted with Tink AEAD under an Android Keystore
  key, and the file is excluded from backup and device transfer. iOS uses the Keychain.
- **Logging:** Ktor logs at `INFO` (request line and status only; no headers or bodies).
- **Builds:** a release build can't use the local backend URL, because the build refuses that
  combination.
- **Deep links:** they're limited to `crichere://leagues/` and verified App Links on `crichere.com`.
- **Screenshot viewer:** it only opens `https://` URLs.
- **Repo:** no keys or signing files are committed.

**Hardening, fixed:**
- **Cleartext HTTP:** Android allowed it in every build, release included. It's now on only for
  `-Penv=local` builds, through a manifest placeholder. The merged manifest was checked for both
  the default and the local build.
- **Deep-link ids:** a link id was used as-is in an authenticated API path. A link such as
  `crichere://leagues/..%2Fauth%2F...` decodes to `../auth/...`. The impact was low, because the
  host is fixed and every GET in the API only reads. Both apps now accept only a UUID. There are 5
  Android unit tests; the iOS change is unverified until the first Xcode build.

**Also fixed (2026-10-05):**
- **iOS local networking:** the `NSAllowsLocalNetworking` exception, which allowed plain HTTP to
  local-network hosts, was removed. The iOS app only ever calls `https://api.crichere.com` (there is
  no iOS base-URL override), so default ATS now applies everywhere. The iOS README no longer points
  at a local http backend.
- **Invalid `Info.plist`:** the file was not valid XML before this change, because three comments
  contained `--`, which strict plist parsers reject. Those were fixed, and the file now parses
  (Python `plistlib`).
- Both are unverified until the first Xcode build, like the rest of the Swift code.
