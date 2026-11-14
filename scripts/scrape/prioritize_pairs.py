"""Rank 专业 x 院校 pairs so a rate-limited crawl spends its budget where it matters.

Priority is popularity of the 专业 (how many schools list it in the catalog = how
widely it is offered) combined with school tier. The crawl budget is a hard cap on
requests, so this ordering decides which records exist after a partial run.
"""
from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "_cache", "2026")

POPULAR = {
    "081200": 100, "085400": 100, "085404": 98, "081000": 90, "081100": 85,
    "085401": 95, "083500": 92, "085405": 90, "0812J1": 80, "140500": 70,
    "025100": 95, "025200": 85, "125300": 90, "125100": 88, "120200": 80,
    "030100": 88, "035101": 92, "035102": 90, "030500": 70, "040100": 80,
    "045101": 75, "045400": 78, "050100": 70, "055200": 82, "050201": 72,
    "100201": 85, "105101": 90, "105200": 70, "100210": 78, "100401": 65,
    "070100": 78, "070200": 65, "071000": 70, "080200": 82, "080500": 70,
    "080800": 72, "085500": 70, "085600": 75, "085700": 80, "085800": 72,
    "085900": 70, "086000": 68, "081400": 78, "081500": 72, "082300": 60,
    "120100": 65, "120400": 75, "020200": 76, "020100": 70, "060100": 60,
    "130100": 55, "135100": 55, "040300": 68, "045200": 65, "050300": 70,
    "080900": 68, "080300": 62, "080400": 60, "080700": 58, "081700": 65,
}
TIER = {"T0": 100, "T1": 88, "T2": 74, "T3": 55, "T4": 40}
TOP = {"10001", "10003", "10246", "10248", "10335", "10358", "10284",
       "10213", "10698", "10006", "10007", "10002", "10247", "10286"}


def main() -> int:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 250
    pairs = [json.loads(x) for x in open(os.path.join(CACHE, "pairs.jsonl"), encoding="utf-8") if x.strip()]
    # how many schools each major lists -> proxy for how widely it is offered
    offered: dict[str, int] = {}
    for p in pairs:
        offered[p["zydm"]] = max(offered.get(p["zydm"], 0), int(p.get("majorTotal") or 0))

    done = set()
    dp = os.path.join(CACHE, "rows_done.txt")
    if os.path.exists(dp):
        with open(dp, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}

    def score(p: dict) -> float:
        z = p["zydm"]
        pop = POPULAR.get(z, 30) + min(offered.get(z, 0), 60) * 0.5
        tier = TIER.get(
            "T0" if p["dwdm"] in TOP else
            ("T1" if p.get("zhx") == "1" else ("T2" if p.get("syl") == "1" else "T3")),
            50,
        )
        return pop + tier

    todo = [p for p in pairs if f"{p['dwdm']}:{p['zydm']}:{p['xwlx']}" not in done]
    todo.sort(key=score, reverse=True)
    chosen = todo[:limit]
    dst = os.path.join(CACHE, "pairs_priority.jsonl")
    with open(dst, "w", encoding="utf-8") as f:
        for p in chosen:
            f.write(json.dumps(p, ensure_ascii=False) + "\n")
    print(f"[priority] total={len(pairs)} done={len(done)} todo={len(todo)} chosen={len(chosen)} -> {dst}")
    print("[priority] top:", [(p["dwdm"], p["zydm"], p["zymc"]) for p in chosen[:5]])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
