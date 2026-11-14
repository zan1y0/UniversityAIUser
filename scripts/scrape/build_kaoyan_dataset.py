"""Normalize the kaoyan.cn crawl into the project's program-record shape.

Grain: 招生单位 x 年份 x 院系 x 专业 x 学习方式.
Sources merged per grain:
  * planListV2 -> 招生计划 (recruit_number), 学习方式, 考试方式, 学位类型
  * schoolScore -> 复试总分线 + 政治/外语/专业课一/专业课二
Records exist even when one side is missing; the missing side stays blank and the
reason is written to 数据缺失说明 (never guessed).

Output: data/2023_2026/programs_2023_2026.csv / .json + per-year counts
"""
from __future__ import annotations

import csv
import json
import os
import re
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(HERE, "_cache", "kaoyan_cn")
OUT = os.path.join(ROOT, "data", "2023_2026")

DIM_CSV = os.path.join(ROOT, "data", "2023_2025", "院校维度_全部招生单位.csv")


def load_jsonl(path: str) -> list[dict]:
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]


def norm_name(s: str) -> str:
    return re.sub(r"[\s（）()·]+", "", s or "")


def load_dimension() -> tuple[dict[str, dict], dict[int, dict]]:
    by_name, by_code = {}, {}
    if not os.path.exists(DIM_CSV):
        return by_name, by_code
    with open(DIM_CSV, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            by_name[norm_name(r["招生单位名称"])] = r
            by_code[r.get("招生单位代码")] = r
    return by_name, by_code


TOP9 = {"北京大学", "清华大学", "复旦大学", "上海交通大学", "浙江大学",
        "中国科学技术大学", "南京大学", "哈尔滨工业大学", "西安交通大学"}


def tier_from(school: dict) -> tuple[str, str]:
    name = school.get("name") or ""
    if school.get("is_985") == 1:
        return ("T0", "T0 顶尖985") if name in TOP9 else ("T1", "T1 985")
    if school.get("is_211") == 1:
        return "T2", "T2 211"
    if school.get("is_syl") == 1:
        return "T2", "T2 双一流"
    if school.get("is_zihuaxian") == 1:
        return "T1", "T1 自划线"
    if school.get("is_ordinary") == 2:
        return "T3", "T3 有研究生院"
    return "T4", "T4 其他"


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    schools = {s["school_id"]: s for s in load_jsonl(os.path.join(CACHE, "schools.jsonl"))}
    scores = load_jsonl(os.path.join(CACHE, "scores.jsonl"))
    plans = load_jsonl(os.path.join(CACHE, "plans.jsonl"))
    plans += load_jsonl(os.path.join(CACHE, "plans_v1.jsonl"))
    # planListV2 and planList(v1) overlap on recent years; keep one row per grain
    seen_plan, dedup_plans = set(), []
    for p in plans:
        k = (p.get("_school_id"), p.get("_year") or p.get("year"), p.get("depart_name"),
             str(p.get("special_code") or ""), p.get("recruit_type_name"))
        if k in seen_plan:
            continue
        seen_plan.add(k)
        dedup_plans.append(p)
    plans = dedup_plans
    print(f"[build] schools={len(schools)} scores={len(scores)} plans={len(plans)}")

    dim_by_name, _ = load_dimension()

    # index scores by (school_id, year, depart, code)
    score_idx: dict[tuple, list[dict]] = defaultdict(list)
    for r in scores:
        key = (r.get("_school_id"), r.get("year"), r.get("depart_name") or "", str(r.get("code") or ""))
        score_idx[key].append(r)
    # fallback index ignoring department (some score rows are school-level)
    score_by_major: dict[tuple, list[dict]] = defaultdict(list)
    for r in scores:
        score_by_major[(r.get("_school_id"), r.get("year"), str(r.get("code") or ""))].append(r)

    consumed: set[int] = set()
    out: list[dict] = []
    for p in plans:
        sid, year = p.get("_school_id"), p.get("_year") or p.get("year")
        code = str(p.get("special_code") or "")
        depart = p.get("depart_name") or ""
        key = (sid, year, depart, code)
        cand = score_idx.get(key) or score_by_major.get((sid, year, code)) or []
        sc = max(cand, key=lambda x: x.get("total") or 0) if cand else {}
        for c in cand:
            consumed.add(id(c))
        out.append(record(p, sc, schools.get(sid, {}), dim_by_name, "plan"))
    for r in scores:
        if id(r) in consumed:
            continue
        out.append(record({}, r, schools.get(r.get("_school_id"), {}), dim_by_name, "score"))
    print(f"[build] records={len(out)}")

    # dedupe on the full grain, keeping the first (plans win over bare score rows)
    seen, unique = set(), []
    for rec in out:
        k = (rec["院校名称"], rec["招生年份"], rec["院系所"], rec["专业代码"], rec["学习方式"])
        if k in seen:
            continue
        seen.add(k)
        unique.append(rec)
    print(f"[build] unique={len(unique)}")

    cols = list(unique[0].keys())
    csv_path = os.path.join(OUT, "programs_2023_2026.csv")
    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        w.writerows(unique)
    json_path = os.path.join(OUT, "programs_2023_2026.jsonl")
    with open(json_path, "w", encoding="utf-8") as f:
        for rec in unique:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    by_year = {}
    for r in unique:
        by_year[r["招生年份"]] = by_year.get(r["招生年份"], 0) + 1
    meta = {
        "records": len(unique),
        "byYear": dict(sorted(by_year.items())),
        "schools": len({r["院校名称"] for r in unique}),
        "majors": len({r["专业代码"] for r in unique}),
        "withPlan": sum(1 for r in unique if r["招生计划"] != ""),
        "withScore": sum(1 for r in unique if r["复试总分线"] != ""),
        "csv": csv_path,
    }
    with open(os.path.join(OUT, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print("[build]", json.dumps(meta, ensure_ascii=False))
    return 0


def record(plan: dict, score: dict, school: dict, dim: dict[str, dict], kind: str) -> dict:
    name = plan.get("_school_name") or score.get("_school_name") or school.get("name") or ""
    d = dim.get(norm_name(name), {})
    tags = []
    if d.get("是否985") == "是" or school.get("is_985") == 1:
        tags.append("985")
    if school.get("is_211") == 1:
        tags.append("211")
    if school.get("is_syl") == 1 or "“双一流”建设高校" in (d.get("院校标签") or ""):
        tags.append("双一流")
    if school.get("is_zihuaxian") == 1 or "自划线" in (d.get("院校标签") or ""):
        tags.append("自划线")
    # kaoyan.cn flags are authoritative for 985/211/双一流; the 研招网 dimension is
    # only a fallback, because a name mismatch there silently degrades everything
    # to T4.
    grade, grade_label = tier_from(school)
    if grade == "T4" and d.get("系统梯度") in ("T0", "T1", "T2", "T3"):
        grade, grade_label = d["系统梯度"], d.get("梯度说明") or grade_label
    year = plan.get("_year") or plan.get("year") or score.get("_year") or score.get("year")
    missing = []
    if plan.get("recruit_number") in (None, ""):
        missing.append("招生计划未收录")
    if score.get("total") in (None, ""):
        missing.append("复试线未收录")
    missing.append("报录比官方未公开")

    return {
        "招生年份": int(year) if year else "",
        "院校代码": d.get("招生单位代码") or "",
        "院校名称": name,
        "院校标签": "/".join(tags),
        "系统梯度": grade,
        "梯度说明": grade_label,
        "所在省": school.get("province") or d.get("所在省") or "",
        "学位类型": {
            "学术型硕士": "学术学位", "专业型硕士": "专业学位",
            "学术学位": "学术学位", "专业学位": "专业学位",
        }.get(plan.get("degree_type_name") or "", "")
        or ("学术学位" if score.get("degree_type") == 2 else "专业学位" if score.get("degree_type") == 1 else ""),
        "学科门类": plan.get("level1_name") or "",
        "一级学科": plan.get("level2_name") or "",
        "专业代码": str(plan.get("special_code") or score.get("code") or ""),
        "专业名称": plan.get("special_name") or score.get("name") or "",
        "院系所": plan.get("depart_name") or score.get("depart_name") or "",
        "学习方式": plan.get("recruit_type_name") or "",
        "考试方式": plan.get("exam_class_name") or "",
        "招生计划": plan.get("recruit_number") if plan.get("recruit_number") is not None else "",
        "复试总分线": score.get("total") if score.get("total") is not None else "",
        "政治": score.get("politics") if score.get("politics") is not None else "",
        "外语": score.get("english") if score.get("english") is not None else "",
        "专业课一": score.get("special_one") if score.get("special_one") is not None else "",
        "专业课二": score.get("special_two") if score.get("special_two") is not None else "",
        "报录比": "",
        "数据缺失说明": "；".join(missing),
        "数据来源": "掌上考研/中国教育在线（院校官方数据汇总）",
        "来源网址": f"https://www.kaoyan.cn/school/{school.get('school_id', '')}",
        "招生官网": (school.get("school_site") or [""])[-1] if school.get("school_site") else "",
        "备注": (plan.get("remark") or score.get("note") or "").strip(),
    }


if __name__ == "__main__":
    raise SystemExit(main())
