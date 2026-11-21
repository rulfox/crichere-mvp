# Admin web design source (Phase 20)

A copy of the approved admin boards from Claude Design (project "Crichere - V2", files P15 and P16), saved on
2026-11-21. The Claude Design connection stops working by 2026-12-14, so the build reads styles from these files.

- `admin-kit.js`: tokens, components, charts and frames shared by every board.
- `p16-*.js`: one file per section (O overview, S sign-in and account, M moderation, L leagues, U users, X states).
- `P16 Admin Screens.dc.html`: the approved board file; its `<style>` block holds layout fixes made during review.
- `P15 Admin Overview.dc.html` and `p15-overview.js`: the three first directions, kept for history.
- `assets/`: the brand files the boards use.

To view the boards without Claude Design, serve this folder and open `preview.html`
(for example `python -m http.server 8765` here, then http://127.0.0.1:8765/preview.html).

Sample data on the boards is fictional. Decisions behind the design: `docs/PHASE20.md`.
