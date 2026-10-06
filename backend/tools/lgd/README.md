# LGD location data

Regenerates the State -> District reference data from the Local Government Directory
(lgdirectory.gov.in). Last run 2026-10-06, output was `V20__refresh_locations_from_lgd.sql` (which
also seeded cities; V21 dropped the city tier, so `gen_sql.py` now emits districts only).

```sh
cd backend/tools/lgd
python pull.py        # ~8k POSTs to LGD's public lgdws endpoints, writes lgd2.json (not committed)
python build.py       # cleaning + city rules, writes final.json (not committed)
python gen_sql.py ../../src/main/resources/db/migration/V<next>__refresh_locations_from_lgd.sql
```

A later refresh must be a **new** migration (Flyway checksums), and `gen_sql.py`'s `REMAP` list must be
rebuilt against whatever the previous seed was. Name cleaning is in `build.py`; the reasoning is in
docs/OPEN-ITEMS.md section 6.
