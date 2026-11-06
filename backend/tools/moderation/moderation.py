#!/usr/bin/env python3
"""Team tool for reports and moderation actions (docs/PHASE18.md). See README.md.

Reads DATABASE_URL, and MEDIA_BUCKET plus the usual AWS settings for remove-image, from the
environment. Never put them in this repo. Every action that changes something is written to
moderation_actions and needs --yes.
"""
import argparse
import json
import os
import sys
import uuid

import psycopg

IMAGE_TARGETS = {
    # kind: (table, url column, removed column, owner column, name column, notice kind)
    "photo": ("profiles", "photo_url", "photo_removed", "user_id", None, "PHOTO"),
    "league-logo": ("leagues", "logo_url", "logo_removed", "organizer_user_id", "name", "LEAGUE_LOGO"),
    "league-banner": ("leagues", "banner_url", "banner_removed", "organizer_user_id", "name", "LEAGUE_BANNER"),
    "franchise-logo": ("league_franchises", "logo_url", "logo_removed", "owner_user_id", "name", "FRANCHISE_LOGO"),
}
ID_COLUMN = {"profiles": "user_id", "leagues": "id", "league_franchises": "id"}


def connect():
    url = os.environ.get("DATABASE_URL")
    if not url:
        sys.exit("Set DATABASE_URL first.")
    return psycopg.connect(url)


def audit(cur, action, report=None, user=None, league=None, note=None):
    cur.execute(
        "insert into moderation_actions (id, action, report_id, user_id, league_id, note) values (%s, %s, %s, %s, %s, %s)",
        (uuid.uuid4(), action, report, user, league, note),
    )


def close_report(cur, report, status):
    cur.execute(
        "update reports set status = %s, resolved_at = now() where id = %s and status = 'OPEN'",
        (status, report),
    )
    if cur.rowcount == 0:
        sys.exit(f"No open report {report}.")


def cmd_list(args):
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """select id, created_at, target_type, reason, coalesce(target_user_id, target_league_id)
               from reports where status = 'OPEN' order by created_at"""
        )
        rows = cur.fetchall()
    for row in rows:
        print(f"{row[0]}  {row[1]:%Y-%m-%d %H:%M}  {row[2]:<6}  {row[3]:<20}  {row[4]}")
    print(f"{len(rows)} open.")


def cmd_show(args):
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """select target_type, target_user_id, target_league_id, context_league_id, reason, status,
                      created_at, details, snapshot, reporter_id from reports where id = %s""",
            (args.report,),
        )
        row = cur.fetchone()
    if row is None:
        sys.exit("No such report.")
    labels = ("type", "user", "league", "context league", "reason", "status", "created", "details")
    for label, value in zip(labels, row):
        print(f"{label + ':':<16}{value}")
    print("evidence:\n" + json.dumps(json.loads(row[8]), indent=2, ensure_ascii=False))
    print(f"{'reporter:':<16}{row[9]}  (keep private)")


def cmd_dismiss(args):
    require_yes(args)
    with connect() as conn, conn.cursor() as cur:
        close_report(cur, args.report, "DISMISSED")
        audit(cur, "DISMISS_REPORT", report=args.report, note=args.note)
    print("Dismissed.")


def cmd_resolve(args):
    require_yes(args)
    with connect() as conn, conn.cursor() as cur:
        close_report(cur, args.report, "RESOLVED")
        audit(cur, "RESOLVE_REPORT", report=args.report, note=args.note)
    print("Resolved.")


def object_key(url):
    marker = ".amazonaws.com/"
    return url.split(marker, 1)[1].split("?", 1)[0] if url and marker in url else None


def owned_key_prefix(cur, kind, target_id):
    """Where this row's own uploads live: an image stored anywhere else belongs to someone else."""
    if kind == "photo":
        return f"users/{target_id}/"
    if kind == "franchise-logo":
        cur.execute("select league_id from league_franchises where id = %s", (target_id,))
        return f"leagues/{cur.fetchone()[0]}/franchise-logos/"
    return f"leagues/{target_id}/"


