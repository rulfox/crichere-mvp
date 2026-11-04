# Claude Design prompt: report and block (Crichere mobile, design update #8)

Paste everything below into Claude Design, attached to the existing Crichere project so it uses the current
theme, fonts and components (League detail E, Live auction L, My Profile N, Sign in A, destructive confirm dialog).

Owner decisions behind this prompt (2026-11-04): blocking hides the blocked user's content but keeps their name in
shared rosters and auctions; reports go to the Crichere team only, never to the organizer.

---

Design the report and block flows for the Crichere Android and iOS app. Google Play requires both for apps with
user uploads. Use the existing Crichere design system exactly (colours, type, spacing, bottom sheets, snackbars, the
red destructive style). Phone frame 412 x 915 dp. Give every state its own artboard, labelled with an ID (R1, R2...).

Context, so the copy and states are right:
- Users upload profile photos, league logos, banners and descriptions, and franchise logos. Other users see them.
- The app has no chat and no direct messages. There is no separate screen for another user's profile: people appear
  as rows in league rosters, as franchise owners, and as lots in the live auction.
- Two things can be reported: a person (their name or photo, or their franchise logo) and a league (its name, logo,
  banner or description).
- Reports go to the Crichere team, who review them by hand. The reported person is never told who reported them, and
  the organizer does not see reports.
- Blocking a person hides their photo and uploads from the blocker, and removes leagues they organize from the
  blocker's dashboard. Their name stays in shared rosters and auctions so results stay correct. A blocked person
  is not told. Blocking does not stop either person joining a league.
- The team can remove an image or suspend an account.

Screens and states:

R1. League detail (existing screen E), roster row: add a quiet overflow action on rows for other people. It must sit
    alongside the organizer's existing row actions (Remove, Approve leave, View payment screenshot) without crowding
    them. Show a player row and a franchise owner row, as a member and as the organizer. Your own row has no action.
    "Deleted user" rows have no action.

R2. Row menu: "Report" and "Block" (or "Unblock" if already blocked).

R3. League detail: "Report league" entry, for anyone viewing a league they don't organize. Show where it sits for
    a signed-in viewer.

R4. Report sheet, step 1: pick a reason. Person: "Inappropriate photo or logo", "Hate or harassment", "Pretending to
    be someone else", "Under 18", "Spam or misleading", "Something else". League: "Inappropriate image or text",
    "Hate or harassment", "Betting or gambling", "Fake or scam league", "Something else". Show the name of who or what
    is being reported at the top.

R5. Report sheet, step 2: optional details field (500 characters, with counter; required only for "Something else"),
    and for a person, an unticked "Also block this person" option. States: empty, filled, at the limit, submitting
    (spinner, sheet can't be dismissed), failure (inline error, text kept, Retry).

R6. Report sent: confirmation with "Thanks. Our team will review this." Also the variant when this user already
    reported the same person or league: "You've already reported this. Our team will review it."

R7. Block confirm dialog: "Block <name>?" explaining in plain words what changes: their photo and uploads are hidden
    from you, leagues they organize leave your dashboard, their name still appears in leagues you share, and they
    aren't told. States: default, submitting, failure (inline error, dialog stays open). Then a snackbar "<name> blocked"
    with Undo.

R8. How a blocked person looks: roster row, franchise owner line, live auction lot and auction results, each with a
    neutral avatar in place of the photo and a neutral logo in place of their franchise logo. The name stays. Decide
    whether a small "Blocked" label is needed; the live auction must stay readable and calm.

R9. League organized by someone you blocked, opened from a shared link or notification: a notice explaining this, with
    "Unblock" and the league content hidden until then. Show a variant where you are also a member of that league:
    the league stays usable with their uploads hidden.

R10. My Profile (existing screen N): add a "Blocked people" entry, placed so it doesn't compete with Log out and
     Delete account.

R11. Blocked people screen: list with name, neutral avatar and Unblock. States: loading, list, empty, load failure with
     Retry, unblock in progress, unblock failure. Unblock takes effect at once with a snackbar and Undo.

R12. Removed by the team: how a removed photo, logo or banner appears to everyone (neutral placeholder). A one-time
     notice to the owner the next time they open the app: "Your <photo / logo / banner> was removed because it broke
     our rules. Contact hello@crichere.com if you think this is a mistake."

R13. Suspended account: on Sign in (existing screen A), after the OTP step: "Your account is suspended. Contact
     hello@crichere.com." No way forward except back to Sign in.

Deliver: artboards for R1-R13 in light theme, plus R4, R5, R7 and R8 in dark theme. Annotate spacing, type styles and
colour tokens on R2, R4, R5 and R7.
