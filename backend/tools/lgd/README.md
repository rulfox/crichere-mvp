# LGD location data

Regenerates the State -> District -> City reference data from the Local Government Directory
(lgdirectory.gov.in). Last run 2026-10-06, output is `V20__refresh_locations_from_lgd.sql`.

```sh
cd backend/tools/lgd
python pull.py        # ~8k POSTs to LGD's public lgdws endpoints, writes lgd2.json (not committed)
python build.py       # cleaning + city rules, writes final.json (not committed)
python gen_sql.py ../../src/main/resources/db/migration/V<next>__refresh_locations_from_lgd.sql
```

A later refresh must be a **new** migration (Flyway checksums), and `gen_sql.py`'s `REMAP` list must be
rebuilt against whatever the previous seed was. City rules and manual overrides are in `build.py`;
the reasoning is in docs/OPEN-ITEMS.md section 6.
