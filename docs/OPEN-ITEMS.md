# Open items

**Last updated:** 2026-10-04 (after design update #4 shipped to Android, web and iOS)

One list of everything still open, gathered from DESIGN-REVIEW Follow-ups, the phase docs and the
design update #4 work. Details live in the linked docs; this file is the index. Design questions are
in the update #5 prompt (`crichere-design-update-5-prompt.md`, given to Claude Design).

## 1. Decisions needed from the owner

| Item | Why it matters | Where |
|---|---|---|
| Is `hello@crichere.app` a real inbox? | H4: hide the landing contact line if not. | web `app/page.tsx` footer |
| Privacy and Terms text | Pages must exist before launch (store listings); links are `#`. Layout requested in update #5 (B3). | PHASE11, DESIGN-REVIEW web |
| Official store badge artwork + store URLs | Badges use mock glyphs and show "Coming soon" until `NEXT_PUBLIC_PLAY_STORE_URL` / `NEXT_PUBLIC_APP_STORE_URL` are set. | DESIGN-REVIEW web |
| iOS direction (mirror Android vs native iOS) | iOS screens are unstyled forms. Asked in update #5 (C). | PHASE15 7.5 |

## 2. Design questions (sent in the update #5 prompt)

- End Auction has no confirmation and can't be undone (A1).
- Mark completed while the auction is live shows a "check your connection" message (A2).
- Date dialog headline with nothing picked; time-zone helper shows "GMT+05:30" outside English (India); M3 time-picker
  digits (A3–A5).
- Confirm: whole pill as Retry target, compact ground bar switching on keyboard, iOS progress on the row (A6).
- Web: "Purse left ₹-250" in standings and completed squads (B1); landing rhythm after the stats strip went (B2);
  Privacy/Terms template (B3); footer variants (B4).

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
