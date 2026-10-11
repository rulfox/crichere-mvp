# Phase 17: Player photos and franchise logos in lists

**Status:** backend, shared, Android and web done and tested; iOS written, not compiled (no Mac).
**Date:** 2026-10-11

## 1. Why

Every list of players or franchises already has an avatar circle or logo tile in its Claude Design export, but
most of them showed initials only. A profile photo is required to complete a profile
(`ProfileCompletionService`), so almost every player has one; the list responses just didn't send it.
This phase fills the existing slots. No layout changes, so no new Claude Design pass.

## 2. API (no renames, additive only)

| Response | New field | Notes |
|---|---|---|
| `LeaguePlayerResponse` (league `players`) | `photoUrl` | From the profile the mapper already loads for `name`. |
| `LeagueRoleResponse` (league `coOrganizers`) | `photoUrl` | Same. |

- A deleted user's `photoUrl` is always `null`. The purge already clears it; the mappers also check
  `accountDeleted`, and `PlayerAuctionResultResponse.photoUrl` got the same check.
- **Considered and dropped:**
  - `franchiseLogoUrl` on auction results;
  - `franchiseId` on `lastResult`.

  Every client already has the league's `franchises` (with `logoUrl`) when it shows results or the live auction,
  so it looks the logo up by franchise id. The last-result line is text only. The live stream stays small.

## 3. Image decisions (owner, 2026-10-11)

- **Upload size 1024px** for profile photos, league logos and franchise logos. The largest place a photo shows
  today is the auction "current player" tile, about 330px, so 1024px leaves headroom for a bigger design later.
  - Android: `PhotoCropSheet.OutputSize` went from 512 to 1024. A crop smaller than that is never upscaled.
  - Banners stay at 1280x720.
- **iOS shrinks before upload.** iOS used to upload the original picker file, often a multi-MB HEIC labelled
  `image/jpeg`. Chrome can't show HEIC, and the file carries EXIF GPS, which leaks where the photo was taken
  through a public URL. Now `downscaledJpeg` (`iosApp/ImageUpload.swift`) re-encodes to JPEG at 1024px, or
  1280px for banners. This strips the metadata and applies the orientation.
  - Payment screenshots are unchanged: they're private, and the organizer needs them readable.
- **Originals are not kept.** No screen needs them, and they carry the location data.
  - Keeping an original plus a list thumbnail is planned with the CDN (OPEN-ITEMS, future scope).
- **CloudFront is deferred** (OPEN-ITEMS).

## 4. Loading and caching

- **Already in place:**
  - uploads are `Cache-Control: public, max-age=31536000, immutable`;
  - each stored URL gets a fresh `?v=` (`PhotoUpload.kt`), so caches never serve a stale image.
- **Android:** Coil 3's default memory and disk cache. It decodes at the composable's size, so a 32dp row keeps a
  small bitmap even from a 1024px file. Each slot draws the initials underneath and the image on top, so the
  initials show while loading, on failure, or when there is no URL.
- **iOS:**
  - `configureImageCache()` at app start raises `URLCache.shared` (which `AsyncImage` uses) to 32 MB memory and
    200 MB disk;
  - `RemoteImage` draws over the initials, the same as Android.
- **Web:** `FranchiseBadge` takes `logoUrl` and draws an `<img loading="lazy" decoding="async">` with a fixed size
  over the initials. A broken image hides itself.

## 5. Where the images now show

- **Android:**
  - League detail players (photo);
  - Manage co-organizers (photo);
  - League detail franchises (logo; this already worked);
  - auction screens (already worked).
- **iOS:**
  - League detail players (photo) and franchises (logo);
  - auction leader, results and "bidding as" badges (logo).
  - iOS auction screens have no player-photo slot, so none was added.
- **Web live auction:** franchise badges in the not-started list, leader, ticker, standings and results (logo).
  - Squad rows have no avatar slot in the design, so none was added.

## 6. Tests

- **Backend: 508/508.**
  - `AccountDeletionFlowIntegrationTest`: a roster row carries `photoUrl`, and it is `null` after the purge.
  - Also fixed a flaky `JwtServiceTest` case: "tampered signature" flipped the last base64 character, which can
    be padding bits only (about 1 run in 16). It now flips the first signature character.
- **Shared:** 336 / 0.
- **Android:** `assembleDebug` and the androidTest compile.
- **Web:** vitest 75/75 (new: logos show on the badges, lazy), lint and `tsc` clean.
- **Emulator** (Pixel_9_Pro, local backend, E2E_Premier_League; images served from the host because the
  emulator's DNS was down):
  - player photos and the franchise logo show;
  - a broken logo URL falls back to initials;
  - after a cold start with the image server stopped, every photo still shows (Coil disk cache);
  - auction results show the photos.

## 7. Not verified

- Manage co-organizers photo on a device (needs that league's organizer signed in).
- Web in a browser with real logos. Vitest covers the markup only.
- iOS entirely: `downscaledJpeg`, the cache, `RemoteImage`.
- A real S3 upload at 1024px (local has no bucket). Byte sizes in prod are unknown; test images at 1024px were
  45–276 KB.
