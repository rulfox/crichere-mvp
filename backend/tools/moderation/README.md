# Moderation runbook

How the team handles reports (docs/PHASE18.md). There is no admin screen: this script is the only way in.

## Setup

```
pip install "psycopg[binary]" boto3
export DATABASE_URL=...   # production Postgres, from Railway
export MEDIA_BUCKET=crichere-media-prod
```

Keep these out of the repo and out of chat. Needs AWS credentials with `s3:DeleteObject` for `remove-image`.

## Daily check, every day including weekends

```
python moderation.py list
python moderation.py show <report-id>
```

Handle the oldest first. Look first at `UNDER_18` and `HATE_OR_HARASSMENT`.

| Outcome | Command |
|---|---|
| Nothing wrong | `python moderation.py dismiss <report-id> --note "why" --yes` |
| Image breaks the rules | `python moderation.py remove-image <kind> <id> --report <report-id> --yes`, then `resolve` |
| Serious or repeated | `python moderation.py suspend <user-id> --report <report-id> --note "why" --yes`, then `resolve` |
| Handled another way | `python moderation.py resolve <report-id> --note "what was done" --yes` |

`remove-image` kinds: `photo` (user id), `league-logo` and `league-banner` (league id), `franchise-logo` (franchise id).
It clears the image for everyone, tells the owner once, and deletes the S3 object. Check `show` first: the evidence
link stops working after this, so save a copy if the matter might go further.

Changing a league's text: there is no command. Contact the organizer, or suspend them.

## Rules

- Never tell anyone who reported them. `show` prints the reporter; keep that private.
- Reports never cause automatic action. A person decides each one.
- Don't suspend an organizer during a live auction, because it stalls. The script refuses unless you pass
  `--during-auction`; use that only for an urgent safety case, and warn a co-organizer.
- A suspended number can't sign up again, even after the account is deleted.
- A suspended user who asks to be deleted: they use the email route in the Privacy Policy.
- Illegal content: don't just delete it. Get legal advice first.

## Retention

Set the period in the Privacy Policy, then delete closed reports older than it. Not automated yet. Open reports keep
their images, even if the account is deleted; the daily media sweep removes them after the report closes.

## Unsuspending

`python moderation.py unsuspend <user-id> --note "why" --yes`. If the account was already purged, delete its row from
`suspended_phone_hashes` by hand, only when the owner has decided to let the number back.
