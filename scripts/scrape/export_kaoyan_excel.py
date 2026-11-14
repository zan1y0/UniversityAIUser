"""Export the 2023-2026 kaoyan.cn dataset to Excel with xlsxwriter.

Sheets: 说明与字段映射 / 统计概览 / 2023..2026 / 院校维度 / 数据来源与缺口.
xlsxwriter is used instead of openpyxl because the dataset is 100k+ rows.
"""
from __future__ import annotations

import csv
import json
import os

import xlsxwriter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, "data", "2023_2026")
DIM_CSV = os.path.join(ROOT, "data", "2023_2025", "院校维度_全部招生单位.csv")
OUT = os.path.join(DATA, "2023-2026院校专业数据.xlsx")

FIELDS = [
    ("招生年份", "该数据对应的招生年度（2023=2022年10月目录、2023年入学）"),
    ("院校代码", "5 位招生单位代码（与研招网一致；未匹配到则为空）"),
    ("院校名称", "招生单位全称"),
    ("院校标签", "985/211/双一流/自划线，来自院校维度"),
    ("系统梯度", "T0 顶尖985 / T1 985或自划线 / T2 211或双一流 / T3 有研究生院 / T4 其他"),
    ("梯度说明", "梯度判定依据"),
    ("所在省", "招生单位所在省级行政区"),
    ("学位类型", "学术学位 / 专业学位"),
    ("学科门类", "门类名称，如 工学"),
    ("一级学科", "一级学科/专业学位类别，如 计算机科学与技术"),
    ("专业代码", "6 位专业代码"),
    ("专业名称", "专业名称"),
    ("院系所", "招生院系"),
    ("学习方式", "全日制 / 非全日制"),
    ("考试方式", "统考 / 单独考试等"),
    ("招生计划", "该院系该专业的计划人数（院校公布口径，多数为专业总数，不含推免的情况见备注）"),
    ("复试总分线", "该院系该专业的复试总分线；院系未单独划线时为空"),
    ("政治", "单科线：政治/管理类综合"),
    ("外语", "单科线：外语"),
    ("专业课一", "单科线：业务课一"),
    ("专业课二", "单科线：业务课二"),
    ("报录比", "报考人数/录取人数；官方未公开，全部留空"),
    ("数据缺失说明", "该条缺失哪些字段"),
    ("数据来源", "掌上考研/中国教育在线（院校官方数据汇总）"),
    ("来源网址", "院校数据页"),
    ("招生官网", "院校研究生招生官网"),
    ("备注", "原始备注（如“招生人数为专业总数，非方向人数”）"),
]


def load_rows() -> list[dict]:
    p = os.path.join(DATA, "programs_2023_2026.jsonl")
    with open(p, encoding="utf-8") as f:
        return [json.loads(x) for x in f if x.strip()]


