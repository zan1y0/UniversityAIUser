"""Probe 研招网 (yz.chsi.com.cn) 硕士专业目录 endpoints.

The public catalog is a Vue app backed by form-POST JSON endpoints:
  /zsml/rs/zys.do   -> majors matching 门类/学科/专业名称
  /zsml/zydetail.do -> HTML shell; school list is filled by another endpoint
This script discovers which endpoint returns the per-school rows and what
fields it exposes, so the importer can be built against verified shapes.
"""
import json
import sys
import urllib.parse
import urllib.request

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)
BASE = "https://yz.chsi.com.cn"
HEADERS = {
    "User-Agent": UA,
    "Referer": BASE + "/zsml/",
    "Origin": BASE,
    "X-Requested-With": "XMLHttpRequest",
    "Accept": "application/json, text/javascript, */*; q=0.01",
}


def post_json(path, data):
    body = urllib.parse.urlencode(data, doseq=True).encode()
    req = urllib.request.Request(BASE + path, data=body, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=40) as r:
        raw = r.read().decode("utf-8", "replace")
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"_raw": raw[:2000]}


def get_text(path):
    req = urllib.request.Request(BASE + path, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read().decode("utf-8", "replace")


def main():
    majors = post_json("/zsml/rs/zys.do", {"mldm": "08", "curPage": 1, "pageSize": 20})
    rows = majors.get("msg", {}).get("list", [])
    print("major rows:", len(rows), "total:", majors.get("msg", {}).get("totalCount"))
    target = next((r for r in rows if r.get("zydm") == "081200"), rows[0] if rows else None)
    print(json.dumps(target, ensure_ascii=False, indent=2))

    if not target:
        return 1

    qs = urllib.parse.urlencode(
        {
            "zydm": target["zydm"],
            "zymc": target["zymc"],
            "xwlx": target["xwlx"],
            "mldm": target["mldm"],
            "mlmc": target["mlmc"],
            "yjxkdm": target["yjxkdm"],
            "yjxkmc": target["yjxkmc"],
            "sign": target["sign"],
        }
    )
    html = get_text("/zsml/zydetail.do?" + qs)
    print("detail html bytes:", len(html))
    for marker in ("访问出错", "dwmc", "rs/", "2026"):
        print(marker, "->", marker in html)
    return 0


if __name__ == "__main__":
    sys.exit(main())
