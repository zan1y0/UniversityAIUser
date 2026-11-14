"""Export the normalized admission dataset to a formatted Excel workbook.

Sheets
  说明与字段字典   what every column means, how each value was obtained
  2026专业数据     one row per 院校 x 专业 x 院系 x 学习方式 x 考试方式
  复试线_2023..25  院校 official cutoff table (校线/学科线) where published
  院校信息         school dimension: code, province, 985/211/双一流/自划线 tags
  数据覆盖说明     honest per-discipline coverage, truncated branches, gaps
  数据来源         every document URL with retrieval time
"""
from __future__ import annotations

import json
import os

import pandas as pd
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.table import Table, TableStyleInfo

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, "data")
TAG = "2026"

FIELDS = [
    ("招生年份", "该专业目录对应的招生年度（2026=2025年10月发布、2026年入学）"),
    ("院校代码", "教育部 5 位招生单位代码，与研招网、学信网一致"),
    ("院校名称", "招生单位全称"),
    ("院校层级梯度", "T0 顶尖985 / T1 985或自划线 / T2 211或双一流 / T3 有博士点 / T4 普通院校"),
    ("梯度说明", "梯度判定的依据文字"),
    ("院校标签", "自划线/985/211/双一流/博士点，来自院校库属性"),
    ("所在省", "招生单位所在省级行政区"),
    ("学科门类", "门类代码+名称，如 08 工学"),
    ("一级学科代码", "4 位一级学科（专业学位为类别代码）"),
    ("一级学科", "一级学科名称"),
    ("专业代码", "6 位专业代码，如 085404"),
    ("专业名称", "专业名称"),
    ("学位类型", "学术学位 / 专业学位"),
    ("院系所代码", "招生院系代码"),
    ("院系所", "招生院系名称"),
    ("研究方向", "同一院系该专业下的全部方向，用；分隔"),
    ("学习方式", "全日制 / 非全日制"),
    ("考试方式", "统考 / 单独考试 / 推免等"),
    ("招生计划（统考/不含推免）", "研招网公示的该院系该专业统考计划；口径见“计划口径”"),
    ("推免人数", "该计划中推免占用人数（研招网 ssjstmrs）"),
    ("招生计划（合计）", "统考计划+推免人数，仅当口径为“不含推免”时可算"),
    ("计划口径", "研招网原文口径：不含推免 / 含推免 / 院系所合计"),
    ("考试科目", "初试四门科目代码与名称"),
    ("复试总分线", "院校公布的复试总分线；本表 2026 年尚未公布，2023-2025 见分年 sheet"),
    ("报录比", "报考人数/录取人数；绝大多数院校不公开，留空并在缺失说明标注"),
    ("系统梯度", "与“院校层级梯度”同值，供接口 difficulty 分组使用"),
    ("数据缺失说明", "该条记录缺失哪些字段、原因"),
    ("数据来源", "原始发布渠道"),
    ("来源网址", "原始页面/目录地址"),
    ("采集时间", "抓取时间（UTC）"),
    ("抓取截断", "该专业在该校是否有超过 10 行未取到（研招网未登录只返回前 10 行）"),
    ("专业备注", "研招网专业备注原文"),
]


def load(path: str):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build_sources(rows: list[dict], tag: str) -> pd.DataFrame:
    seen, out = set(), []
    for r in rows:
        key = (r.get("来源网址"), r.get("数据来源"))
        if key in seen:
            continue
        seen.add(key)
        out.append(
            {
                "年份": r.get("招生年份"),
                "来源名称": r.get("数据来源"),
                "网址": r.get("来源网址"),
                "发布单位": "教育部学生服务与素质发展中心（研招网）",
                "采集时间": r.get("采集时间"),
                "覆盖记录数": sum(1 for x in rows if (x.get("来源网址"), x.get("数据来源")) == key),
            }
        )
    return pd.DataFrame(out)


def build_schools(rows: list[dict]) -> pd.DataFrame:
    seen = {}
    for r in rows:
        code = r.get("院校代码")
        if code in seen:
            continue
        seen[code] = {
            "院校代码": code,
            "院校名称": r.get("院校名称"),
            "所在省": r.get("所在省"),
            "院校标签": r.get("院校标签"),
            "院校层级梯度": r.get("院校层级梯度"),
            "梯度说明": r.get("梯度说明"),
        }
    df = pd.DataFrame(seen.values())
    return df.sort_values(["院校层级梯度", "院校代码"]) if len(df) else df


