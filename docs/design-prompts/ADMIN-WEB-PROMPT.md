# Claude Design prompt: Crichere admin web (Phase 20)

Project: "Crichere - V2" (uses its DESIGN.md: Daylight tokens, Archivo / Inter / IBM Plex Mono, hairline-first).
Boards go in `P15 Admin ...` files. Plan and data model: `docs/PHASE20.md`.

---

Design the private admin site for Crichere at admin.crichere.com. One user, the owner, on a laptop and on a phone.
It answers three questions every day: how is Crichere doing, is anything live right now, and does anything need me.

Use the Crichere design system exactly (DESIGN.md). Daylight by default, with a dark theme drawn from Floodlight
tokens. Amber stays within its six uses: here only the live connection indicator, live-auction badge and current bid.

Chart rules (from the dataviz method, validated 2026-11-21):
- Categorical, max 3 series: light `#12936A`, `#2A78D6`, `#C2410C`; dark `#199E70`, `#3987E5`, `#D95926`
  (all checks pass, all pairs, light on `#FFFFFF`, dark on `#10161C`).
- Sequential (amounts, heatmap): brand ramp `#E7F3EE` `#C6E3D8` `#5FB294` `#2E9070` `#0B6E4F` `#084C38`.
- Status: DESIGN.md success, warn, danger, info, always with an icon and a word.
- One y-axis per chart. 2px lines, 4px rounded bar ends on the baseline, 2px gaps between bars and segments.
  Legend for 2+ series plus direct labels. Values and labels in text colours, never in series colours.
- Every chart: hover readout (crosshair on lines), a table view, and "refetch keeps the frame" (old chart stays,
  faded, while new data loads). No pies or donuts. Small counts show "+3", never "+300%".
- Numbers in IBM Plex Mono, tabular, Indian grouping (1,28,400).

Screens (desktop 1440 and phone 390, light and dark):
1. Sign in: Google button, one line on who it's for, error when the account isn't allowed.
2. Overview: filter row (date presets, compare, state and district, exclude internal), live status, KPI row (users,
   active users with stickiness, active leagues, auctions completed, open reports with oldest age), Live now (running
   auctions, activity feed), Growth (new users, sign-up funnel, SMS against daily cap), Engagement (daily, weekly,
   monthly active, retention heatmap), Leagues and auctions (status split, auctions per week, districts ranked),
   Trust and safety (opened vs closed, open by reason, time to close vs 24 h target), System health.
3. Reports queue and report detail with evidence and actions (dismiss, resolve, remove image, suspend), confirm
   dialogs with a required note; suspended accounts; audit log.
4. League list (paged, filters, keyset "Next"), league detail with tabs (Overview, Franchises, Players, Auction,
   Awards, Moderation, Timeline); payment screenshot viewer that says the view is logged.
5. User list (paged, filters, exact phone lookup that never shows the number), user detail with tabs (Profile,
   Leagues, Auction, Activity calendar, Moderation, Account); internal-account toggle.
6. Global search, sign-out everywhere, session expired, empty, filtered-empty, stale live data, error states.

Copy: plain, specific, sentence case, US spelling ("organizer"), no exclamation marks.
