"""Generate the Flyway migration that imports the 2026 catalog into `programs`.

The table is the project's JSON aggregate (`id`, `owner_id`, `version`, `payload`,
timestamps). Each row keeps the fields CatalogService already reads (schoolName,
programName, major, region, year, difficulty, scoreLine, enrollment,
admissionRatio, sources, snapshotId) and adds the richer catalog fields, which the
service ignores but which make the record useful outside the API.

Ids are deterministic UUIDv5 from (school code, major code, department, study mode,
exam mode) so a re-run or a second import never duplicates a record.
"""
from __future__ import annotations

import json
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, "data")
NS = uuid.UUID("6f1c1a3e-6f4a-4f0e-9f7e-6a1b2c3d4e5f")
TAG = "2026"


def esc(s) -> str:
    return (str(s).replace("\\", "\\\\").replace("'", "''").replace("\n", " ").replace("\r", " ")
            if s is not None else "")


def payload_of(r: dict, rid: str) -> dict:
    return {
        "id": rid,
        "schoolName": r.get("院校名称"),
        "programName": f"{r.get('专业代码')} {r.get('专业名称')} · {r.get('院系所代码')} {r.get('院系所')}",
        "major": r.get("专业名称"),
        "region": r.get("所在省"),
        "year": int(r.get("招生年份") or 2026),
        "difficulty": {"T0": "top", "T1": "high", "T2": "medium", "T3": "low", "T4": "low"}.get(
            r.get("系统梯度", ""), "medium"),
        "scoreLine": r.get("复试总分线") if isinstance(r.get("复试总分线"), int) else None,
        "enrollment": r.get("招生计划（统考/不含推免）")
        if isinstance(r.get("招生计划（统考/不含推免）"), int) else None,
        "admissionRatio": None,
        "sources": [
            {
                "title": "中国研究生招生信息网《硕士专业目录》",
                "url": "https://yz.chsi.com.cn/zsml/",
                "publisher": "教育部学生服务与素质发展中心",
                "version": f"official-{r.get('招生年份')}-catalog",
                "publishedAt": None,
                "retrievedAt": "2026-11-10T00:00:00Z",
            }
        ],
        "snapshotId": rid,
        # extended, non-breaking fields
        "schoolCode": r.get("院校代码"),
        "province": r.get("所在省"),
        "majorCode": r.get("专业代码"),
        "discipline": r.get("学科门类"),
        "firstLevelDiscipline": r.get("一级学科"),
        "degreeType": r.get("学位类型"),
        "departmentCode": r.get("院系所代码"),
        "department": r.get("院系所"),
        "directions": r.get("研究方向"),
        "studyMode": r.get("学习方式"),
        "examMode": r.get("考试方式"),
        "enrollmentExam": r.get("招生计划（统考/不含推免）"),
        "enrollmentTotal": r.get("招生计划（合计）"),
        "recommendedExempt": r.get("推免人数"),
        "enrollmentBasis": r.get("计划口径"),
        "examSubjects": r.get("考试科目"),
        "retestScoreLine": None,
        "admissionRatio": None,
        "systemTier": r.get("系统梯度"),
        "schoolTags": r.get("院校标签"),
        "missingFields": r.get("数据缺失说明"),
        "truncated": r.get("抓取截断") == "是",
        "notes": r.get("专业备注"),
    }


def main() -> int:
    src = os.path.join(DATA, TAG, f"programs_{TAG}.json")
    if not os.path.exists(src):
        raise SystemExit(f"missing {src}")
    with open(src, encoding="utf-8") as f:
        rows = json.load(f)
    print(f"[sql] rows={len(rows)}")

    lines = [
        "-- 2026 硕士专业目录（研招网）导入。确定性 UUID：重复执行不会产生重复记录。",
        "-- 由 scripts/scrape/export_sql.py 生成，数据来源见文件末尾。",
        "SET NAMES utf8mb4;",
        "",
    ]
    seen_ids = set()
    for r in rows:
        key = f"{r.get('院校代码')}|{r.get('专业代码')}|{r.get('院系所代码')}|{r.get('学习方式')}|{r.get('考试方式')}"
        rid = str(uuid.uuid5(NS, key))
        if rid in seen_ids:
            continue
        seen_ids.add(rid)
        p = payload_of(r, rid)
        payload = json.dumps(p, ensure_ascii=False, separators=(",", ":"))
        lines.append(
            "INSERT INTO programs (id, owner_id, version, payload) SELECT "
            f"'{rid}', 'public', 1, '{esc(payload)}' "
            f"WHERE NOT EXISTS (SELECT 1 FROM programs WHERE id = '{rid}');"
        )
    lines.append("")
    lines.append(f"-- 共 {len(seen_ids)} 条；来源：研招网 硕士专业目录 https://yz.chsi.com.cn/zsml/")
    sql = "\n".join(lines) + "\n"

    for dialect in ("mysql", "h2"):
        dst = os.path.join(
            ROOT, "src", "main", "resources", "db", "migration", dialect,
            "V7__catalog_2026_from_chsi_batch2.sql",
        )
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write(sql)
        print(f"[sql] wrote {dst} ({len(sql)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
