"""Discover the 研招网 detail endpoints that return per-school catalog rows."""
import json
import re
import sys
import urllib.parse

sys.path.insert(0, r"D:\STUDY\UniversityAIUser\scripts\scrape")
from _probe_chsi import get_text, post_json  # noqa: E402

MAJOR_CODE = "0812"

majors = []
for page in range(1, 30):
    res = post_json(
        "/zsml/rs/zys.do", {"mldm": "08", "yjxkdm": MAJOR_CODE, "curPage": page, "pageSize": 20}
    )
    msg = res.get("msg") or {}
    rows = msg.get("list") or []
    majors.extend(rows)
    if page >= (msg.get("totalPage") or 1):
        break

print("majors found:", [(m["zydm"], m["zymc"]) for m in majors])
if not majors:
    sys.exit(1)

target = next((m for m in majors if m["zydm"] == "081200"), majors[0])
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
out = r"D:\STUDY\UniversityAIUser\scripts\scrape\_cache\zydetail_081200.html"
import os

os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, "w", encoding="utf-8").write(html)
print("html:", len(html), "->", out)
for m in sorted(set(re.findall(r"/zsml/[A-Za-z0-9_/.\-]+", html))):
    print("  endpoint:", m)
