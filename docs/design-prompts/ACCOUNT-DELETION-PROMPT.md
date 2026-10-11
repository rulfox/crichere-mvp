# Claude Design prompt: account deletion (Crichere mobile, design update #7)

Paste everything below into Claude Design, attached to the existing Crichere project so it uses the current
theme, fonts and components (My Profile screen N, Sign in A, League detail E, destructive confirm dialog).

---

Design the account deletion flow for the Crichere Android and iOS app. Use the existing Crichere design system
exactly (colours, type, spacing, the red destructive style already used for "Log out" and "Mark league
completed"). Phone frame 412 x 915 dp. Give every state its own artboard, labelled with an ID (D1, D2...).

Context, so the copy and states are right:
- Users sign in with phone number + OTP. A league has one organizer and optional co-organizers.
- Deleting signs the user out on every device at once. Nothing is erased for 14 days; signing in again in that
  time cancels the deletion. After 14 days the phone number, profile, photo, payment screenshots and follows are
  erased. Auction results and rosters of leagues that already ran stay, with the name shown as "Deleted user".
- Deletion is refused while (a) an auction the user organizes, plays in or owns a franchise in is running, or
  (b) the user organizes a league that isn't completed and has no co-organizer. The server returns the list of
  those leagues with the reason for each.
- If the user organizes a league that has a co-organizer, the earliest co-organizer becomes the organizer when
  the account is erased.

Screens and states:

D1. My Profile (existing screen N): add a "Delete account" entry below "Log out". It must read as destructive but
    quieter than Log out (for example a text-style row in the error colour). Show where it sits on a long profile.

D2. Delete account screen, loading state (checking what would block the deletion).

D3. Delete account screen, can delete. Explains in plain words: what is deleted, what is kept ("Deleted user" in
    past leagues), the 14-day window and that signing in again cancels it, and, when relevant, which leagues will
    pass to which co-organizer (show a variant with 2 leagues listed). Primary action: "Delete account" (destructive).

D4. Delete account screen, blocked. Lists each blocking league as a row with its name and a one-line reason:
    "Auction is live. Wait for it to end." / "Add a co-organizer or mark the league completed." Each row opens
    that league. The delete action is not available. Show a variant with one of each reason.

D5. Failed to load the check (network error) with Retry.

D6. Confirm dialog: the user types DELETE to enable the confirm button. States: empty, typed wrong ("delete"
    lowercase does not count), typed correctly, submitting (spinner, cancel disabled), and a failure (inline
    error, dialog stays open).

D7. After deleting: signed out, landing on Sign in (A) with a one-time notice: "Your account will be deleted on
    25 Oct 2026. Sign in before then to keep it."

D8. After signing in during the 14 days: a one-time notice on the first screen after sign-in: "Welcome back. Your
    account deletion was cancelled."

D9. League detail (existing screen E), seen by the new organizer after a handover: a dismissible notice near the
    top: "The previous organizer deleted their account. You're now the organizer of <league>." Show it with the
    organizer menu visible below.

D10. Rosters and auction results showing a deleted user: confirm "Deleted user" with a neutral avatar (no photo,
     no initials from a real name) reads well in the player row, the franchise owner line and the results list.

Deliver: artboards for D1-D10 in light theme, plus D3, D4 and D6 in dark theme. Annotate spacing, type styles
and colour tokens on D3, D4 and D6.