def build_coverage(tag: str, rows: list[dict]) -> pd.DataFrame:
    cache = os.path.join(HERE, "_cache", tag)
    majors = []
    mp = os.path.join(cache, "majors.jsonl")
    if os.path.exists(mp):
        with open(mp, encoding="utf-8") as f:
            majors = [json.loads(x) for x in f if x.strip()]
    trunc = load(os.path.join(cache, "majors_truncated.json"))
    out = []
    by_major = {}
    for r in rows:
        by_major.setdefault(r.get("专业代码"), set()).add(r.get("院校代码"))
    for m in majors:
        out.append(
            {
                "专业代码": m.get("zydm"),
                "专业名称": m.get("zymc"),
                "学位类型": "学术学位" if m.get("xwlx") == "xs" else "专业学位",
                "已收录院校数": len(by_major.get(m.get("zydm"), ())),
            }
        )
    df = pd.DataFrame(out)
    return df


def style_sheet(ws, header_row: int = 1):
    fill = PatternFill("solid", fgColor="1F4E79")
    thin = Side(style="thin", color="BFBFBF")
    for cell in ws[header_row]:
        cell.font = Font(bold=True, color="FFFFFF", size=10)
        cell.fill = fill
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = Border(bottom=thin)
    ws.row_dimensions[header_row].height = 32
    ws.freeze_panes = ws.cell(row=header_row + 1, column=3)


def autosize(ws, max_width: int = 46, min_width: int = 9):
    for col in range(1, ws.max_column + 1):
        letter = get_column_letter(col)
        longest = 0
        for cell in ws[letter][:400]:
            v = "" if cell.value is None else str(cell.value)
            longest = max(longest, max(len(x) for x in v.split("\n")) if v else 0)
        ws.column_dimensions[letter].width = max(min_width, min(max_width, longest * 1.15 + 2))


def write_df(ws, df: pd.DataFrame, header_row: int = 1, table_name: str | None = None):
    for j, col in enumerate(df.columns, 1):
        ws.cell(row=header_row, column=j, value=col)
    for i, rec in enumerate(df.itertuples(index=False), header_row + 1):
        for j, val in enumerate(rec, 1):
            ws.cell(row=i, column=j, value=None if pd.isna(val) else val)
    style_sheet(ws, header_row)
    if table_name and len(df):
        ref = f"A{header_row}:{get_column_letter(len(df.columns))}{header_row + len(df)}"
        try:
            t = Table(displayName=table_name, ref=ref)
            t.tableStyleInfo = TableStyleInfo(
                name="TableStyleLight9", showRowStripes=True, showColumnStripes=False
            )
            ws.add_table(t)
        except Exception:  # noqa: BLE001 - duplicate names on repeated writes
            pass