def main() -> int:
    rows = load_rows()
    meta = json.load(open(os.path.join(DATA, "meta.json"), encoding="utf-8"))
    print(f"[xlsx] rows={len(rows)} meta={meta['byYear']}")

    wb = xlsxwriter.Workbook(OUT, {"strings_to_numbers": False, "constant_memory": False})
    head = wb.add_format({"bold": True, "font_color": "white", "bg_color": "#1F4E79",
                          "align": "center", "valign": "vcenter", "text_wrap": True, "border": 1})
    bold = wb.add_format({"bold": True})
    wrap = wb.add_format({"text_wrap": True, "valign": "top"})

    # 1) field dictionary
    ws = wb.add_worksheet("说明与字段映射")
    ws.set_column(0, 0, 30)
    ws.set_column(1, 1, 100)
    ws.write(0, 0, "2023–2026 考研院校专业数据（院校专业级）", wb.add_format({"bold": True, "font_size": 14}))
    ws.write(2, 0, "数据库字段（programs.payload）", head)
    ws.write(2, 1, "本文件对应列 / 说明", head)
    mapping = [
        ("schoolName", "院校名称"), ("programName", "专业代码 + 专业名称 + 院系所"),
        ("major", "专业名称"), ("region", "所在省"), ("year", "招生年份"),
        ("scoreLine", "复试总分线"), ("enrollment", "招生计划"),
        ("admissionRatio", "报录比（官方未公开，留空）"), ("difficulty / systemTier", "系统梯度"),
        ("sources[].title/url", "数据来源 / 来源网址"),
    ]
    r = 3
    for k, v in mapping:
        ws.write(r, 0, k)
        ws.write(r, 1, v, wrap)
        r += 1
    r += 1
    ws.write(r, 0, "字段字典", bold)
    r += 1
    ws.write(r, 0, "字段", head)
    ws.write(r, 1, "含义/来源", head)
    r += 1
    for name, meaning in FIELDS:
        ws.write(r, 0, name)
        ws.write(r, 1, meaning, wrap)
        r += 1

    # 2) overview
    ws2 = wb.add_worksheet("统计概览")
    ws2.set_column(0, 0, 40)
    ws2.set_column(1, 1, 18)
    ws2.write(0, 0, "数据概览", wb.add_format({"bold": True, "font_size": 13}))
    stats = [
        ("记录总数", len(rows)),
        ("覆盖招生单位", len({x["院校名称"] for x in rows})),
        ("覆盖专业代码", len({x["专业代码"] for x in rows})),
        ("含招生计划的记录", sum(1 for x in rows if x["招生计划"] != "")),
        ("含复试线的记录", sum(1 for x in rows if x["复试总分线"] != "")),
    ]
    r = 2
    for k, v in stats:
        ws2.write(r, 0, k)
        ws2.write(r, 1, v)
        r += 1
    r += 1
    ws2.write(r, 0, "分年份记录数", bold)
    r += 1
    for y, n in sorted(meta["byYear"].items()):
        ws2.write(r, 0, f"{y} 年")
        ws2.write(r, 1, n)
        r += 1

    # 3) per-year sheets
    cols = list(rows[0].keys())
    for year in sorted(meta["byYear"]):
        name = f"{year}"
        ws3 = wb.add_worksheet(name)
        for j, c in enumerate(cols):
            ws3.write(0, j, c, head)
            width = 26 if c in ("数据缺失说明", "备注", "招生官网", "来源网址") else 12
            if c in ("院校名称", "专业名称", "院系所", "一级学科", "学科门类"):
                width = 22
            ws3.set_column(j, j, width)
        yrows = [x for x in rows if str(x["招生年份"]) == str(year)]
        for i, rec in enumerate(yrows, 1):
            for j, c in enumerate(cols):
                v = rec.get(c, "")
                ws3.write(i, j, v)
        ws3.freeze_panes(1, 0)
        ws3.autofilter(0, 0, len(yrows), len(cols) - 1)
        print(f"[xlsx] {name}: {len(yrows)}")

    # 4) dimension
    ws4 = wb.add_worksheet("院校维度")
    with open(DIM_CSV, encoding="utf-8-sig") as f:
        dr = list(csv.DictReader(f))
    dcols = list(dr[0].keys()) if dr else []
    for j, c in enumerate(dcols):
        ws4.write(0, j, c, head)
        ws4.set_column(j, j, 18)
    for i, rec in enumerate(dr, 1):
        for j, c in enumerate(dcols):
            ws4.write(i, j, rec.get(c, ""))
    ws4.freeze_panes(1, 0)
    ws4.autofilter(0, 0, len(dr), len(dcols) - 1)

    # 5) coverage
    ws5 = wb.add_worksheet("数据来源与缺口")
    ws5.set_column(0, 0, 34)
    ws5.set_column(1, 1, 110)
    notes = [
        ("数据来源", "掌上考研/中国教育在线 www.kaoyan.cn 院校数据（聚合各校官方招生计划与复试线），逐校逐年代理；院校维度来自研招网院校库。"),
        ("2023 招生计划", "源站未收录 2023 年招生计划，2023 只有复试线；这是源站缺口，不是漏抓。"),
        ("报录比", "院校普遍不公开报考人数，源站也无该字段，全部留空，未做推算。"),
        ("复试线口径", "院系/专业线；院系未单独划线时为空（此时实际执行校线或国家线）。"),
        ("招生计划口径", "院校公布的专业计划数，多数备注为“招生人数为专业总数，非方向人数”；是否含推免以院校原文为准。"),
        ("复现", "scripts/scrape/kaoyan_cn.py 抓取，build_kaoyan_dataset.py 归一化，export_kaoyan_excel.py 出表；断点续跑。"),
    ]
    r = 0
    for k, v in notes:
        ws5.write(r, 0, k, bold)
        ws5.write(r, 1, v, wrap)
        r += 1

    wb.close()
    print(f"[xlsx] wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
