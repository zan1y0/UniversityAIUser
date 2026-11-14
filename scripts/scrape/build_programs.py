"""Turn cached 研招网 catalog rows into the normalized admission dataset.

Input  : scripts/scrape/_cache/<tag>/rows.jsonl (raw catalog rows)
Output : data/<tag>/programs_<tag>.csv  + programs_<tag>.json

Grain of one output record: 招生单位 x 专业 x 院系 x 学习方式 x 考试方式.
Reason: 研招网 repeats the same 专业-level plan ("专业：19(不含推免)") on every
研究方向 row of that 院系, so summing rows would multiply the plan. The builder
dedupes on that grain and keeps all 研究方向 as one semicolon-joined value.

Fields that 研招网 does not publish are left empty and flagged in `数据缺失说明`:
   复试线  -> 省/校/院线来自院校官网，另表收录
   报录比  -> 绝大多数院校不公开报考人数
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

# ---- 院校梯度规则（系统梯度）------------------------------------------------
TOP985 = {
    "北京大学", "清华大学", "复旦大学", "上海交通大学", "浙江大学",
    "中国科学技术大学", "南京大学", "哈尔滨工业大学", "西安交通大学",
}
STRONG985 = {
    "北京航空航天大学", "北京理工大学", "中国人民大学", "同济大学", "东南大学",
    "武汉大学", "华中科技大学", "中山大学", "四川大学", "南开大学", "天津大学",
    "厦门大学", "山东大学", "吉林大学", "中南大学", "湖南大学", "重庆大学",
    "大连理工大学", "西北工业大学", "华南理工大学", "电子科技大学",
    "兰州大学", "东北大学", "中国农业大学", "北京师范大学", "华东师范大学",
}


def tier(row: dict) -> tuple[str, str]:
    """Return (gradient code, human label) from official school attributes."""
    name = row.get("dwmc") or ""
    tags = []
    if row.get("_zhx") == "1":
        tags.append("自划线")
    if row.get("b985") == "1":
        tags.append("985")
    if row.get("b211") == "1":
        tags.append("211")
    if row.get("_syl") == "1":
        tags.append("双一流")
    if name in TOP985:
        return "T0", "T0 顶尖985（自划线）"
    if "985" in tags:
        return ("T1", "T1 强势985（自划线）") if "自划线" in tags else ("T1", "T1 985")
    if "211" in tags:
        return "T2", "T2 211/双一流学科"
    if "双一流" in tags:
        return "T2", "T2 双一流学科"
    if "自划线" in tags:
        return "T1", "T1 自划线"
    if row.get("bs") == "1":
        return "T3", "T3 有博士点（普通院校）"
    return "T4", "T4 普通院校"


PLAN_RE = re.compile(r"(\d+)")


def parse_plan(text: str | None) -> tuple[int | None, str]:
    """'专业：19(不含推免)' -> (19, '不含推免'); '院系所：5' -> (5, '')."""
    if not text:
        return None, ""
    m = PLAN_RE.search(text)
    if not m:
        return None, ""
    note = ""
    if "不含推免" in text:
        note = "不含推免"
    elif "含推免" in text:
        note = "含推免"
    if text.startswith("院系所"):
        note = (note + ";院系所合计").strip(";")
    return int(m.group(1)), note


def exam_text(kskmz) -> str:
    if not kskmz:
        return ""
    parts = []
    for item in kskmz:
        if not isinstance(item, dict):
            continue
        for k in ("km1Vo", "km2Vo", "km3Vo", "km4Vo"):
            v = item.get(k)
            if isinstance(v, dict) and v.get("kskmmc"):
                parts.append(f"{v.get('kskmdm', '')} {v['kskmmc']}".strip())
    # keep first set (the four subjects), dedupe
    out, seen = [], set()
    for p in parts:
        if p not in seen:
            seen.add(p)
            out.append(p)
    return " | ".join(out[:4])


BOOLEAN_FIELDS = {
    "tydxs": "退役大学生士兵专项",
    "jsggjh": "少数民族高层次骨干计划",
    "jsxbjh": "援藏计划",
}


def build(tag: str) -> dict:
    cache = os.path.join(HERE, "_cache", tag)
    rows_path = os.path.join(cache, "rows.jsonl")
    if not os.path.exists(rows_path):
        raise SystemExit(f"missing {rows_path}; run chsi_catalog.py --stage rows first")
    with open(rows_path, encoding="utf-8") as f:
        rows = [json.loads(x) for x in f if x.strip()]
    print(f"[build] raw rows: {len(rows)}")

    # 专业代码 -> whether the catalog lists every school for it (a major whose
    # school list exceeded one page was only sampled, so 专业是否完整 is "否")
    major_truncated: dict[str, bool] = {}
    mp = os.path.join(cache, "majors_truncated_total.json")
    if os.path.exists(mp):
        with open(mp, encoding="utf-8") as f:
            for item in json.load(f):
                major_truncated[str(item.get("zydm"))] = bool(item.get("truncated"))
    pairs_path = os.path.join(cache, "pairs.jsonl")
    if not major_truncated and os.path.exists(pairs_path):
        for p in (json.loads(x) for x in open(pairs_path, encoding="utf-8") if x.strip()):
            z = str(p.get("zydm"))
            if (p.get("majorTotal") or 0) > 10:
                major_truncated[z] = True

    groups: "OrderedDict[tuple, dict]" = OrderedDict()
    for r in rows:
        key = (
            r.get("_dwdm"),
            r.get("zydm"),
            r.get("yxsdm"),
            r.get("xxfs"),
            r.get("ksfsdm"),
        )
        g = groups.get(key)
        if g is None:
            g = groups[key] = {
                "row": r,
                "directions": [],
                "exams": "",
                "truncated": bool(r.get("_truncated")),
                "wantTotal": r.get("_total"),
            }
        d = (r.get("yjfxdm") or "", r.get("yjfxmc") or "")
        if d[1] and d[1] not in [x[1] for x in g["directions"]]:
            g["directions"].append(d)
        if not g["exams"]:
            g["exams"] = exam_text(r.get("kskmz"))
        # 院系-level plan text appears on every direction row; keep the widest
        if not g["row"].get("nzsrsstr") and r.get("nzsrsstr"):
            g["row"]["nzsrsstr"] = r["nzsrsstr"]

    print(f"[build] deduped records: {len(groups)}")
    out_rows = []
    stats = {"plan": 0, "plan_total": 0, "tuimian": 0, "exam": 0}
    for (dwdm, zydm, yxsdm, xxfs, ksfs), g in groups.items():
        r = g["row"]
        # 研招网 semantics: nzsrs is the 专业-level plan INCLUDING 推免, since
        # nzsrsstr reads "专业：65(含推免55)" / "研究方向：10(不含推免)"; ssjstmrs is
        # the 推免 share. Keep the original text in 计划口径 and derive both counts.
        raw_total = r.get("nzsrs")
        raw_total = raw_total if isinstance(raw_total, int) else None
        tuimian = r.get("ssjstmrs")
        tuimian = tuimian if isinstance(tuimian, int) else None
        plan_text = r.get("nzsrsstr") or ""
        plan_from_text, plan_note = parse_plan(plan_text)
        if raw_total is not None:
            total_plan = raw_total
            exam_plan = raw_total - (tuimian or 0)
            plan = exam_plan if exam_plan >= 0 else None
        else:
            plan = plan_from_text
            total_plan = (plan + tuimian) if (plan is not None and tuimian is not None) else None
        grade, grade_label = tier(r)
        directions = "；".join(x[1] for x in g["directions"] if x[1]) or "不区分研究方向"
        rec = {
            "招生年份": int(tag) if tag.isdigit() else tag,
            "院校代码": r.get("_dwdm") or "",
            "院校名称": r.get("_dwmc") or "",
            "院校层级梯度": grade,
            "梯度说明": grade_label,
            "院校标签": "/".join(
                [t for t, on in [("自划线", r.get("_zhx") == "1"), ("985", r.get("b985") == "1"),
                                 ("211", r.get("b211") == "1"), ("双一流", r.get("_syl") == "1"),
                                 ("博士点", r.get("bs") == "1")] if on]
            ),
            "所在省": r.get("_ssmc") or "",
            "学科门类": f"{r.get('_mldm') or ''} {r.get('_mlmc') or ''}".strip(),
            "一级学科代码": r.get("_yjxkdm") or "",
            "一级学科": r.get("yjxkmc") or "",
            "专业代码": zydm,
            "专业名称": r.get("zymc") or "",
            "学位类型": "学术学位" if r.get("_xwlx") == "xs" else "专业学位",
            "院系所代码": yxsdm or "",
            "院系所": r.get("yxsmc") or "",
            "研究方向": directions,
            "学习方式": "全日制" if str(r.get("xxfs")) == "1" else ("非全日制" if str(r.get("xxfs")) == "2" else (r.get("xxfs") or "")),
            "考试方式": r.get("ksfsmc") or "",
            "招生计划（统考/不含推免）": plan if plan is not None else "",
            "推免人数": tuimian if tuimian is not None else "",
            "招生计划（合计）": total_plan if total_plan is not None else "",
            "计划口径": plan_text or plan_note,
            "考试科目": g["exams"],
            "复试总分线": "",
            "报录比": "",
            "系统梯度": grade,
            "数据缺失说明": "",
            "数据来源": "研招网《硕士专业目录》",
            "来源网址": "https://yz.chsi.com.cn/zsml/",
            "采集时间": r.get("_retrievedAt") or "",
            "抓取截断": "是" if g["truncated"] else "否",
            "专业是否完整": "否（该专业院校清单超过10所，仅取前10所）" if major_truncated.get(zydm) else "是",
            "专业备注": (r.get("zybz") or "").replace("\n", " ").strip(),
        }
        missing = []
        if not rec["复试总分线"]:
            missing.append("复试线未收录(见复试线表)")
        if not rec["报录比"]:
            missing.append("报录比官方未公开")
        if rec["招生计划（统考/不含推免）"] == "":
            missing.append("招生计划未公布")
        elif rec["招生计划（统考/不含推免）"] == 0:
            missing.append("统考计划为0，名额可能全部由推免占满")
        rec["数据缺失说明"] = "；".join(missing)
        if plan is not None:
            stats["plan"] += 1
        if total_plan is not None:
            stats["plan_total"] += 1
        if tuimian is not None:
            stats["tuimian"] += 1
        if g["exams"]:
            stats["exam"] += 1
        out_rows.append(rec)

    out_dir = os.path.join(ROOT, "data", tag)
    os.makedirs(out_dir, exist_ok=True)
    csv_path = os.path.join(out_dir, f"programs_{tag}.csv")
    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()))
        w.writeheader()
        w.writerows(out_rows)
    json_path = os.path.join(out_dir, f"programs_{tag}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(out_rows, f, ensure_ascii=False, indent=1)
    meta = {
        "tag": tag,
        "records": len(out_rows),
        "rawRows": len(rows),
        "withPlan": stats["plan"],
        "withTotalPlan": stats["plan_total"],
        "withTuimian": stats["tuimian"],
        "withExam": stats["exam"],
        "csv": os.path.relpath(csv_path, ROOT),
        "builtAt": __import__("time").strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    with open(os.path.join(out_dir, f"programs_{tag}.meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print("[build]", json.dumps(meta, ensure_ascii=False))
    return meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="2026")
    a = ap.parse_args()
    build(a.tag)


if __name__ == "__main__":
    main()
