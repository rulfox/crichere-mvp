# Claude Design prompt: Google Play store graphics (Crichere, design update #9)

Paste everything below into Claude Design, attached to the existing Crichere project so it uses the current
theme, fonts, brand icon (`ic-launcher-full`), wordmark and screens (Sign in A, Dashboard, League detail E,
Live auction L, My Profile N).

Owner decisions behind this prompt (2026-11-10): store graphics come from Claude Design; the listing text is already
in Play Console (`docs/legal/PLAY-CONSOLE.md` section 3). Upload sizes are Google Play's, checked in Play Console on
2026-11-10.

---

Design the Google Play store graphics for Crichere, an Android app for amateur cricket leagues in India. Use the
existing Crichere design system exactly: colours, type, the app icon and the white wordmark. Don't redesign the app;
every phone screen shown must be one of the existing Crichere screens, filled with realistic sample data. Give every
asset its own artboard at the exact pixel size below, labelled with an ID (P1, P2...).

What Crichere does, for the copy:
- Organisers create a league with its ground, dates, rules and teams.
- Players set up a cricket profile (role, batting hand, bowling style) and join leagues near them, by state and
  district.
- Franchise owners build squads in a live player auction run by the organiser, using a virtual budget.
- Rosters, franchises and auction results in one place; league pages can be shared with a link.

Rules that keep the listing within Google Play policy (a reviewer reads these images):
- No words that suggest money games: never "win", "prize", "cash", "earn", "bet", "jackpot", "fantasy", "rewards".
- Auction amounts must read as a budget, not money changing hands: small, plain figures, and wherever an amount is
  prominent say "virtual budget". No piles of coins, notes, trophies with cash, or "₹" in large type.
- No Google Play badge, no "#1", "best", "top" or rating claims, no other brands or real player or team names, no
  IPL logos or colours that copy a real franchise.
- Sample people are fictional, adults, with plain initials avatars or neutral illustrated avatars, not stock photos of
  real people.
- Text inside images is English, large enough to read on a phone, and inside the safe areas given below.

Assets:

P1. App icon, 512 x 512 px, PNG, up to 1 MB. The existing Crichere launcher icon, unchanged, exported at this
    size on its full-bleed background. Google applies the rounded mask, so don't add rounded corners or a shadow.

P2. Feature graphic, 1024 x 500 px, PNG or JPEG, up to 15 MB, no transparency. The Crichere wordmark and one short line, for
    example "Run your local cricket league", beside a phone showing the Live auction screen (L) mid-lot. Keep the
    wordmark and the line inside the central 924 x 400 area: Play may crop the edges or overlay a play button in the
    middle on some surfaces, so don't put anything important in the exact centre. Calm, sporty, not busy. Make one
    alternative (P2b) with the League detail screen (E) instead of the auction.

P3 to P8. Phone screenshots, 1080 x 1920 px each (9:16, portrait), PNG, up to 8 MB each. Play needs 2 to 8; at
    least 4 at 1080 px or more per side make the app eligible for promotion. Each one: a short caption (at most 6 words)
    at the top on the brand background, and the real screen in a simple phone frame below it. In order:
    P3. "Find cricket leagues near you" with the Dashboard / league list filtered by state and district.
    P4. "Every league in one place" with League detail (E): ground, dates, rules, franchises.
    P5. "Build squads in a live auction" with Live auction (L) mid-lot, budget shown as "virtual budget".
    P6. "Rosters and results, ready to share" with the auction results or roster view.
    P7. "Your cricket profile" with My Profile (N): role, batting hand, bowling style.
    P8. "Safe for everyone" with the roster row menu open on Report / Block (board R1-R2).
    Keep captions and important UI out of the top and bottom 80 px, which Play can crop in some layouts.

Deliver: the artboards above, plus a one-line note per asset listing which existing screen it uses, so the developer
can check it against the real app before uploading.