def cmd_remove_image(args):
    require_yes(args)
    table, url_col, removed_col, owner_col, name_col, notice_kind = IMAGE_TARGETS[args.kind]
    id_col = ID_COLUMN[table]
    name_sql = name_col or "null"
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            f"select {url_col}, {owner_col}, {name_sql} from {table} where {id_col} = %s for update",
            (args.id,),
        )
        row = cur.fetchone()
        if row is None:
            sys.exit("Nothing found with that id.")
        url, owner, subject = row
        if url is None:
            sys.exit("It has no image.")
        cur.execute(f"update {table} set {url_col} = null, {removed_col} = true where {id_col} = %s", (args.id,))
        # A league can point at an image another row uses, so never delete an object something still needs.
        cur.execute(
            """select count(*) from (
                   select photo_url u from profiles union all select logo_url from leagues
                   union all select banner_url from leagues union all select logo_url from league_franchises
               ) refs where split_part(u, '?', 1) = split_part(%s, '?', 1)""",
            (url,),
        )
        still_used = cur.fetchone()[0] > 0
        owned_prefix = owned_key_prefix(cur, args.kind, args.id)
        cur.execute(
            "insert into moderation_notices (id, user_id, kind, subject_name) values (%s, %s, %s, %s)",
            (uuid.uuid4(), owner, notice_kind, subject),
        )
        audit(cur, "REMOVE_IMAGE", report=args.report, user=owner, note=f"{args.kind} {url}")
    # After the commit: the image must disappear from the app even if S3 fails, and this says so.
    key = object_key(url)
    if still_used:
        print(f"Removed from this row. Another row uses the same image, so check it; S3 object kept: {url}")
        return
    if key is None or args.no_s3:
        print(f"Removed from the app. S3 object NOT deleted: {url}")
        return
    if not key.startswith(owned_prefix):
        print(f"Removed from the app. The image isn't stored under this owner, so it was kept: {key}")
        return
    try:
        import boto3

        boto3.client("s3").delete_object(Bucket=os.environ["MEDIA_BUCKET"], Key=key)
        print("Removed from the app and S3.")
    except Exception as error:
        print(f"Removed from the app. S3 delete FAILED, delete {key} by hand: {error}")


def cmd_suspend(args):
    require_yes(args)
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            """select l.name from leagues l where l.organizer_user_id = %s and l.auction_status = 'IN_PROGRESS'""",
            (args.user,),
        )
        live = [row[0] for row in cur.fetchall()]
        if live and not args.during_auction:
            sys.exit(f"Organizer of a live auction: {', '.join(live)}. Wait for it to end, or pass --during-auction for an urgent safety case.")
        # Same row lock a token refresh takes.
        cur.execute("select id from users where id = %s and deleted_at is null for update", (args.user,))
        if cur.fetchone() is None:
            sys.exit("No such account.")
        cur.execute("update users set suspended_at = now() where id = %s", (args.user,))
        cur.execute("update refresh_tokens set revoked_at = now() where user_id = %s and revoked_at is null", (args.user,))
        cur.execute("delete from device_tokens where user_id = %s", (args.user,))
        audit(cur, "SUSPEND", report=args.report, user=args.user, note=args.note)
    print("Suspended. Takes effect within 30 seconds.")


def cmd_unsuspend(args):
    require_yes(args)
    with connect() as conn, conn.cursor() as cur:
        cur.execute("update users set suspended_at = null where id = %s and suspended_at is not null", (args.user,))
        if cur.rowcount == 0:
            sys.exit("That account isn't suspended.")
        audit(cur, "UNSUSPEND", user=args.user, note=args.note)
    print("Unsuspended. They can sign in again.")


def require_yes(args):
    if not args.yes:
        sys.exit("This changes production data. Check the ids, then add --yes.")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)

    commands.add_parser("list").set_defaults(run=cmd_list)

    show = commands.add_parser("show")
    show.add_argument("report")
    show.set_defaults(run=cmd_show)

    for name, run in (("dismiss", cmd_dismiss), ("resolve", cmd_resolve)):
        command = commands.add_parser(name)
        command.add_argument("report")
        command.add_argument("--note")
        command.add_argument("--yes", action="store_true")
        command.set_defaults(run=run)

    remove = commands.add_parser("remove-image")
    remove.add_argument("kind", choices=IMAGE_TARGETS)
    remove.add_argument("id", help="user id for photo; league id for league images; franchise id for franchise-logo")
    remove.add_argument("--report")
    remove.add_argument("--no-s3", action="store_true", help="skip the S3 delete, for local testing")
    remove.add_argument("--yes", action="store_true")
    remove.set_defaults(run=cmd_remove_image)

    suspend = commands.add_parser("suspend")
    suspend.add_argument("user")
    suspend.add_argument("--report")
    suspend.add_argument("--note")
    suspend.add_argument("--during-auction", action="store_true")
    suspend.add_argument("--yes", action="store_true")
    suspend.set_defaults(run=cmd_suspend)

    unsuspend = commands.add_parser("unsuspend")
    unsuspend.add_argument("user")
    unsuspend.add_argument("--note")
    unsuspend.add_argument("--yes", action="store_true")
    unsuspend.set_defaults(run=cmd_unsuspend)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
