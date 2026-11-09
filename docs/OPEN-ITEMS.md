# Open items

**Last updated:** 2026-11-07 (Phase 18 live on production since merge `df59304`, 2026-11-06 23:20 UTC; mobile release not shipped; Phase 19 on branch `android-release`)

One list of everything still open, gathered from DESIGN-REVIEW Follow-ups, the phase docs and the
design update #4 work. Details live in the linked docs; this file is the index. Design questions are
were answered by update #5 (PHASE15 8).

## 1. Decisions needed from the owner

| Item | Why it matters | Where |
|---|---|---|
| Privacy and Terms text | Drafts in `docs/legal/` (copies in Downloads), now also on /privacy and /terms (2026-10-11, still `noindex`, not in the footer). Owner details filled 2026-11-09 (Aswin A S, Alappuzha, Kerala 688538; grievance officer the owner; courts at Alappuzha, Kerala; draft retention periods and Rs. 1,000 cap kept; effective 9 November 2026). Left: lawyer review, then flip `LEGAL_PAGES_LINKED`. Pages must exist before launch (store listings). Decided 2026-10-04: template with placeholder text at /privacy and /terms, `noindex`, not linked until the real text arrives. | PHASE15 8, DESIGN-REVIEW web |
| Old bucket `crichere-media-dev` still serves a payment screenshot publicly | Prod bucket fixed 2026-10-05. Owner approved deleting it (2026-10-05); `crichere-claude` lacks delete rights, so delete it in the S3 console (empty, then delete). Also remove the temporary `crichere-media-move-temp` IAM policy. | SECURITY-AUDIT 1 |
| S3 permissions for the media sweep | Done 2026-11-07: `s3:DeleteObject` added on `crichere-media-prod/users/*` and `leagues/*`; list and delete tested with non-existent keys. The sweep and purge themselves have not run yet: check the logs after 04:00 IST on 2026-11-08. Read-only check 2026-11-07: all 6 stored image URLs match an S3 key, so they stay. The first run should delete 7 orphans (a deleted league's logo, banner and 2 franchise logos; 3 profile photos of 2 deleted users and 1 user with no saved photo). Payment proofs are never candidates. **The backend IAM user is `crichere-claude`, the same key as the local CLI.** Split them: a dedicated backend user, then reduce `crichere-claude` and drop its migration permissions. | SECURITY-AUDIT 6 |
| Account deletion: iOS check, Play Console | S3 grant done 2026-11-07 (row above). Backend, `/delete-account` and Android done (PHASE16); iOS written, never compiled. Play Console: Data safety deletion URL = https://crichere.com/delete-account. | PHASE16 5, 7 |
| Image URLs saved before 2026-10-11 | The backend now only accepts our-bucket image URLs (SECURITY-AUDIT Finding 4), but existing rows aren't rechecked. Run once on production: `SELECT 'profile', user_id::text, photo_url FROM profiles WHERE photo_url NOT LIKE 'https://crichere-media-prod.s3.%' UNION ALL SELECT 'league', id::text, logo_url FROM leagues WHERE logo_url NOT LIKE 'https://crichere-media-prod.s3.%' UNION ALL SELECT 'banner', id::text, banner_url FROM leagues WHERE banner_url NOT LIKE 'https://crichere-media-prod.s3.%' UNION ALL SELECT 'franchise', id::text, logo_url FROM league_franchises WHERE logo_url NOT LIKE 'https://crichere-media-prod.s3.%'`, then null out anything found. | SECURITY-AUDIT 4 |
| Official store badge artwork + store URLs | Badges use mock glyphs and show "Coming soon" until `NEXT_PUBLIC_PLAY_STORE_URL` / `NEXT_PUBLIC_APP_STORE_URL` are set. | DESIGN-REVIEW web |
| Report and block (Google Play User Generated Content policy) | Live on production since 2026-11-06 (docs/PHASE18.md): backend, shared, Android and web tested; iOS written, never compiled. Backend and web deployed; mobile release not shipped. Block organizer link added 2026-11-06 for organizers with no roster row. | PHASE18 |
| Terms acceptance and auction money note | Backend live since 2026-11-06, before the mobile release, so installed older apps can't create accounts until it ships. Existing accounts are unaffected. | PHASE18 |
| MSG91 before launch | Check the production widget: keep one demo number for the Play reviewer (non-obvious OTP if possible, plain sample account), remove the others. Rotate the Auth Key and widget token. Test real SMS on Jio, Airtel and Vi. | PHASE12 6, PHASE18 |
| Android release: Data safety and other App content forms, store listing | App created in Play Console 2026-11-10 (`com.crichere.app`, en-IN, free); 1.0.0 (1) uploaded to internal testing. Play signing SHA-1 on the Maps key, SHA-256 in `assetlinks.json`. | PHASE19 |
| Play release path | Personal account (after Nov 2023): internal test, then a closed test with 12+ testers opted in for 14 days in a row, then apply for production. Form answers and listing draft ready. | legal/PLAY-CONSOLE |
| Play reviewer account | Finish the demo account's profile and add it to a sample league with players, franchises and a completed auction; put the league's name in the review notes. Confirm hello@crichere.com receives mail. | legal/PLAY-REVIEW-NOTES |
| Store listing assets | 512 px icon, 1024 x 500 feature graphic, 2 to 8 phone screenshots from the release build. | legal/PLAY-CONSOLE 3 |

## 2a. Decided (2026-11-04)

- Blocking hides the blocked user's photo and uploads from the blocker and drops leagues they organize from the
  blocker's dashboard. Their name stays in shared rosters and auctions. It does not stop anyone joining a league.
- Reports go to the Crichere team only (stored in the backend and checked daily; email alerts via Amazon SES are future scope, decided 2026-11-04); organizers don't see
  them. The team can remove an image or suspend an account.

## 2. Decided (2026-10-04)

- Contact: `NEXT_PUBLIC_CONTACT_EMAIL=hello@crichere.com`; the Contact link shows only when it is set.
- iOS direction: native iOS (update #5 C). This round: fonts + theme, live auction and league detail restyle; other
  screens later.
- Update #5 L23 (owner dock between lots) dropped.

Update #5 is built on every platform (PHASE15 8); iOS is unverified until a first Xcode build. Set `NEXT_PUBLIC_CONTACT_EMAIL=hello@crichere.com` on the web deploy.

## 3. Bugs and engineering risks

| Item | Notes | Where |
|---|---|---|
| `JwtServiceTest` "accepted one second before it expires" was reported flaky | Could not reproduce on 2026-11-06: 8 forced runs passed, and the test already uses a fixed clock, so no change made. Cause unknown; reopen if it fails again. | backend tests |
| iOS test target doesn't compile | `:shared:compileTestKotlinIosSimulatorArm64` rejects 53 test names in `commonTest` that contain a comma (Kotlin/Native rule). Android tests and the iOS app are unaffected. Rename them before running iOS tests. | shared tests |
| Swift code never compiled | Every iOS file, including the update #4 port and PHASE17's image upload shrink + cache, needs a first Xcode build (expect fixes). | PHASE15 7.5, PHASE17 7, iosApp/README |
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
- **Location data (LGD, V20/V21):** not seen in the app: long district pickers (UP has 75; form pickers have
  no search), GPS auto-fill matching against LGD district names (geocoder spellings differ). See DESIGN-REVIEW
  Follow-ups for the update #6 list.

## 5. Future scope (not scheduled)

- Country-code picker (phone entry, co-organizer lookup, backend normaliser).
- Scripted assertions for the local e2e harness (`backend/e2e`), so it becomes a regression suite.
- Places API (New) search for the ground map. API enabled on project `crichere` and added to the `Maps SDK Android (debug)` key's API restrictions (2026-10-11); `PLACE_SEARCH_PROVIDER=places` set in `mobile/local.properties` and verified on the Pixel_9_Pro AVD 2026-10-11 (autocomplete "Nehru Stadium Kottayam" -> pick -> map flew there, pin at 9.5960, 76.5260). Release builds must set the same property; billing is per session (PHASE2 decisions).
- iOS Universal Links.
- CloudFront in front of `crichere-media-prod`, with on-request resizing (e.g. AWS Serverless Image Handler) so list
  rows fetch ~128px instead of the 1024px upload (PHASE17 3). Deferred 2026-10-11: images are cached forever on
  devices; it needs AWS setup and a rewrite of stored URLs.

## 6. Location data (LGD) — decided 2026-10-06

States, districts and cities come from the Local Government Directory (lgdirectory.gov.in, Govt of India), pulled
2026-10-06 via its public `lgdws` web services; migration `V20__refresh_locations_from_lgd.sql`, tooling and rerun
steps in `backend/tools/lgd/`. Owner decisions: source = LGD (national), not Kerala LSG; city = ULB with
sub-district fill; old rows remapped where the rename is known.

- **States:** our 36 V4 codes and names kept (stored rows hold the name; ours already match LGD). `lgd_code` added.
- **Districts:** all 784 LGD districts (was ~95). LGD's Odia transliterations replaced by English names (Kataka →
  Cuttack, Sundaragada → Sundargarh, …); `Ntr` → NTR, `Leh Ladakh` → Leh.
- **Cities (5,280, dropped by V21 the same day):** urban local bodies mapped to the district *or one of its sub-districts* (district-level
  mapping alone misses e.g. Guntur, Vijayawada), names stripped of type suffixes. Districts with no ULB get their
  sub-districts. Karnataka = ULBs + taluks (LGD maps few Karnataka ULBs: Kolar, Mandya, Udupi had no own town).
  Delhi = sub-districts (LGD still has the three pre-2022 corporations). A district missing its HQ town gets the
  same-named sub-district. Manual: Noida, Greater Noida, Amaravati, Vasco da Gama, Bokaro Steel City; Bengaluru's 5
  GBA corporations → Bengaluru; Hubballi-Dharwad → Hubballi + Dharwad. `cities.source` records ULB / SUBDISTRICT /
  MANUAL.
- **Remap:** profiles/leagues/grounds store text, so the V20 UPDATEs move every V4/V5-seeded pair that no longer
  exists (e.g. Warangal Urban → Hanumakonda, Krishna/Vijayawada → NTR/Vijayawada, Mumbai City → Mumbai). Free text
  outside the old seed is left as-is.
- **Known limits:** LGD spellings kept elsewhere (Cuddapah, Port Blair not Sri Vijaya Puram); some sub-district
  fills are admin names (Arunachal circles). A refresh is a new migration; `REMAP` must be rebuilt then.

### 6.1 Decided 2026-10-06 (owner), built 2026-10-06 (design update #6)

- **Drop City everywhere** (profile, league, ground, filters, display, `cities` table and endpoint). App is
  unreleased, so columns are dropped, not kept nullable.
- **Ground mandatory** on every league (`ground_id NOT NULL`, FK no longer `SET NULL`); "Nearest to me" then covers
  every active league.
- **League keeps its own State/District (option B)**, independent of the ground's.
- **Profile back button:** none on first-time profile setup (new registration); shown on Edit profile.
- Built: backend V21 (drops `cities` + city columns, deletes ground-less leagues, ground NOT NULL / RESTRICT),
  shared, Android, web, iOS. Record and unverified list: DESIGN-REVIEW "Design update #6" + Follow-ups.

