"""Validate the exported dataset and print a machine-readable report.

Checks that would have caught the classes of error found while building this
pipeline: silent truncation, duplicated records, plan values that cannot be a
count, score lines outside the plausible range, and rows missing provenance.
Exit code 1 when a hard check fails.
"""
from __future__ import annotations

import json
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, "data")
TAG = "2026"

REQUIRED = ["招生年份", "院校代码", "院校名称", "专业代码", "专业名称", "院系所", "学习方式", "数据来源", "来源网址"]
HARD = []


def check(cond: bool, msg: str, hard: bool = True):
    if not cond:
        (HARD if hard else []).append(msg)
        print(f"[{'FAIL' if hard else 'WARN'}] {msg}")


def main() -> int:
    p = os.path.join(DATA, TAG, f"programs_{TAG}.json")
    if not os.path.exists(p):
        print(f"[FAIL] missing {p}")
        return 1
    with open(p, encoding="utf-8") as f:
        rows = json.load(f)
    print(f"[info] records={len(rows)}")

    check(len(rows) > 0, "dataset is empty")
    missing_req = Counter()
    for r in rows:
        for k in REQUIRED:
            if r.get(k) in (None, ""):
                missing_req[k] += 1
    for k, n in missing_req.items():
        check(False, f"{n} rows missing required field {k}")

    keys = Counter(
        (r.get("院校代码"), r.get("专业代码"), r.get("院系所代码"), r.get("学习方式"), r.get("考试方式"))
        for r in rows
    )
    dup = [k for k, n in keys.items() if n > 1]
    check(not dup, f"{len(dup)} duplicated records, e.g. {dup[:3]}")

    years = {r.get("招生年份") for r in rows}
    check(years == {2026}, f"unexpected years: {years}")

    bad_plan = [
        r for r in rows
        if r.get("招生计划（统考/不含推免）") not in ("", None)
        and (not isinstance(r["招生计划（统考/不含推免）"], int) or r["招生计划（统考/不含推免）"] < 0
             or r["招生计划（统考/不含推免）"] > 1000)
    ]
    check(not bad_plan, f"{len(bad_plan)} implausible plan values, e.g. {bad_plan[:2]}")

    with_plan = sum(1 for r in rows if isinstance(r.get("招生计划（统考/不含推免）"), int))
    with_exam = sum(1 for r in rows if r.get("考试科目"))
    with_retest = sum(1 for r in rows if isinstance(r.get("复试总分线"), int))
    print(f"[stat] plan={with_plan} examSubjects={with_exam} retestLine={with_retest}")

    # cross-check that a score line, when present, sits in a plausible band
    bad_line = [r for r in rows if isinstance(r.get("复试总分线"), int)
                and not (200 <= r["复试总分线"] <= 450)]
    check(not bad_line, f"{len(bad_line)} implausible score lines")

    schools = {r.get("院校名称") for r in rows}
    majors = {r.get("专业代码") for r in rows}
    print(f"[stat] schools={len(schools)} majors={len(majors)}")

    report = {
        "records": len(rows),
        "schools": len(schools),
        "majors": len(majors),
        "withPlan": with_plan,
        "withExamSubjects": with_exam,
        "withRetestLine": with_retest,
        "hardFailures": HARD,
        "ok": not HARD,
    }
    dst = os.path.join(DATA, TAG, "validation.json")
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("[validate] " + json.dumps(report, ensure_ascii=False))
    return 0 if not HARD else 1


if __name__ == "__main__":
    sys.exit(main())
