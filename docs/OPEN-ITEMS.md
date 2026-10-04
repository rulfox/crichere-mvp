# Open items

**Last updated:** 2026-10-04 (design update #5 in progress: backend, shared and Android done)

One list of everything still open, gathered from DESIGN-REVIEW Follow-ups, the phase docs and the
design update #4 work. Details live in the linked docs; this file is the index. Design questions are
were answered by update #5 (PHASE15 8).

## 1. Decisions needed from the owner

| Item | Why it matters | Where |
|---|---|---|
| Privacy and Terms text | Pages must exist before launch (store listings). Decided 2026-10-04: template with placeholder text at /privacy and /terms, `noindex`, not linked until the real text arrives. | PHASE15 8, DESIGN-REVIEW web |
| Official store badge artwork + store URLs | Badges use mock glyphs and show "Coming soon" until `NEXT_PUBLIC_PLAY_STORE_URL` / `NEXT_PUBLIC_APP_STORE_URL` are set. | DESIGN-REVIEW web |

## 2. Decided (2026-10-04)

- Contact: `NEXT_PUBLIC_CONTACT_EMAIL=hello@crichere.com`; the Contact link shows only when it is set.
- iOS direction: native iOS (update #5 C). This round: fonts + theme, live auction and league detail restyle; other
  screens later.
- Update #5 L23 (owner dock between lots) dropped.

Update #5 remaining: web (W5–W9, legal template, footer) and iOS. Android and shared are done (PHASE15 8).

## 3. Bugs and engineering risks

| Item | Notes | Where |
|---|---|---|
| Uploads go live before Save | Fixed S3 key per owner; a cancelled edit still replaces the live image. Fix: fresh key, point the record at it on save. | DESIGN-REVIEW |
| Stale refresh token → 401 after reinstall | Seen once (2026-10-01); cause unconfirmed. Consider a short reuse grace window. | DESIGN-REVIEW |
| Orphaned test uploads in S3 | Delete, or clean up never-referenced uploads. | DESIGN-REVIEW |
| `JwtServiceTest` "accepted one second before it expires" is flaky | Passes alone; sits on a one-second boundary. Make the test use a fixed clock. | backend tests |
| Swift code never compiled | Every iOS file, including the update #4 port, needs a first Xcode build (expect fixes). | PHASE15 7.5, iosApp/README |
| Backend deploy carries new auction fields + own-lead rule + heartbeat | Watch the first live auction on Railway after the pushes of 2026-10-04. | PHASE15 6, 7.1 |

## 4. Not yet verified

- **Live auction (Android):** outbid flash, purse-can't-cover tile, over-purse bidding line, amount field focused /
  error / 9-digit, Retry on the pill, "Completing…" / "Removing…", Undo after clearing the auction time, viewer fades
  (no S3 locally), ground map with a real full-height keyboard. Everything else from update #4 was checked on the
  Pixel_9_Pro emulator against the local backend.
- **Physical phone (CPH2487) and the deployed backend:** none of update #4 has been seen there yet.
- **Web:** update #4 checked in Chromium against the mock server only; not against a real auction, not in Safari/iOS.
- **Older gaps:** share-card live checks, 10-digit phone input on device, MSG91 against the real service, C1 new-user
  profile, G/H/I scenarios on device, auction settings J5 failure live, co-organizer flows on the real backend, My
  leagues empty/error, My profile photo change, Navigation 3 on device. See DESIGN-REVIEW Follow-ups.

## 5. Future scope (not scheduled)

- Country-code picker (phone entry, co-organizer lookup, backend normaliser).
- Scripted assertions for the local e2e harness (`backend/e2e`), so it becomes a regression suite.
- Places API (New) search for the ground map.
- iOS Universal Links.