def main() -> int:
    src = os.path.join(DATA, TAG, f"programs_{TAG}.json")
    if not os.path.exists(src):
        raise SystemExit(f"missing {src}; run build_programs.py first")
    rows = load(src)
    df = pd.DataFrame(rows)
    df = df.sort_values(["院校层级梯度", "所在省", "院校名称", "专业代码", "院系所代码"])
    print(f"[xlsx] rows={len(df)} cols={len(df.columns)}")

    out = os.path.join(DATA, f"考研院校专业数据_{TAG}.xlsx")
    os.makedirs(DATA, exist_ok=True)
    with pd.ExcelWriter(out, engine="openpyxl") as writer:
        wb = writer.book
        ws = wb.create_sheet("说明与字段字典")
        ws["A1"] = "考研院校专业招生数据（研招网硕士专业目录 + 院校官方复试线）"
        ws["A1"].font = Font(bold=True, size=14)
        notes = [
            ("数据口径", "每条记录 = 招生单位 × 专业 × 院系所 × 学习方式 × 考试方式，招生计划为该口径下的计划数。"),
            ("招生计划", "来自研招网《硕士专业目录》公示的 nzsrsstr；同一院系同一专业的研究方向行重复同一计划数，已去重，不能按行相加。"),
            ("推免", "ssjstmrs 为该计划中的推免人数；合计计划 = 统考计划 + 推免，仅“不含推免”口径可算。"),
            ("复试线", "2026 年复试线在 2026 年 3 月才公布，故本表 2026 数据为空；2023-2025 见分年 sheet，逐条附官方页面。"),
            ("报录比", "绝大多数院校不公开报考人数，因此留空并标注“官方未公开”，未做任何推算。"),
            ("系统梯度", "T0 顶尖985 / T1 985或自划线 / T2 211或双一流 / T3 有博士点 / T4 普通院校，依据院校库公开属性。"),
            ("已知限制", "研招网未登录时每个查询只返回前 10 行，因此部分一级学科的专业清单与部分院校的专业行可能不完整，见“数据覆盖说明”。"),
            ("复现方式", "scripts/scrape 下脚本按 catalog/pairs/rows 三阶段抓取，build_programs.py 归一化，export_excel.py 出表。"),
        ]
        r = 3
        for k, v in notes:
            ws.cell(row=r, column=1, value=k).font = Font(bold=True)
            ws.cell(row=r, column=2, value=v).alignment = Alignment(wrap_text=True, vertical="top")
            ws.row_dimensions[r].height = 42
            r += 1
        r += 1
        ws.cell(row=r, column=1, value="字段字典").font = Font(bold=True, size=12)
        r += 1
        ws.cell(row=r, column=1, value="字段").font = Font(bold=True)
        ws.cell(row=r, column=2, value="含义/来源").font = Font(bold=True)
        for name, meaning in FIELDS:
            r += 1
            ws.cell(row=r, column=1, value=name)
            c = ws.cell(row=r, column=2, value=meaning)
            c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.column_dimensions["A"].width = 30
        ws.column_dimensions["B"].width = 110

        ws2 = wb.create_sheet(f"{TAG}专业数据")
        write_df(ws2, df, table_name="t_programs")
        autosize(ws2)
        ws2.auto_filter.ref = f"A1:{get_column_letter(len(df.columns))}{len(df) + 1}"

        # overview: what the current batch actually covers
        ws0 = wb.create_sheet("统计概览", 1)
        ws0["A1"] = "本批次数据概览"
        ws0["A1"].font = Font(bold=True, size=13)
        stats = [
            ("记录数（院校×专业×院系×学习方式×考试方式）", len(df)),
            ("院校数", df["院校名称"].nunique() if len(df) else 0),
            ("专业数", df["专业代码"].nunique() if len(df) else 0),
            ("院系数", df["院系所"].nunique() if len(df) else 0),
            ("含招生计划的记录数", int((df["招生计划（统考/不含推免）"] != "").sum()) if len(df) else 0),
            ("含考试科目的记录数", int((df["考试科目"] != "").sum()) if len(df) else 0),
            ("2023 复试线记录数", len(load(os.path.join(DATA, "scorelines", "scorelines_2023.json")))),
            ("2023 复试线覆盖院校数", len({r["学校"] for r in load(os.path.join(DATA, "scorelines", "scorelines_2023.json"))})),
        ]
        r0 = 3
        for k, v in stats:
            ws0.cell(row=r0, column=1, value=k)
            ws0.cell(row=r0, column=2, value=v)
            r0 += 1
        r0 += 1
        ws0.cell(row=r0, column=1, value="按院校层级").font = Font(bold=True)
        r0 += 1
        tier_counts = df["院校层级梯度"].value_counts().to_dict() if len(df) else {}
        for k, v in tier_counts.items():
            ws0.cell(row=r0, column=1, value=k)
            ws0.cell(row=r0, column=2, value=int(v))
            r0 += 1
        r0 += 1
        ws0.cell(row=r0, column=1, value="按学位类型").font = Font(bold=True)
        r0 += 1
        degree_counts = df["学位类型"].value_counts().to_dict() if len(df) else {}
        for k, v in degree_counts.items():
            ws0.cell(row=r0, column=1, value=k)
            ws0.cell(row=r0, column=2, value=int(v))
            r0 += 1
        ws0.column_dimensions["A"].width = 46
        ws0.column_dimensions["B"].width = 14

        for year in (2025, 2024, 2023):
            p = os.path.join(DATA, "scorelines", f"scorelines_{year}.json")
            sdf = pd.DataFrame(load(p))
            if sdf.empty:
                continue
            ws3 = wb.create_sheet(f"复试线{year}")
            write_df(ws3, sdf)
            autosize(ws3)

        ws4 = wb.create_sheet("院校信息")
        write_df(ws4, build_schools(rows))
        autosize(ws4)

        ws5 = wb.create_sheet("数据覆盖说明")
        write_df(ws5, build_coverage(TAG, rows))
        autosize(ws5)
        trunc = load(os.path.join(HERE, "_cache", TAG, "majors_truncated.json"))
        start = ws5.max_row + 2
        ws5.cell(row=start, column=1, value="以下一级学科的专业数超过单次查询上限(10)，仅收录前 10 个专业").font = Font(bold=True, color="C00000")
        if trunc:
            ws5.cell(row=start + 1, column=1, value="一级学科代码")
            ws5.cell(row=start + 1, column=2, value="一级学科")
            ws5.cell(row=start + 1, column=3, value="已返回专业数")
            ws5.cell(row=start + 1, column=4, value="实际专业数")
            for i, t in enumerate(trunc, start + 2):
                ws5.cell(row=i, column=1, value=t.get("yjxkdm"))
                ws5.cell(row=i, column=2, value=t.get("yjxkmc"))
                ws5.cell(row=i, column=3, value=t.get("returned"))
                ws5.cell(row=i, column=4, value=t.get("total"))

        ws6 = wb.create_sheet("数据来源")
        write_df(ws6, build_sources(rows, TAG))
        autosize(ws6)

        if "Sheet" in wb.sheetnames:
            del wb["Sheet"]

    print(f"[xlsx] wrote {out}")
    print(json.dumps({"file": out, "rows": len(df)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
