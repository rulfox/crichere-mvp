"""Build cleaned State -> District -> City reference data from the raw LGD pull (lgd2.json)."""
import json, re, difflib, collections

raw = json.load(open("lgd2.json", encoding="utf8"))

# Our existing 2-letter codes + display names (V4) -- kept as-is; stored profile/league rows hold the name.
OURS = {
    "AP": "Andhra Pradesh", "AR": "Arunachal Pradesh", "AS": "Assam", "BR": "Bihar", "CG": "Chhattisgarh",
    "GA": "Goa", "GJ": "Gujarat", "HR": "Haryana", "HP": "Himachal Pradesh", "JH": "Jharkhand",
    "KA": "Karnataka", "KL": "Kerala", "MP": "Madhya Pradesh", "MH": "Maharashtra", "MN": "Manipur",
    "ML": "Meghalaya", "MZ": "Mizoram", "NL": "Nagaland", "OD": "Odisha", "PB": "Punjab",
    "RJ": "Rajasthan", "SK": "Sikkim", "TN": "Tamil Nadu", "TS": "Telangana", "TR": "Tripura",
    "UP": "Uttar Pradesh", "UK": "Uttarakhand", "WB": "West Bengal",
    "AN": "Andaman and Nicobar Islands", "CH": "Chandigarh", "DN": "Dadra and Nagar Haveli and Daman and Diu",
    "DL": "Delhi", "JK": "Jammu and Kashmir", "LA": "Ladakh", "LD": "Lakshadweep", "PY": "Puducherry",
}
key = lambda n: re.sub(r"[^a-z]", "", n.lower())
BY_KEY = {key(v): c for c, v in OURS.items()}
BY_KEY[key("The Dadra And Nagar Haveli And Daman And Diu")] = "DN"

DISTRICT_FIX = {
    "Ntr": "NTR", "Lakshadweep District": "Lakshadweep", "East Singhbum": "East Singhbhum", "Leh Ladakh": "Leh",
    # LGD carries Odia transliterations for these; the English names are what users type and what Odisha uses in English
    "Kataka": "Cuttack", "Sundaragada": "Sundargarh", "Anugola": "Angul", "Baragada": "Bargarh",
    "Debagada": "Deogarh", "Kandhamala": "Kandhamal", "Nayagada": "Nayagarh", "Kendrapada": "Kendrapara",
}
# Cities forced in/renamed (LGD has no ULB for them or uses an administrative name).
CITY_RENAME = {
    "Greater Chennai Corporation": "Chennai", "S.A.S.Nagar-Mohali": "Mohali", "Gudalur-Cbe": "Gudalur",
    "Greater Mumbai": "Mumbai", "Ahmadabad": "Ahmedabad", "Haldwani-Cumkathgodam": "Haldwani",
    "Gangtok (M Corp.)": "Gangtok", "Bkt": "Bakshi Ka Talab", "Secundarabad": "Secunderabad",
}
# Odisha: LGD uses Odia transliterations; map to the established English town names.
OD_CITY = {
    "Anugola": "Angul", "Talacher": "Talcher", "Palalahada": "Pallahara", "Baragada": "Bargarh", "Kataka": "Cuttack",
    "Athagada": "Athagarh", "Debagada": "Deogarh", "Boudhagada": "Boudh", "Kendrapada": "Kendrapara",
    "Nayagada": "Nayagarh", "Khandapada": "Khandapara", "Raurkela": "Rourkela", "Sundaragada": "Sundargarh",
    "Bhabanipatana": "Bhawanipatna", "Dharmagada": "Dharamgarh", "Jayapatana": "Jaipatna", "Junagada": "Junagarh",
    "Jayapur": "Jeypore", "Kendujhargada": "Keonjhar", "Badabil": "Barbil", "Phulabani": "Phulbani",
    "Asika": "Aska", "Nabarangapur": "Nabarangpur", "Jagatsinghapur": "Jagatsinghpur",
}
CITY_SPLIT = {"Hubballi-Dharwad": ["Hubballi", "Dharwad"]}  # one ULB, two cities people actually name
CITY_DROP = {("UP", "Gnida"), ("UP", "Yeida")}  # industrial development authorities, not towns
CITY_EXTRA = {
    ("UP", "Gautam Buddha Nagar"): ["Noida", "Greater Noida"],  # development-authority towns, not ULBs
    ("AP", "Guntur"): ["Amaravati"],                            # state capital, no ULB
    ("GA", "South Goa"): ["Vasco da Gama"],                     # inside Mormugao ULB
    ("JH", "Bokaro"): ["Bokaro Steel City"],                    # steel township, no ULB
}
SUBDISTRICT_STATES = {"DL"}          # cities = sub-districts only
UNION_SUBDISTRICT_STATES = {"KA"}    # cities = ULBs + all sub-districts (LGD ULB mapping too sparse)

ULB_SUFFIX = re.compile(
    r"\s*(\(Gba\)|\(Np\)|\(M\)|Municipal Corporation|Municipal Council|Muncipal Council|Municipal Committee|"
    r"Municipal Board|Municipal Co-Operation|Municipality|City Corporation|Nagar Panchayat|Nagarpanchayat|Nagar Parishad|Nagar Palika|"
    r"Town Committee|Town Council|Town Panchayat|Tc|Mb|Np|Acc)$", re.I)
