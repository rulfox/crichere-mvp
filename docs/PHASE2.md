# Phase 2 — League Dashboard

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions that apply here.

**Last updated:** 2026-09-04
**Status:** scoped via interactive discussion (research-grounded against CricHeroes/PlayBid/CricBid/CricAuction conventions) — deliberately narrow: discovery + creation only, no auction mechanics yet. Ready to move into implementation planning.

---

## 1. Overview

In plain terms: after logging in and setting up their cricket-player profile (Phase 1), a user lands on a dashboard listing cricket leagues that have been announced nearby or in their area. They can filter by state, by city, or find the ones closest to them using their phone's location. Any logged-in user can also create a new league themselves — give it a name, say where and roughly when it happens, and it shows up on everyone's dashboard.

This phase is deliberately narrow. It does **not** include the auction itself — no team budgets, no live bidding, no buying/selling players. Real cricket-auction platforms researched during scoping (CricHeroes, PlayBid, CricBid, CricAuction) split cleanly into "discover/announce a league" vs. "run its auction," and those are very different problems (the auction is real-time, multi-party, and has its own hard state machine). Phase 2 is the first half only: get leagues discoverable and creatable. The auction mechanic — team owners, player registration pools, budgets, bidding, sold/unsold — is explicitly deferred to a later phase.

---

## 2. Features

- View a dashboard listing announced leagues
- Filter the dashboard by state, district, city, or by "nearest to me" (device GPS)
- Create a new league: name, location (state/district/city), a ground (search/pick an existing one or register a new one with a map pin), start date, format, description, logo, banner, capacity targets (teams/players required), fees (franchise/player, display-only), and prize awards (First/Second/Third suggested at creation, more addable anytime)
- Search for and reuse an existing ground when creating a league, or register a new one
- View a single league's full details, including its full award list
- Add, edit, or remove awards on your own league at any time (not just at creation)
- Mark your own created league as completed once it's run its course

---

## 3. Screens

Describing what belongs on each screen — not layout, spacing, or component choice.

**League Dashboard** — reached via the new 2-tab bottom nav (Dashboard, My Profile); the app's new post-login landing screen for a complete profile.
- List of announced leagues (logo, name, location, start date, format shown per item).
- Filter controls: State, District (dependent on State), City (dependent on District) — see the District-tier retrofit in Decisions Made — plus a "Near me" option that sorts/filters leagues that have a ground attached, by distance from the device's current GPS location (mutually exclusive with the State/District/City filters — leagues without a ground don't participate in "nearest" but still show up under the area filters).
- A visible action to create a new league.
- Tapping a league opens League Detail.

