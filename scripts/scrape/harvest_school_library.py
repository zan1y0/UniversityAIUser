"""Harvest the 研招网 院校库 (yz.chsi.com.cn/sch) into a school dimension table.

Each result card carries the school name, schId, 所在地, 主管部门 and its 5-digit
招生单位代码 (the 网报公告 link), plus 双一流 / 自划线 / 研究生院 tags. That code is
the same key the 专业目录 uses, so this table resolves codes for the crawl.
"""
from __future__ import annotations

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import get_text  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "_cache", "schools_library.json")

CARD = re.compile(r'<div class="sch-item">(.*?)</div>\s*</div>\s*</div>', re.S)


def clean(x: str) -> str:
    return re.sub(r"\s+", " ", x or "").strip()


def parse_page(html: str) -> list[dict]:
    out = []
    blocks = html.split('<div class="sch-item">')[1:]
    for b in blocks:
        sch = re.search(r"schoolInfo--schId-(\d+)\.dhtml", b)
        name = re.search(r"schoolInfo--schId-\d+\.dhtml[^>]*>\s*([^<]{2,40})\s*<", b)
        dw = re.search(r"sswbgg/\?dwdm=(\d{5})&ssdm=(\d+)", b)
        if not (sch and name):
            continue
        dept = re.search(r'<div class="sch-department">(.*?)</div>', b, re.S)
        dept_txt = clean(re.sub(r"<[^>]+>", " ", dept.group(1))) if dept else ""
        tags = [clean(re.sub(r"<[^>]+>", "", t)) for t in re.findall(r'sch-tag">(.*?)</span>', b, re.S)]
        out.append(
            {
                "dwdm": dw.group(1) if dw else "",
                "dwmc": clean(name.group(1)),
                "schId": sch.group(1),
                "ssdm": dw.group(2) if dw else "",
                "dept": dept_txt,
                "tags": [t for t in tags if t],
            }
        )
    return out


def main() -> int:
    all_rows, empty_pages = [], 0
    for page in range(0, 60):
        start = page * 20
        url = f"https://yz.chsi.com.cn/sch/search.do?start={start}"
        html = get_text(url, referer="https://yz.chsi.com.cn/sch/")
        rows = parse_page(html)
        if not rows:
            empty_pages += 1
            if empty_pages >= 2:
                break
            continue
        all_rows.extend(rows)
        print(f"[sch] start={start} -> {len(rows)} (total {len(all_rows)})", flush=True)
    dedup: dict[tuple, dict] = {}
    for r in all_rows:
        dedup.setdefault((r["dwdm"], r["dwmc"]), r)
    out = list(dedup.values())
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"[sch] wrote {len(out)} schools -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
