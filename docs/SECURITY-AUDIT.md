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

**Known limits:**
- A signed link works for one hour for whoever holds it.
- An organizer who leaves the league screen open for more than an hour must refresh it before opening a proof.
- Co-organizers still can't see screenshots. That's an existing rule (`callerId == organizerUserId`),
  unchanged here.

## Finding 2: a removed franchise could still bid (Medium), next

`AuctionService.placeBid` doesn't check `removedAt`. A franchise that was removed, or whose leave
request was approved, can still bid through the API.

## Below the bar

- **Share-card fetch:** `LeagueSaveRequest.logoUrl` and `bannerUrl` have no validation, and the web
  viewer fetches `logoUrl` from its own server inside Railway's private network. The request is
  mostly blind, because only image responses are used. Hardening option: require `https://` on our
  bucket host.
