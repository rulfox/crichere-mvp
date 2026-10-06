"""Pull states -> districts -> sub-districts -> urban local bodies from LGD's public web services into lgd2.json."""
import json, urllib.request, time, concurrent.futures as cf

B = "https://lgdirectory.gov.in/webservices/lgdws/"


def post(ep):
    # Every lgdws endpoint is POST-only with query-string parameters and an empty body.
    err = None
    for i in range(6):
        try:
            r = urllib.request.Request(B + ep, data=b"", method="POST")
            return json.loads(urllib.request.urlopen(r, timeout=60).read().decode("utf8"))
        except Exception as e:
            time.sleep(2 * (i + 1)); err = e
    raise RuntimeError(f"{ep}: {err}")


states = post("stateList")
for s in states:
    s["districts"] = post(f"districtList?stateCode={s['stateCode']}")
districts = [x for s in states for x in s["districts"]]


def fill_district(x):
    c = x["districtCode"]
    x["ulbs"] = post(f"fetchUrbanLocalBodyForGivenDistrict?landRegionCode={c}&landRegionType=D")
    x["subdistricts"] = post(f"subdistrictList?districtCode={c}")


with cf.ThreadPoolExecutor(6) as ex:
    list(ex.map(fill_district, districts))

subdistricts = [sd for x in districts for sd in x["subdistricts"]]


def fill_subdistrict(sd):
    # Many ULBs are mapped to a sub-district rather than to the district itself.
    sd["ulbs"] = post(f"fetchUrbanLocalBodyForGivenDistrict?landRegionCode={sd['subdistrictCode']}&landRegionType=T")


with cf.ThreadPoolExecutor(8) as ex:
    list(ex.map(fill_subdistrict, subdistricts))

json.dump(sorted(states, key=lambda s: s["stateNameEnglish"]), open("lgd2.json", "w", encoding="utf8"), ensure_ascii=False, indent=1)
print(len(states), "states", len(districts), "districts", len(subdistricts), "sub-districts")
