# Google Play Console: answers and release checklist

**Last checked:** 2026-11-07, against release build 1.0.0 (versionCode 1) on `android-release` and the Privacy Policy in
this folder. Re-check every answer when a permission, SDK or data field changes: the Data safety form and the Privacy
Policy must say the same thing, or the app can be rejected or removed.

Developer account: personal, created after 13 Nov 2023 (owner, 2026-11-07). Publisher named in the policies: the
owner as an individual.

## 1. Release path

A new personal account can't publish to production straight away:

1. **Internal testing.** Upload `androidApp-release.aab` (`./gradlew :androidApp:bundleRelease`, signed with the
   upload key, docs/PHASE19.md). Opt in on your own phone, check sign-in, maps, Places, photo upload and push.
   Then copy the app signing SHA-1 and SHA-256 from Setup > App signing (step 4 below).
2. **Closed testing.** Create a closed track and add at least 12 testers (Google accounts, by email list or Google
   Group). At least 12 must stay opted in for **14 days in a row**. Invitations that weren't accepted don't count,
   and someone who opts out breaks the run. Ask testers to actually use it: Google asks about their feedback next.
3. **Apply for production access** (Dashboard). The questionnaire asks how you recruited testers, what feedback you
   got and what you changed, and why the app is ready. Keep short notes during the 14 days. If Google declines,
   the closed test continues and you apply again later.
4. **After the first upload:** send me the app signing SHA-1 and SHA-256. The SHA-1 goes on the Maps key (or Maps and
   Places fail in every Play install). The SHA-256 goes in `web-viewer/public/.well-known/assetlinks.json` (or shared
   links open the browser, not the app).
5. **Production.** Staged rollout, starting at 20%. Watch Crashlytics and Play's Android vitals for the first days.

Every track goes through review. The first review of a new app usually takes several days.

## 2. App content forms

### Privacy policy

`https://crichere.com/privacy`. It must be complete before you submit: no [placeholders] (docs/OPEN-ITEMS.md).

### App access

"All or some functionality is restricted." Add one set of instructions:

- Name: Crichere sign-in
- Username / phone: the MSG91 demo number (kept only here in Play Console, never in the repo)
- Password / code: its fixed OTP
- Other information: paste the review notes (`PLAY-REVIEW-NOTES.md`), including the sample league's name.

The demo account must already have a finished profile and be a member of a sample league with players, franchises and
a completed auction, so the reviewer sees the real features.

### Ads

**No**, the app contains no ads.

### Advertising ID