**League Creation** — one continuous scrollable form with grouped sections (mirrors Profile Setup's actual shipped pattern), not a multi-screen wizard.
- *Basics*: Name field, description field (optional, free text), logo upload, banner upload.
- *Location*: State selector, District selector (dependent on state), City selector (dependent on district) — GPS auto-fill across all three, always hand-editable, same pattern as Phase 1's Profile Setup.
- *Ground*: search existing grounds (by name/area) and pick one, or register a new ground (name + a real interactive map view, drop/drag a pin — no map SDK exists yet in this app, this is genuinely new scope; Android needs a Google Maps API key not yet available, see Decisions Made). Optional — a league can be created without one and gain a ground later. No ground-edit feature in Phase 2 — a wrong ground is corrected by registering a new one.
- *Schedule*: Start date picker (single date, not a range).
- *Format*: free text (e.g. "Box cricket," "Tennis-ball," "T20" — no fixed list for Phase 2).
- *Capacity*: franchises-required (integer, e.g. 8 teams), players-required (integer, e.g. 15 per team) — exact numbers, not ranges.
- *Fees*: franchise fee (optional number, per team) and player fee (optional number, per player) — display-only, no payment processing.
- *Awards*: a repeatable list, pre-suggested with three starter rows ("First Prize," "Second Prize," "Third Prize" — editable/removable, not mandatory to fill). Each row: award name (free text, suggestions like "Man of the Match," "Best Bowler," "Best Batsman," "Emerging Player" offered but not a fixed list), cash amount (optional number), has-trophy (yes/no toggle). More rows addable here or later from League Detail.
- A save action. The league becomes visible on the dashboard automatically once every required field is filled — no separate "publish" step (see Decisions Made). Only Name, State, District, City, and Start Date are required to reach `ANNOUNCED`; ground, logo, banner, capacity, fees, and awards are all optional and addable later.

**League Detail**
- Everything entered at creation: logo, banner, name, description, location (state/district/city), ground (name + map, if set), start date, format, organizer.
- Capacity (franchises/players required), fees (if set), and the full awards list (name, cash amount, trophy) displayed together as a "Prizes" section.
- If the current user is the league's organizer: an "Edit" action (covers every field including adding/removing awards, and attaching/changing the ground) and a "Mark as completed" action. Both remain available even after the league is marked completed — nothing freezes.
- No join/registration action yet — that arrives with the auction phase.

---

## 4. Decisions Made

| Decision | Rationale |
|---|---|
| Phase 2 = discovery + creation only, no auction mechanics | Research across existing cricket-auction platforms shows discovery/metadata and the live auction are separable concerns with very different complexity profiles. Keeps this phase's scope matched to OVERVIEW.md's original Phase 2 description (dashboard + creation) rather than growing it into the auction, which deserves its own phase. |
| Team owner and player are the same underlying identity as Phase 1's `User`/`Profile` — no new role/entity in this phase | A profile can own a team in one league and separately register as a player in another (or, structurally, both in the same league) — nothing about "who can do what" needs a new user type. Whatever per-league participation record (owns team X / registered as player in league Y) is needed is deferred to the phase that actually builds registration/auction, since Phase 2 has no registration mechanic to attach it to yet. |
| **League lifecycle is 2 states in practice for Phase 2 — `ANNOUNCED` → `COMPLETED`, not 3** | Resolved during implementation (2026-09-04): League Creation is one continuous form with a single Save action, no resumable partial-save like Profile Setup's onboarding (confirmed explicitly) — so Name/State/District/City/Start-Date are `NOT NULL` at the database level, required in one sitting to create a league at all. That makes `DRAFT` genuinely unreachable, not just rare, so it isn't modeled — a `DRAFT` branch that can never execute is dead code. **Forward-compat note:** when the auction phase lands, the real-world lifecycle (`Draft → Registration Open → Auction Live → Ongoing → Completed`) may reintroduce a real pre-announcement state — revisit then, don't build for it now. |
| `COMPLETED` is a manual organizer action, not derived | Unlike `DRAFT`→`ANNOUNCED` (inferable from whether required fields are filled), "has this league run its course" isn't something the data can infer — only the organizer knows. |
| Every league is public — no private/invite-only leagues in Phase 2 | Matches a self-serve community-discovery platform; simplest for MVP. Private/invite-only is a real gap for later if the platform ever supports club-only or invite-based leagues, but not needed now. |
| Single "starts on" date, not a date range | Enough for dashboard display/sorting now. Real match/tournament scheduling isn't in this phase — a date range would be speculative modeling for a feature that doesn't exist yet. |
| Any logged-in user can create a league — no approval gate | Simplest, matches a self-serve community platform. Spam/abuse handling (e.g. rate limits, moderation) explicitly deferred, same posture as Phase 1 took toward abuse protection being handled at the auth layer, not the content layer. |
| Format is free text, not a fixed enum | Format isn't a dashboard filter (only State/City/nearest-GPS are, per OVERVIEW.md's original scope) — it's descriptive metadata only. A fixed enum (like Playing Role in Phase 1) is unwarranted complexity for a field nothing filters or validates against yet. |
| Country scoped to India, field exists but not shown in UI | Same pattern as Phase 1 — data model stays extensible without cluttering the UI with a one-option field. |
| Only Name, State, District, City, and Start Date are required to reach `ANNOUNCED`; ground, logo, banner, capacity, fees, and awards are all optional | Everything added in this round (ground, logo, banner, capacity, fees, awards) is real value but none of it should block a league from being discoverable — an organizer who just wants to say "this is happening, here, on this date" shouldn't be forced through a long form first. Matches the platform's general bias (Phase 1 also kept required fields to the minimum that makes the feature functional). |
| Logo and banner both built now, not logo-only | Reversed from the earlier "logo only, defer banner" lean once fees/awards were added to scope anyway — since League Creation is now a real multi-step flow regardless, the incremental cost of a second image-upload step is small, and League Detail benefits from both immediately (logo for identity, banner for the hero). |
| **Ground is a shared, reusable entity (`grounds` table), not fields embedded on the league row** | Organizers reuse the same physical ground across multiple leagues over time — modeling it as its own entity means the second league at "MRF Ground, Chennai" is a search-and-pick, not a re-pin. Also sets up ground-level history/stats for free later, at the cost of a search/pick-or-create step in the creation flow instead of a bare venue-name field. |
| A league's own State/District/City stay required and independent of Ground; Ground is optional and separately located | A league can be announced ("this is happening in Bengaluru, Karnataka") before its exact ground is confirmed. Making Ground required just to satisfy location filtering would force premature precision. When a ground is attached, its own state/district/city is expected to agree with the league's, but the two aren't hard-linked at the schema level for Phase 2. |
| **District added as a real third location tier (State → District → City), retrofitting Phase 1's profile too** | Location granularity is a platform-wide question, not a per-feature one — profile and league should stay consistent. This is real implementation work on already-shipped, merged Phase 1 code, not a Phase-2-only addition — see the retrofit note added to PHASE1.md Section 6. Timing (dedicated patch vs. bundled into Phase 2's own build) is still an open scheduling decision. |
| Franchises-required / players-required are exact integers | Simple two-field capacity metadata, useful for dashboard/marketing display now; becomes real input to the auction phase's team/pool sizing later without needing a schema change (just gets *used* for something, not redefined). |
| Fees (franchise, player) are optional display-only numbers, no payment processing | Organizers already collect entry fees offline (cash/UPI) — showing the amount is real information value. Actually handling payment is a genuine business-model decision (who processes it, who's liable, what happens on a no-show) nobody has made yet, so it stays out of scope rather than being implied by a form field. |
| Awards modeled as one generic repeatable list (name, cash amount, trophy flag), not hardcoded First/Second/Third columns | The ask itself ("Man of the Match etc. addable after creation") only holds together if First/Second/Third aren't special-cased — they're just the first three rows of the same list, pre-suggested at creation to avoid a blank canvas but not structurally different from any award added later. One table, one edit surface, no special-case code path for "the big three" vs "everything else." |
| Trophy is a plain yes/no flag per award, not a description field | Simplest thing that answers "does this award include a trophy" — most local leagues don't need to name/describe the trophy itself. Revisit only if real organizer feedback asks for it. |
| **League Dashboard becomes the app's new post-login landing screen**, not Own Profile View | Decided during Phase 2's gap analysis (2026-09-04). PHASE1.md's own "Terminal screen" decision said Own Profile View was only the landing screen "since there's no dashboard yet (that's Phase 2)" — now that Phase 2 exists, app-start routing changes: incomplete profile → Profile Setup (unchanged), complete profile → League Dashboard (new), Own Profile View reachable via a nav tab/menu instead of being the first screen. Superseding note added to PHASE1.md Section 4. |
| **Reversed:** a `COMPLETED` league is NOT frozen — everything stays editable, awards included | Superseded same-day: freezing would block a real, common workflow — awards like Man of the Match/tournament MVP are often only decided *after* the league completes, so a freeze-on-complete rule would prevent adding the very award that depends on completion. No lock logic at all in Phase 2; revisit only if unwanted after-the-fact edits become a real observed problem. |
| **No ground-edit feature in Phase 2 at all** — not "registrant-only edit" as first decided | Corrected same-day: Section 3's Screens never actually described a "edit a ground's own details" screen (only "attach/change which ground a league points to"), so the earlier registrant-only-edit decision had no feature to attach to. A mis-registered ground gets a fresh duplicate instead — same accepted soft cost as no de-duplication. |
| **Nearest-GPS only ranks leagues with a ground attached — no city/district centroid fallback** | The centroid fallback would need real latitude/longitude sourced for ~90+ cities and now districts — a real data-acquisition task, not a code change, and nothing like it exists today. Leagues without a ground still show up under State/District/City filters, just don't participate in "nearest" sort. Given the map-pin picker is being built anyway, most location-conscious organizers will have one. |
| **Ground location capture is a full interactive map-pin picker** (drag-to-place, not just a "use my current location" button) | Chosen over the simpler GPS-only alternative despite it being genuinely new scope for this app (no existing map SDK/UI anywhere) — lets an organizer pin a ground without needing to be physically there. Android needs a Google Maps API key that doesn't exist in this environment yet; built authored-but-unverified, same posture Phase 1 used for missing Firebase/AWS credentials. iOS's MapKit needs no key. |
| League Creation is one continuous scrollable form with grouped sections, not a multi-screen wizard | Mirrors Profile Setup's actual proven pattern exactly (which, despite PHASE1.md's "may be one screen or a short sequence" wording, shipped as one scrollable form, not separate step screens) — no new Next/Back navigation state machine needed. |
| **New 2-tab bottom navigation (Dashboard, My Profile) replaces Own Profile View as the landing screen** | Standard minimal pattern for exactly two top-level destinations, once Dashboard became the landing screen (see the row above). Own Profile View's own screens/callbacks are unchanged — only what hosts them changes. |
| No validation linking a league's own State/District/City to its attached Ground's location | Confirms the earlier "not hard-linked" data-model decision explicitly as a real behavior, not a silent gap: a league can (unusually) reference a ground in a different city than its declared location. Accepted as an edge case nobody is expected to hit in practice, not worth validating against. |
| **`maps-compose` pinned to 6.4.1, not the latest 8.4.0** | Discovered on the first full `androidApp` compile after League Creation's map picker landed (2026-09-05): 8.4.0's transitive `androidx.core:core-ktx:1.19.0` requires `compileSdk 37` + AGP 9.1.0, and this project is on `compileSdk 36` / AGP 8.13.2 (see `libs.versions.toml`'s own `agp` comment on why that pairing was chosen). 6.4.1 exposes the identical `GoogleMap`/`Marker`/`rememberCameraPositionState` API this app uses, so no functional change, no AGP/compileSdk bump needed. Revisit alongside any future AGP upgrade. |

---

## 5. Open Questions and Gaps

All forks surfaced during scoping and implementation planning (2026-09-04) were resolved by explicit decision — see Decisions Made. Remaining, genuinely still-open items:

- **Ground de-duplication**: search-and-pick relies on the organizer recognizing an existing ground by name — no strict duplicate prevention (e.g. two grounds registered a few meters apart under slightly different names) is planned for Phase 2. Acceptable soft cost at MVP scale; revisit only if it becomes a real nuisance.
- **What "announced" means for filtering** is resolved for Phase 2 (derived: Name/State/District/City/Start Date all present) but will need revisiting once the auction phase introduces `Registration Open` as a distinct, later state.
- Whether the dashboard needs pagination/search beyond the filters (State/District/City/nearest) — not addressed yet, likely fine to defer until league volume is large enough to matter.
- League delete/archive (beyond "mark completed") isn't decided — not needed for Phase 2's Screens as written, revisit if it comes up.
- Who can register a new Ground — any logged-in user (same posture as league creation), or only while creating/editing a league (no standalone "add a ground" entry point)? Leaning toward the latter for Phase 2 (grounds are created as a byproduct of league creation, not their own directory feature yet), not yet confirmed.
- Google Maps API key provisioning (Android map picker) — `GroundMapPicker` (the draggable-marker `GoogleMap` composable) is built and wired into League Creation's Ground section as of 2026-09-05, but genuinely blocks real on-device verification (does the pin actually render/drag correctly) until someone provisions a key; everything else is unaffected.
- **iOS Phase 2 screens deliberately skipped for now (explicit user decision, 2026-09-05)**: discovered mid-Phase-2 that iOS never got Phase 1's own auth nav shell built either — `ContentView.swift` is still the pre-Phase-1 toolchain-proof screen, `PhoneEntryView`/`OtpVerifyView`/a root nav switcher don't exist, and `ProfileSetupView`/`OwnProfileView` are authored but never wired to anything. Building League Dashboard/Creation/Detail SwiftUI screens on top of that gap wouldn't be usable. Android Phase 2 continues to completion; iOS (Phase 1's nav shell and Phase 2's screens together) is deferred to its own dedicated pass.

---

## 6. Pure Technical Things

### Data Model (draft, pending implementation planning)

- `leagues`: id, organizer_user_id (FK to `users`), name (NOT NULL), description (free text, nullable), logo_url (nullable), banner_url (nullable), country (default `IN`, not user-facing), state (NOT NULL), district (NOT NULL), city (NOT NULL), ground_id (FK to `grounds`, nullable), starts_on (date, NOT NULL), format (free text, nullable), franchises_required (int, nullable), players_required (int, nullable), franchise_fee (numeric, nullable), player_fee (numeric, nullable), completed_at (timestamptz, nullable — the only status signal; status is `ANNOUNCED` when null, `COMPLETED` when set, per the 2-state resolution above), created_at, updated_at.
- `grounds`: id, name, country (default `IN`), state, district, city, latitude, longitude, registered_by_user_id (FK to `users`), created_at. Shared across leagues — many `leagues` rows can point at the same `ground_id`. Not hard-linked to its leagues' own state/district/city (see Decisions Made). No update/delete — Phase 2 has no ground-edit feature.
- `league_awards`: id, league_id (FK), name (free text — "First Prize" etc. are just conventional values, not an enum), cash_amount (numeric, nullable), has_trophy (boolean, default false), display_order (int, so First/Second/Third render before later-added awards). One league has many awards, editable regardless of the league's status (no completion freeze); the creation flow pre-populates three suggested rows client-side (or via the create call), nothing schema-special about them.
- Location reference data becomes a 3-level chain once the District retrofit lands (see PHASE1.md Section 6): `states` → `districts` → `cities`, replacing Phase 1's current direct `states`→`cities` link. Both profile and league location pickers consume the same reference endpoints.
- Logo/banner uploads reuse Phase 1's S3 presigned-POST pattern (`PhotoUploadService`) — same mechanism, different object-key prefix (e.g. `leagues/{leagueId}/logo.jpg`, `leagues/{leagueId}/banner.jpg`), no new upload infrastructure needed.
- Nearest-GPS: simple Haversine-distance calculation against the league's `ground.latitude`/`ground.longitude`, only for leagues with `ground_id` set — no centroid fallback (see Decisions Made), no PostGIS or dedicated geo-index needed at this scale (a local/regional platform, not a global one).

### API surface (draft)

- `GET /api/v1/leagues` — list, with optional `state`, `district`, `city`, or `near` (lat/long) query params for the filters.
- `POST /api/v1/leagues` — create.
- `GET /api/v1/leagues/{id}` — detail (includes its awards list and ground, if set).
- `PUT /api/v1/leagues/{id}` — organizer-only, edit (full-replace, likely following Phase 1's `PUT /profiles/me` precedent rather than partial-merge, for the same validation-simplicity reason).
- `PATCH /api/v1/leagues/{id}/complete` — organizer-only, sets `completed_at`. Does not lock the league — `PUT`/awards endpoints remain usable after.
- `POST /api/v1/leagues/{id}/logo-upload-url`, `POST /api/v1/leagues/{id}/banner-upload-url` — organizer-only, presigned POST (mirrors `PhotoUploadService`).
- `POST /api/v1/leagues/{id}/awards`, `PUT /api/v1/leagues/{id}/awards/{awardId}`, `DELETE /api/v1/leagues/{id}/awards/{awardId}` — organizer-only, manage the awards list independent of the league's own edit call.
- `GET /api/v1/grounds?search=...&state=...&district=...&city=...` — search existing grounds for the Ground picker. `POST /api/v1/grounds` — register a new one (name, state/district/city, lat/long).
- Auth: creation/edit/complete/awards/uploads/ground-registration require an authenticated session (reuses Phase 1's JWT bearer auth); dashboard/detail/ground-search reads are likely public given leagues are public by decision above — worth confirming during implementation planning whether reads still require auth for consistency with the rest of the API's posture.

### Security Considerations (analysis pass, 2026-09-04)

- **Authorization is Phase 2's biggest new surface.** Every mutation in Phase 1 was implicitly "your own profile" — no explicit ownership check was ever needed. Phase 2 is the first place a resource (league, its awards, its logo/banner) has an owner distinct from "whoever is calling." Every mutating endpoint (`PUT`/`PATCH /leagues/{id}`, awards CRUD, logo/banner presign) must explicitly verify `organizer_user_id == current user` — this has to be deliberately built and tested per-endpoint, it doesn't fall out for free the way `/profiles/me` did. Grounds have no mutating endpoint beyond creation (no edit feature), so `registered_by_user_id` is stored for provenance only, not enforced against anything yet.
- **Logo/banner presign needs the ownership check, not just key-namespacing.** Phase 1's `PhotoUploadService` could namespace by `userId` alone because the caller *was* the subject. Here the caller and the league are different things — the presign endpoint must verify the caller is the league's organizer before issuing a URL scoped to `leagues/{leagueId}/...`, otherwise any authenticated user could overwrite any league's logo.
- **New creation endpoints need their own rate limits.** Phase 1's Bucket4j limiter is auth-endpoint-specific. League/ground/award creation are new unrestricted-volume endpoints — need a per-user creation rate limit or the public dashboard fills with junk.
- **Public unauthenticated reads** (list/detail/ground-search) are fine per the public-leagues decision, but should still get a light IP-based throttle (same Bucket4j mechanism) so they're not a free scraping/DoS surface.
- Free-text fields (description, format, ground name) carry no injection risk as long as they only ever render as plain text (Compose `Text()`/SwiftUI `Text`), never HTML/Markdown — worth confirming this stays true during implementation, not a reason to add sanitization now.
- Ground GPS pins are public-venue coordinates (a cricket ground, not a person), so no privacy concern beyond what's already true of a public address.
- No payment processing (confirmed decision) means no financial-data handling surface to secure in this phase.
