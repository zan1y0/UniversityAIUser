"""Build the 2023-2025 school-level deliverable (dimension + verified cutoffs).

What is included, and why only this:
  * 院校维度 — every 招生单位 in the 研招网 院校库 (936), with province, 双一流/
    自划线/研究生院 attributes and a documented T0-T4 tier rule.
  * 2023 自划线校线 — parsed from 研招网 HTML tables (2019-2023 合辑), verified.
  * 2024/2025 校线 — 研招网 publishes these as images (2024) and PDFs whose table
    layout varies per school (2025); they are NOT included here rather than
    guessed. The gap is stated in the workbook and the markdown note.

Output: data/2023_2025/院校维度.csv, 自划线校线_2023.csv, 交付说明.xlsx
"""
from __future__ import annotations

import json
import os

import pandas as pd
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, "data")
OUT = os.path.join(DATA, "2023_2025")

PROV = {
    "11": "北京", "12": "天津", "13": "河北", "14": "山西", "15": "内蒙古",
    "21": "辽宁", "22": "吉林", "23": "黑龙江", "31": "上海", "32": "江苏",
    "33": "浙江", "34": "安徽", "35": "福建", "36": "江西", "37": "山东",
    "41": "河南", "42": "湖北", "43": "湖南", "44": "广东", "45": "广西",
    "46": "海南", "50": "重庆", "51": "四川", "52": "贵州", "53": "云南",
    "54": "西藏", "61": "陕西", "62": "甘肃", "63": "青海", "64": "宁夏",
    "65": "新疆",
}
B_ZONE = {"15", "45", "46", "52", "53", "54", "62", "63", "64", "65"}

C985 = {
    "北京大学", "清华大学", "中国人民大学", "北京航空航天大学", "北京理工大学",
    "北京师范大学", "中国农业大学", "中央民族大学", "南开大学", "天津大学",
    "大连理工大学", "东北大学", "吉林大学", "哈尔滨工业大学", "复旦大学",
    "同济大学", "上海交通大学", "华东师范大学", "南京大学", "东南大学",
    "浙江大学", "中国科学技术大学", "厦门大学", "山东大学", "中国海洋大学",
    "武汉大学", "华中科技大学", "湖南大学", "中南大学", "中山大学",
    "华南理工大学", "四川大学", "电子科技大学", "重庆大学", "西安交通大学",
    "西北工业大学", "西北农林科技大学", "兰州大学", "国防科技大学",
}
TOP9 = {
    "北京大学", "清华大学", "复旦大学", "上海交通大学", "浙江大学",
    "中国科学技术大学", "南京大学", "哈尔滨工业大学", "西安交通大学",
}
TIER_RULE = (
    "T0 顶尖985（C9）; T1 其余985; T2 双一流建设高校（非985）; "
    "T3 有研究生院（非双一流）; T4 其他招生单位"
)


