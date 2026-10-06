"""Generate the Flyway migration that replaces districts/cities with the cleaned LGD set (final.json)."""
import json, sys

d = json.load(open("final.json", encoding="utf8"))
out_path = sys.argv[1]
q = lambda s: "'" + s.replace("'", "''") + "'"
STATE_NAME = {s["code"]: s["name"] for s in d}

# (state code, old district, old city) -> (new district, new city): every V4/V5-seeded pair whose
# district or city no longer exists after the refresh. A NULL old city remaps the district for any city.
REMAP = [
    ("AP", "Krishna", "Vijayawada", "NTR", "Vijayawada"),
    ("AP", "Chittoor", "Tirupati", "Tirupati", "Tirupati"),
    ("AS", "Kamrup Metropolitan", "Dispur", "Kamrup Metro", "Guwahati"),
    ("AS", "Kamrup Metropolitan", None, "Kamrup Metro", None),
    ("MH", "Mumbai City", None, "Mumbai", None),
    ("MH", "Aurangabad", None, "Chhatrapati Sambhajinagar", None),
    ("SK", "East Sikkim", None, "Gangtok", None),
    ("TS", "Warangal Urban", None, "Hanumakonda", None),
    ("UP", "Gautam Buddh Nagar", None, "Gautam Buddha Nagar", None),
    ("AN", "South Andaman", None, "South Andamans", None),
]
# sanity: every remap target exists
idx = {(s["code"], x["name"]): {c["name"] for c in x["cities"]} for s in d for x in s["districts"]}
for st, od, oc, nd, nc in REMAP:
    assert (st, nd) in idx, (st, nd)
    assert nc is None or nc in idx[(st, nd)], (st, nd, nc)

L = []
w = L.append
w("""-- Location reference refresh from the Local Government Directory (LGD, lgdirectory.gov.in, Govt of
-- India), pulled 2026-10-06 through its public lgdws web services. Replaces the hand-curated V4/V5
-- district and city seed (~95 districts, ~90 cities) with every current district and a full city list.
-- See docs/OPEN-ITEMS.md "Location data (LGD)" for the rules and backend/tools/lgd/ to regenerate.
--
-- States keep their V4 codes and names (profiles/leagues/grounds store the state *name*, and our names
-- already match LGD); they only gain lgd_code. Districts are LGD's 784 districts. Cities per district:
--   * Urban Local Bodies (corporations, municipalities, nagar panchayats, ...) mapped to the district
--     or to one of its sub-districts, names cleaned of type suffixes ("Municipal Council", "Tc", ...);
--   * a district with no ULB in LGD gets its sub-districts (taluk/tehsil/mandal/circle) instead;
--   * Karnataka gets ULBs plus taluks (LGD's Karnataka ULB mapping misses most taluk towns);
--   * Delhi uses sub-districts (LGD still lists the three pre-2022 municipal corporations);
--   * a district whose HQ town is missing gets the same-named sub-district;
--   * a handful of manual additions (source = 'MANUAL'): Noida, Greater Noida, Amaravati,
--     Vasco da Gama, Bokaro Steel City.
--
-- profiles/leagues/grounds hold location as plain text (not FKs), so the old rows survive the reseed;
-- the UPDATEs below move every V4/V5-seeded district/city pair that no longer exists onto its new name.

ALTER TABLE states ADD COLUMN lgd_code INTEGER UNIQUE;
ALTER TABLE districts ADD COLUMN lgd_code INTEGER UNIQUE;
-- A city's lgd_code is a ULB code or a sub-district code depending on source; not unique (one ULB can
-- span several districts, e.g. Hyderabad, and Hubballi-Dharwad is listed as two cities).
ALTER TABLE cities ADD COLUMN lgd_code INTEGER;
ALTER TABLE cities ADD COLUMN source VARCHAR CHECK (source IN ('ULB', 'SUBDISTRICT', 'MANUAL'));
""")

w("UPDATE states s SET lgd_code = v.lgd FROM (VALUES")
w(",\n".join(f"    ({q(s['code'])}, {s['lgd']})" for s in d))
w(") AS v(code, lgd) WHERE s.code = v.code;\n")

for table in ("profiles", "leagues", "grounds"):
    w(f"-- {table}: old seeded district/city pairs -> refreshed names (city-specific rows first)")
    for st, od, oc, nd, nc in sorted(REMAP, key=lambda r: r[2] is None):
        sets = f"district = {q(nd)}" + (f", city = {q(nc)}" if nc else "")
        cond = f"state = {q(STATE_NAME[st])} AND district = {q(od)}" + (f" AND city = {q(oc)}" if oc else "")
        w(f"UPDATE {table} SET {sets} WHERE {cond};")
    w("")

w("-- Reseed. Deleting districts cascades to cities (V5 FK ON DELETE CASCADE); nothing else references either.")
w("DELETE FROM districts;\n")
for s in d:
    w(f"-- {s['name']}")
    w("INSERT INTO districts (state_code, name, lgd_code) VALUES")
    w(",\n".join(f"    ({q(s['code'])}, {q(x['name'])}, {x['lgd']})" for x in s["districts"]) + ";")
    w("")

for s in d:
    w(f"-- {s['name']} cities")
    w("INSERT INTO cities (district_id, name, lgd_code, source)")
    w("SELECT dist.id, v.name, v.lgd_code, v.source FROM (VALUES")
    rows = []
    for x in s["districts"]:
        for c in x["cities"]:
            lgd = "NULL::integer" if c["lgd"] is None else str(c["lgd"])
            rows.append(f"    ({x['lgd']}, {q(c['name'])}, {lgd}, {q(c['kind'])})")
    w(",\n".join(rows))
    w(") AS v(district_lgd, name, lgd_code, source)")
    w("JOIN districts dist ON dist.lgd_code = v.district_lgd;")
    w("")

open(out_path, "w", encoding="utf8", newline="\n").write("\n".join(L))
print("wrote", out_path, sum(len(x["cities"]) for s in d for x in s["districts"]), "cities")
