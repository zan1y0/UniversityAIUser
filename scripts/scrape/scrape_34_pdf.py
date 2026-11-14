"""Collect 2023-2025 自划线院校复试基本分数线 from 研招网 34所 pages.

2024 and 2025 articles publish the table as a PDF attachment (2023 sits in the
2019-2023 HTML series handled by scrape_chsi_scorelines.py). This module finds the
PDF link on each school-year page, extracts its text with pypdf, and turns rows of
"学科门类 总分 单科(满分=100) 单科(满分>100)" into records.

Output: data/scorelines/scorelines_<year>_34.json  (append-safe, one file per year)
"""
from __future__ import annotations

import io
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import get_bytes, get_text  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "data", "scorelines")
TOPIC = "https://yz.chsi.com.cn/kyzx/zt/kyfs.shtml"

UA_HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126"}


def zhx_list() -> list[tuple[str, str, str]]:
    """(学校, 年份, 文章 URL) from the topic page's inline zhxList array."""
    html = get_text(TOPIC, retries=3)
    start = html.find("var zhxList = [")
    if start < 0:
        raise RuntimeError("zhxList not found on topic page")
    end = html.find("];", start)
    block = html[start:end]
    # one entry per school; whitespace inside the object is normal, so split on
    # the school-name key rather than on "},{", which would cut each year item out.
    out = []
    for chunk in re.split(r"yxmc:\s*'", block)[1:]:
        name = chunk.split("'", 1)[0]
        for ym in re.finditer(r"year:\s*'(\d{4})'\s*,\s*url:\s*'([^']+)'", chunk):
            out.append((name, ym.group(1), ym.group(2)))
    return out


def pdf_url(article_html: str, page_url: str) -> str | None:
    for m in re.finditer(r"""href=['"]([^'"]+\.pdf)['"]""", article_html, re.I):
        href = m.group(1)
        if href.startswith("http"):
            return href
        if href.startswith("/"):
            return "https://yz.chsi.com.cn" + href
        return page_url.rsplit("/", 1)[0] + "/" + href
    return None


def parse_pdf_text(text: str) -> list[dict]:
    """Parse '01 哲 学 50 50 90 90 345' style rows.

    The 总分 column position differs per school (some list 政治/外语/专业课1/
    专业课2 then 总分, some omit a column), so take the last plausible score as
    总分 and the first two small numbers as the 100-point subject minimums.
    """
    lines = [re.sub(r"\s+", " ", x).strip() for x in text.splitlines()]
    lines = [x for x in lines if x]
    out = []
    for line in lines:
        m = re.match(r"^(\d{2})\s*([\u4e00-\u9fa5（）()、·\s]{2,20}?)\s+((?:\d{1,3}\s+)+)(.*)$", line)
        if not m:
            continue
        code, name, nums, tail = m.group(1), m.group(2), m.group(3), m.group(4)
        values = [int(x) for x in nums.split()]
        total = next((v for v in reversed(values) if 200 <= v <= 500), None)
        if total is None:
            continue
        singles = [v for v in values if v != total and v <= 120]
        out.append(
            {
                "学科门类": f"{code}{name.strip()}",
                "总分": total,
                "单科满分100": "/".join(str(v) for v in singles[:2]),
                "单科满分>100": str(max([v for v in values if v != total], default="")),
            }
        )
    return out


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    years = sys.argv[1:] or ["2024", "2025"]
    entries = [e for e in zhx_list() if e[1] in years]
    print(f"[34] entries={len(entries)} years={years}")

    from pypdf import PdfReader

    by_year: dict[str, list[dict]] = {y: [] for y in years}
    for school, year, url in entries:
        try:
            page = get_text(url, referer=TOPIC, retries=3)
        except Exception as e:  # noqa: BLE001
            print(f"[34] page fail {school} {year}: {e}")
            continue
        pdf = pdf_url(page, url)
        if not pdf:
            # some years keep an HTML table instead of a PDF
            print(f"[34] no pdf {school} {year}")
            continue
        try:
            data = get_bytes(pdf, referer=url, retries=3)
            reader = PdfReader(io.BytesIO(data))
            text = "\n".join((p.extract_text() or "") for p in reader.pages)
        except Exception as e:  # noqa: BLE001
            print(f"[34] pdf fail {school} {year}: {e}")
            continue
        rows = parse_pdf_text(text)
        for r in rows:
            by_year[year].append(
                {
                    "学校": school,
                    "年份": int(year),
                    "学科门类": r["学科门类"],
                    "总分": r["总分"],
                    "单科满分100": r["单科满分100"],
                    "单科满分>100": r["单科满分>100"],
                    "来源": "研招网34所自划线院校复试分数线",
                    "来源网址": url,
                    "附件": pdf,
                }
            )
        print(f"[34] {school} {year}: {len(rows)} rows", flush=True)
        time.sleep(0.2)

    for year, rows in by_year.items():
        if not rows:
            continue
        p = os.path.join(OUT, f"scorelines_{year}_34.json")
        with open(p, "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, indent=1)
        print(f"[34] wrote {len(rows)} rows -> {p} ({len({r['学校'] for r in rows})} schools)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