def tier(name: str, tags: list[str]) -> tuple[str, str]:
    if name in TOP9:
        return "T0", "T0 顶尖985"
    if name in C985:
        return "T1", "T1 985"
    if "“双一流”建设高校" in tags or "双一流" in tags:
        return "T2", "T2 双一流"
    if "研究生院" in tags:
        return "T3", "T3 有研究生院"
    return "T4", "T4 其他"


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    lib = json.load(open(os.path.join(HERE, "_cache", "schools_library.json"), encoding="utf-8"))
    rows = []
    for r in lib:
        tags = r.get("tags") or []
        dept = r.get("dept") or ""
        if "自划线" in dept and "自划线" not in tags:
            tags = tags + ["自划线"]
        province = PROV.get(r.get("ssdm") or "", "")
        grade, label = tier(r["dwmc"], tags)
        rows.append(
            {
                "招生单位代码": r.get("dwdm"),
                "招生单位名称": r.get("dwmc"),
                "所在省": province,
                "所属区": "B区" if r.get("ssdm") in B_ZONE else "A区",
                "院校标签": "/".join(tags),
                "是否985": "是" if r["dwmc"] in C985 else "否",
                "是否自划线": "是" if "自划线" in tags else "否",
                "系统梯度": grade,
                "梯度说明": label,
                "数据来源": "研招网院校库",
                "来源网址": "https://yz.chsi.com.cn/sch/",
            }
        )
    dim = pd.DataFrame(rows).sort_values(["系统梯度", "招生单位代码"])
    dim_path = os.path.join(OUT, "院校维度_全部招生单位.csv")
    dim.to_csv(dim_path, index=False, encoding="utf-8-sig")
    print(f"[dim] {len(dim)} schools -> {dim_path}")

    # verified 2023 cutoffs
    lines_path = os.path.join(DATA, "scorelines", "scorelines_2023.json")
    lines = pd.DataFrame(json.load(open(lines_path, encoding="utf-8"))) if os.path.exists(lines_path) else pd.DataFrame()
    if not lines.empty:
        lines = lines[["学校", "年份", "学科门类", "总分", "单科满分100", "单科满分>100", "来源", "来源网址"]]
        lines = lines.sort_values(["学校", "学科门类"])
        lines.to_csv(os.path.join(OUT, "自划线校线_2023.csv"), index=False, encoding="utf-8-sig")
        print(f"[2023] {len(lines)} rows, {lines['学校'].nunique()} schools")

    # coverage report
    cov = pd.DataFrame(
        [
            {"项目": "院校维度（全部招生单位）", "数量": len(dim), "状态": "完整"},
            {"项目": "2023 自划线院校校线", "数量": len(lines), "状态": f"覆盖 {lines['学校'].nunique() if not lines.empty else 0} 所自划线院校"},
            {"项目": "2024 自划线院校校线", "数量": 0, "状态": "研招网以图片发布，需 OCR，未收录"},
            {"项目": "2025 自划线院校校线", "数量": 0, "状态": "研招网以 PDF 发布，各校表格结构不一，未收录"},
            {"项目": "2023-2025 各专业招生计划", "数量": 0, "状态": "需逐校抓取官网招生专业目录（PDF/HTML/Excel），本次未批量完成"},
            {"项目": "2023-2025 报录比", "数量": 0, "状态": "仅少数院校公开，需逐校收集"},
        ]
    )

    out = os.path.join(OUT, "2023-2025院校专业数据_交付说明.xlsx")
    with pd.ExcelWriter(out, engine="openpyxl") as w:
        wb = w.book
        ws = wb.create_sheet("说明与字段映射")
        ws["A1"] = "2023-2025 考研院校专业数据（院校维度 + 2023 校线）"
        ws["A1"].font = Font(bold=True, size=14)
        head = PatternFill("solid", fgColor="1F4E79")
        white = Font(bold=True, color="FFFFFF")
        ws["A3"], ws["B3"] = "数据库字段（programs.payload）", "本文件对应列 / 说明"
        for c in ("A3", "B3"):
            ws[c].fill = head
            ws[c].font = white
        mapping = [
            ("schoolName", "招生单位名称"),
            ("programName", "本批为院校维度，暂无专业名；专业级见后续批次"),
            ("major", "同上"),
            ("region", "所在省"),
            ("year", "年份（校线表为 2023）"),
            ("scoreLine", "自划线校线表：总分"),
            ("enrollment", "招生计划：本次未批量取得，留空"),
            ("admissionRatio", "报录比：官方未公开，留空"),
            ("difficulty / systemTier", "系统梯度：T0–T4"),
            ("sources[].title / url", "数据来源、来源网址"),
            ("sources[].retrievedAt", "采集时间（见各脚本文档）"),
        ]
        for i, (k, v) in enumerate(mapping, start=4):
            ws.cell(row=i, column=1, value=k)
            c = ws.cell(row=i, column=2, value=v)
            c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.column_dimensions["A"].width = 34
        ws.column_dimensions["B"].width = 90
        r = len(mapping) + 6
        ws.cell(row=r, column=1, value="梯度规则").font = Font(bold=True)
        ws.cell(row=r, column=2, value=TIER_RULE).alignment = Alignment(wrap_text=True)
        ws.row_dimensions[r].height = 40

        def dump(ws2, df, name):
            if df.empty:
                ws2["A1"] = "暂无数据"
                return
            for j, col in enumerate(df.columns, 1):
                cell = ws2.cell(row=1, column=j, value=col)
                cell.fill = head
                cell.font = white
            for i, rec in enumerate(df.itertuples(index=False), 2):
                for j, val in enumerate(rec, 1):
                    ws2.cell(row=i, column=j, value=None if pd.isna(val) else val)
            ws2.freeze_panes = "C2"
            for col in range(1, len(df.columns) + 1):
                ws2.column_dimensions[get_column_letter(col)].width = 22

        dump(wb.create_sheet("院校维度"), dim, "dim")
        dump(wb.create_sheet("自划线校线2023"), lines, "lines")
        dump(wb.create_sheet("覆盖与缺口"), cov, "cov")
        if "Sheet" in wb.sheetnames:
            del wb["Sheet"]
    print(f"[xlsx] {out}")

    summary = {
        "schools": len(dim),
        "cutoffRows2023": len(lines),
        "cutoffSchools2023": int(lines["学校"].nunique()) if not lines.empty else 0,
        "excel": out,
        "dimCsv": dim_path,
    }
    with open(os.path.join(OUT, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print("[done]", json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