**No.** The release manifest has no `AD_ID` permission, and no SDK in the app uses the advertising ID (Firebase
Messaging and Crashlytics don't; there is no Analytics).

### Content rating (IARC questionnaire)

- Category: **All other app types** (not a game, not reference or news).
- Violence, fear, sexuality, crude humour, bad language, drugs, alcohol, tobacco: **No** to all.
- Gambling: real gambling **No**; simulated gambling **No**. The auction uses a virtual budget with no cash value
  (Terms, section 1).
- Users can interact or exchange content: **Yes** (profiles, photos, league pages; reports and blocking available).
- Shares the user's current location with other users: **No.** The district on a profile is chosen by the user,
  and "Use my location" only fills that field on the device.
- Digital purchases: **No.** Unrestricted web browsing: **No.**

Expected result: a low age rating with an "Users interact" note.

### Target audience and content

- Age groups: **18 and over** only. The Terms require 18+ and sign-in has the "I am 18 or older" tick.
- Appeal to children: **No.** Not in Designed for Families.

### News, government, health, financial features

- News app: **No.** Government app: **No.** Health app: **No.**
- Financial features: **None.** Crichere doesn't process, hold or move money. League fees are paid between users
  outside the app; the app only stores a screenshot as proof.

### Data safety

General questions:

- Does the app collect or share any of the required user data types? **Yes.**
- Is all user data encrypted in transit? **Yes** (HTTPS only; release builds refuse cleartext).
- Do you provide a way for users to request that their data is deleted? **Yes.** Delete account URL:
  `https://crichere.com/delete-account`.
- Shared with third parties: **None.** MSG91, Firebase, Google Maps/Places, AWS and Railway act as service providers
  on our behalf, which Play doesn't count as sharing.
- Processed ephemerally: **No** for all.

Data types collected:

| Category > type | Required or optional | Purposes | What it is |
|---|---|---|---|
| Personal info > Name | Required | App functionality, Account management | Profile name |
| Personal info > Phone number | Required | Account management, Fraud prevention, security and compliance | OTP sign-in |
| Personal info > User IDs | Required | App functionality, Account management | Account ID |
| Personal info > Other info | Required | App functionality | Playing role, batting hand, bowling style, consent record |
| Location > Approximate location | Required | App functionality | Home state and district; "Use my location" and "Show nearest to me" (coarse, foreground only) |
| Photos and videos > Photos | Required | App functionality | Profile photo; league logos and banners; franchise logos; payment screenshots |
| Financial info > Other financial info | Optional | App functionality | Payment screenshots for paid leagues (proof for the organiser) |
| App activity > App interactions | Required | App functionality | Bids, joins, follows, franchise claims |
| App activity > In-app search history | Optional | App functionality | Ground search text sent to Google Places |
| App activity > Other user-generated content | Optional | App functionality, Fraud prevention, security and compliance | League details, ground details, reports and their text, blocks |
| App info and performance > Crash logs | Required | Analytics, App functionality | Crashlytics crash reports |
| App info and performance > Diagnostics | Required | Analytics, App functionality | Device model, OS and app version in crash reports |
| Device or other IDs > Device or other IDs | Required | App functionality, Analytics | FCM notification token; Crashlytics install ID |

Not collected: email, address (street), precise location, contacts, messages, audio, video, files, calendar, health,
web browsing, installed apps, user payment info (cards, UPI IDs), purchase history.

Each row matches Privacy Policy section 1. If one changes, change the other.

### Account deletion

In-app path: My Profile > Delete account. Web: `https://crichere.com/delete-account`. Both are live.

## 3. Store listing

| Item | Spec | Status |
|---|---|---|
| App name | Up to 30 characters | `Crichere` |
| Short description | Up to 80 characters | Draft below |
| Full description | Up to 4,000 characters | Draft below |
| App icon | 512 x 512 PNG, 32-bit | To export from the brand icon |
| Feature graphic | 1024 x 500 PNG or JPEG | To make |
| Phone screenshots | 2 to 8; 16:9 or 9:16; each side 320 to 3,840 px | To make from the release build |
| Category | Sports | |
| Contact email | hello@crichere.com | Check the mailbox receives mail |
| Website | https://crichere.com | |

Avoid words that suggest money games in the listing and screenshots: win, prize, cash, earn, bet, jackpot. Prefer
screenshots of league pages, rosters and the auction with small, plain budget numbers.

**Short description (draft, 69 characters):**

> Run your local cricket league: player auction, franchises and rosters.

**Full description (draft):**

> Crichere is for amateur cricket leagues played on real grounds.
>
> Organisers create a league with its ground, dates, rules and teams. Players set up a cricket profile with their
> role, batting hand and bowling style, and join leagues near them. Franchise owners build their squads in a live
> player auction run by the organiser.
>
> - Find leagues near you by state and district
> - Player profiles with role and playing style
> - Live player auction with a virtual budget for each franchise
> - Rosters, franchises and auction results in one place
> - Share league pages with a link
> - Report or block anyone, right from the league page
>
> The auction uses a virtual budget to pick squads. Bids are not payments and have no cash value. Crichere has no
> betting, fantasy contests or games of chance. If a league has a joining fee, it is paid to the organiser outside
> the app.
>
> For players and organisers aged 18 and over in India.

## 4. Rejection risks

| Risk | Level | Why | What covers it |
|---|---|---|---|
| Real-money or gambling impression | Medium | Rupee amounts in the auction, joining fees, league awards | Terms section 1, in-app money note, review notes, listing wording, screenshots |
| Policy incomplete or not matching Data safety | Medium until fixed | Placeholders on the live pages | Fill them (OPEN-ITEMS), then compare section 2 with the policy |
| Reviewer can't reach features | Low-medium | Demo account without a profile or league | Finish the demo profile, sample league, App access notes |
| Payment screenshots | Low | Users upload UPI receipts | Declared under Photos and Other financial info; private storage; deleted with the account |
| User-generated content | Low | Profiles, photos, league pages | Report, block, Terms tick, daily moderation (PHASE18) |
| Permissions, target API, 16 KB pages | Low | | Checked 2026-11-07: no sensitive permissions, targetSdk 36, native libraries 16 KB aligned |
| Closed test not accepted | Low-medium | Testers drop out or give no feedback | Recruit more than 12, collect feedback notes |