ULB_PREFIX = re.compile(r"^(Municipal (Committee|Council|Corporation)|City Corporation|Nagar Palika,?)\s+", re.I)
SUB_SUFFIX = re.compile(r"\s*(Circle|Cicle|Adc|Eac|Sdo|Hq|\(Pt\)|\(Pt-I\)|\(?Rural\)?|\(?Urban\)?|Central|East|West|North|South|Sadar)$", re.I)


def tidy(n):
    n = n.replace("*", "").replace("..", " ")
    n = re.sub(r"\(\s+", "(", n)
    n = re.sub(r"\s+\)", ")", n)
    n = re.sub(r"(\w)\(", r"\1 (", n)
    n = re.sub(r"\s*-\s*", "-", n) if not re.search(r"\s-\s(I|II|III)$", n) else n
    n = re.sub(r"\s+", " ", n).strip()
    if n.isupper() and len(n) > 3:
        n = n.title()
    return n


def clean_district(n):
    n = tidy(n)
    n = re.sub(r"\bAnd\b", "and", n)
    return DISTRICT_FIX.get(n, n)


def clean_ulb(n):
    n = tidy(n)
    if n in CITY_RENAME:
        return CITY_RENAME[n]
    if re.match(r"^Bengaluru \w+ City Corporation", n):
        return "Bengaluru"
    n = ULB_PREFIX.sub("", n)
    for _ in range(2):
        n = ULB_SUFFIX.sub("", n).strip()
    return n


def clean_sub(n):
    n = tidy(n)
    for _ in range(2):
        n = SUB_SUFFIX.sub("", n).strip()
    return n


def strip_district_qualifier(name, district_keys):
    m = re.match(r"^(.*\S)\s*\(([^)]+)\)$", name)
    if not m:
        return name
    q = key(m.group(2))
    if any(difflib.SequenceMatcher(None, q, dk).ratio() >= 0.75 for dk in district_keys):
        return m.group(1)
    return name


states = []
for s in raw:
    code = BY_KEY[key(s["stateNameEnglish"])]
    dkeys = [key(x["districtNameEnglish"]) for x in s["districts"]]
    districts = []
    for x in s["districts"]:
        dname = clean_district(x["districtNameEnglish"])
        cands = []  # (name, lgd_code, kind)
        if code not in SUBDISTRICT_STATES:
            seen = set()
            for u in x["ulbs"] + [u for sd in x["subdistricts"] for u in sd["ulbs"]]:
                if u["localBodyCode"] in seen:
                    continue
                seen.add(u["localBodyCode"])
                n = clean_ulb(u["localBodyNameEnglish"])
                if (code, n) in CITY_DROP:
                    continue
                for part in CITY_SPLIT.get(n, [n]):
                    cands.append((part, u["localBodyCode"], "ULB"))
        use_subs = code in SUBDISTRICT_STATES or code in UNION_SUBDISTRICT_STATES or not cands
        for sd in x["subdistricts"]:
            hq = key(sd["subdistrictNameEnglish"]) == key(x["districtNameEnglish"])
            if use_subs or (hq and not any(key(x["districtNameEnglish"]) in key(c[0]) for c in cands)):
                sn = clean_sub(sd["subdistrictNameEnglish"])
                ulb_keys = [key(c[0]) for c in cands if c[2] == "ULB"]
                if code in UNION_SUBDISTRICT_STATES and any(
                        difflib.SequenceMatcher(None, key(sn), uk).ratio() >= 0.8 or (len(key(sn)) > 4 and key(sn) in uk)
                        for uk in ulb_keys):
                    continue  # spelling variant of a ULB already listed (Kagwad/Kagawada, Kittur/Chennammana Kittur)
                cands.append((clean_sub(sd["subdistrictNameEnglish"]), sd["subdistrictCode"], "SUBDISTRICT"))
        for extra in CITY_EXTRA.get((code, dname), []):
            cands.append((extra, None, "MANUAL"))
        # strip "(District)" qualifiers unless that creates a clash inside this district
        base_counts = collections.Counter(key(strip_district_qualifier(c[0], dkeys)) for c in cands)
        out, seen_names = [], set()
        if code == "OD":
            cands = [(OD_CITY.get(n, n), lgd, kind) for n, lgd, kind in cands]
        for n, lgd, kind in cands:
            s2 = strip_district_qualifier(n, dkeys)
            if base_counts[key(s2)] == 1 or key(s2) == key(n):
                n = s2
            if not n or key(n) in seen_names:
                continue
            seen_names.add(key(n))
            out.append({"name": n, "lgd": lgd, "kind": kind})
        districts.append({"name": dname, "lgd": x["districtCode"], "cities": sorted(out, key=lambda c: c["name"])})
    # district-name dedupe safety
    assert len({key(d["name"]) for d in districts}) == len(districts), code
    states.append({"code": code, "name": OURS[code], "lgd": s["stateCode"], "districts": sorted(districts, key=lambda d: d["name"])})

json.dump(states, open("final.json", "w", encoding="utf8"), ensure_ascii=False, indent=1)
nd = sum(len(s["districts"]) for s in states)
nc = sum(len(d["cities"]) for s in states for d in s["districts"])
kinds = collections.Counter(c["kind"] for s in states for d in s["districts"] for c in d["cities"])
print(len(states), "states", nd, "districts", nc, "cities", dict(kinds))
print("empty districts:", [(s["code"], d["name"]) for s in states for d in s["districts"] if not d["cities"]])
