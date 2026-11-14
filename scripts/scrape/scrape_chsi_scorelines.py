"""Scrape school-wide 复试基本分数线 tables published by 研招网 for 自划线 schools.

Source: 研招网 考研分数线 series, one article per school covering 2019-2023, with
one table per 学科门类. Each table row gives 年份/总分/单科线; the 门类 heading
trails its own table on these pages. Output: data/scorelines/scorelines_<year>.json,
one row per school x 学科门类 x year, with the article URL kept for provenance.
"""
from __future__ import annotations

import html as H
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import get_text  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "data", "scorelines")
INDEX = "https://yz.chsi.com.cn/kyzx/other/"

HEADING = re.compile(
    r"(\d{2})([\u4e00-\u9fa5]{2,6})门类\s*(?:[（(]\s*(学术学位|专业学位)\s*[)）])?"
)
TABLE = re.compile(r"(?is)<table.*?</table>")


def school_from_title(title: str) -> str:
    m = re.search(r"\|([^0-9|]{2,20}?)(?:\d{4})", title)
    if m:
        return m.group(1).strip()
    return title[:20]


def plain_of(html: str) -> str:
    return H.unescape(re.sub(r"(?is)<[^>]+>", " ", html))


def parse_article(html: str, url: str) -> list[dict]:
    title_m = re.search(r"(?is)<title>(.*?)</title>", html)
    title = re.sub(r"\s+", " ", title_m.group(1)).strip() if title_m else ""
    school = school_from_title(title)
    plain = plain_of(html)
    out: list[dict] = []
    last_label = [""]
    for m in TABLE.finditer(html):
        raw = m.group(0)
        rows = []
        for tr in re.findall(r"(?is)<tr.*?</tr>", raw):
            cells = [
                re.sub(r"\s+", " ", H.unescape(re.sub(r"<[^>]+>", "", c))).strip()
                for c in re.findall(r"(?is)<t[dh][^>]*>(.*?)</t[dh]>", tr)
            ]
            if any(cells):
                rows.append(cells)
        if not any(r and r[0].startswith("年份") for r in rows):
            continue
        # Each 门类 heading is rendered just above its own table, so the label is
        # the nearest heading before the table starts.
        here = len(plain_of(html[: m.start()]))
        label = ""
        for h in HEADING.finditer(plain):
            if h.start() > here:
                break
            label = f"{h.group(1)}{h.group(2)}门类{h.group(3) or ''}"
        for row in rows:
            if not row or row[0].startswith("年份"):
                continue
            ym = re.match(r"(\d{4})", row[0])
            if not ym:
                continue
            out.append(
                {
                    "学校": school,
                    "年份": int(ym.group(1)),
                    "学科门类": label,
                    "总分": row[1] if len(row) > 1 else "",
                    "单科满分100": row[2] if len(row) > 2 else "",
                    "单科满分>100": row[3] if len(row) > 3 else "",
                    "来源": "研招网考研分数线",
                    "来源网址": url,
                    "原文标题": title,
                }
            )
    return out


def discover_index() -> list[tuple[str, str]]:
    html = get_text(INDEX, retries=3)
    found = []
    for m in re.finditer(r'(?is)<a[^>]+href="([^"]+)"[^>]*>([^<]{2,40})</a>', html):
        label = m.group(2).strip()
        if "考研分数线|" in label and "2019-2023" in label:
            href = m.group(1)
            url = href if href.startswith("http") else "https://yz.chsi.com.cn" + href
            found.append((url, label))
    return found


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    links = discover_index()
    print(f"[line] index links: {len(links)}")
    by_year: dict[int, list[dict]] = {2023: [], 2024: [], 2025: []}
    for url, label in links:
        try:
            html = get_text(url, referer=INDEX, retries=3)
        except Exception as e:  # noqa: BLE001
            print(f"[line] {label} fail {e}")
            continue
        rows = parse_article(html, url)
        for r in rows:
            if r["年份"] in by_year:
                by_year[r["年份"]].append(r)
        print(f"[line] {label}: {len(rows)} rows, schools {len({x['学校'] for x in by_year[2023]})}")
    for year, rows in by_year.items():
        if not rows:
            continue
        p = os.path.join(OUT, f"scorelines_{year}.json")
        with open(p, "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, indent=1)
        print(f"[line] wrote {len(rows)} rows -> {p}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
