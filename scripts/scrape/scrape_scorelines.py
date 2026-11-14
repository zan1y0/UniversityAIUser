"""Collect 2023-2025 复试分数线 (re-examination cutoffs) from school sites.

Only 自划线 (self-determining) institutions publish a machine-readable school-wide
cutoff table every year; other schools publish 院系-level notices or PDFs that do
not share a layout. This module therefore targets the 34 自划线 schools and keeps
the provenance of every row (page URL + title captured from the page).

Output: scripts/scrape/_cache/scorelines/<school>.json with the candidate tables
as normalized rows; a human review pass marks the table that is really the 分数线.
"""
from __future__ import annotations

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import get_text  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "_cache", "scorelines")

SELF_LINE = [
    ("北京大学", "https://admission.pku.edu.cn/"),
    ("清华大学", "https://yz.tsinghua.edu.cn/"),
    ("中国人民大学", "https://pgs.ruc.edu.cn/"),
    ("北京航空航天大学", "https://yzb.buaa.edu.cn/"),
    ("北京理工大学", "https://grd.bit.edu.cn/"),
    ("中国农业大学", "https://yz.cau.edu.cn/"),
    ("北京师范大学", "https://yz.bnu.edu.cn/"),
    ("南开大学", "https://yzb.nankai.edu.cn/"),
    ("天津大学", "https://yzb.tju.edu.cn/"),
    ("大连理工大学", "https://gs.dlut.edu.cn/"),
    ("东北大学", "https://yz.neu.edu.cn/"),
    ("吉林大学", "https://zsb.jlu.edu.cn/"),
    ("哈尔滨工业大学", "https://yzb.hit.edu.cn/"),
    ("复旦大学", "https://gsao.fudan.edu.cn/"),
    ("同济大学", "https://yz.tongji.edu.cn/"),
    ("上海交通大学", "https://yzb.sjtu.edu.cn/"),
    ("南京大学", "https://grawww.nju.edu.cn/"),
    ("东南大学", "https://yzb.seu.edu.cn/"),
    ("浙江大学", "https://grs.zju.edu.cn/"),
    ("中国科学技术大学", "https://yz.ustc.edu.cn/"),
    ("厦门大学", "https://zs.xmu.edu.cn/"),
    ("山东大学", "https://www.yz.sdu.edu.cn/"),
    ("武汉大学", "https://gs.whu.edu.cn/"),
    ("华中科技大学", "https://gszs.hust.edu.cn/"),
    ("湖南大学", "https://gra.hnu.edu.cn/"),
    ("中南大学", "https://gra.csu.edu.cn/"),
    ("中山大学", "https://graduate.sysu.edu.cn/"),
    ("华南理工大学", "https://yz.scut.edu.cn/"),
    ("四川大学", "https://gs.scu.edu.cn/"),
    ("重庆大学", "https://yz.cqu.edu.cn/"),
    ("电子科技大学", "https://yz.uestc.edu.cn/"),
    ("西安交通大学", "https://yz.xjtu.edu.cn/"),
    ("西北工业大学", "https://yzb.nwpu.edu.cn/"),
    ("兰州大学", "https://yz.lzu.edu.cn/"),
]


def text_of(html: str) -> str:
    """Visible-ish text with tags stripped, whitespace collapsed."""
    body = re.sub(r"(?is)<(script|style).*?</\1>", " ", html)
    body = re.sub(r"(?s)<[^>]+>", "\n", body)
    return re.sub(r"[ \t\r\f\v]+", " ", body)


def find_links(html: str, base: str, keyword: str) -> list[tuple[str, str]]:
    """(absolute url, anchor text) for <a> tags whose text or href matches keyword."""
    out = []
    for m in re.finditer(r'(?is)<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', html):
        href, label = m.group(1), re.sub(r"<[^>]+>", "", m.group(2))
        label = re.sub(r"\s+", " ", label).strip()
        if keyword in label or keyword in href:
            if href.startswith("http"):
                url = href
            elif href.startswith("/"):
                url = re.match(r"https?://[^/]+", base).group(0) + href
            else:
                url = base.rstrip("/") + "/" + href
            out.append((url, label))
    return out


def tables(html: str) -> list[list[list[str]]]:
    """Parse <table> blocks into rows of cell text, keeping header rows."""
    import html as htmllib

    out = []
    for t in re.findall(r"(?is)<table.*?</table>", html):
        rows = []
        for tr in re.findall(r"(?is)<tr.*?</tr>", t):
            cells = [
                re.sub(r"\s+", " ", htmllib.unescape(re.sub(r"<[^>]+>", "", c))).strip()
                for c in re.findall(r"(?is)<t[dh][^>]*>(.*?)</t[dh]>", tr)
            ]
            if any(cells):
                rows.append(cells)
        if len(rows) >= 2:
            out.append(rows)
    return out


def score_like(table: list[list[str]]) -> bool:
    flat = " ".join(" ".join(r) for r in table)
    return ("复试" in flat or "分数线" in flat) and bool(re.search(r"\b(2[5-9]\d|3[0-9]\d)\b", flat))


def probe_school(name: str, home: str) -> dict:
    rec = {"school": name, "home": home, "fetchedAt": None, "pages": []}
    try:
        html = get_text(home, retries=2)
    except Exception as e:  # noqa: BLE001
        rec["error"] = f"home: {e}"
        return rec
    cand = find_links(html, home, "分数线") + find_links(html, home, "复试")
    seen = set()
    for url, label in cand[:8]:
        if url in seen:
            continue
        seen.add(url)
        try:
            page = get_text(url, referer=home, retries=2)
        except Exception as e:  # noqa: BLE001
            rec["pages"].append({"url": url, "label": label, "error": str(e)[:120]})
            continue
        title = re.search(r"(?is)<title>(.*?)</title>", page)
        tabs = [t for t in tables(page) if score_like(t)]
        rec["pages"].append(
            {
                "url": url,
                "label": label,
                "title": re.sub(r"\s+", " ", title.group(1)).strip() if title else "",
                "tables": len(tabs),
                "sample": tabs[0][:6] if tabs else [],
            }
        )
    return rec


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    only = sys.argv[1:] or None
    for name, home in SELF_LINE:
        if only and name not in only:
            continue
        dst = os.path.join(OUT, f"{name}.json")
        if os.path.exists(dst):
            print(f"[line] skip {name}")
            continue
        rec = probe_school(name, home)
        with open(dst, "w", encoding="utf-8") as f:
            json.dump(rec, f, ensure_ascii=False, indent=1)
        print(f"[line] {name}: pages={len(rec['pages'])} err={rec.get('error', '')}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
