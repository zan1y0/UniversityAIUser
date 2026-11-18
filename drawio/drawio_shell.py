# -*- coding: utf-8 -*-
"""App shell + block renderers for 研途 AI wireframe diagrams."""
from drawio_kit import (
    Diagram, esc, text_w, chip, button,
    INK, MUTED, ACCENT, ACCENT2, CARD_FILL, CARD_STROKE, SOFT, SOFT_STROKE,
    TAG_FILL, TAG_STROKE, TAG_TEXT, BTN_FILL, BTN_TEXT, BTN2_FILL, BTN2_STROKE,
    NOTE_FILL, NOTE_STROKE, NOTE_TEXT, DASH_STROKE, SIDEBAR_FILL, SIDEBAR_STROKE,
    TOPBAR_FILL, HERO_FILL, HERO_STROKE, FONT,
)

SW = 220          # sidebar width
TB = 56           # topbar height
CX = 240          # content x
CW = 1176         # content width
GAP = 16

NAV_GROUPS = [
    ("概览", [("/dashboard", "学习工作台")]),
    ("学习", [
        ("/companion", "数字人陪伴督学"),
        ("/schools", "智能择校分析"),
        ("/public-courses", "公共课智能辅导"),
        ("/professional-courses", "专业课学习支持"),
        ("/qa", "AI 答疑与解题"),
    ]),
    ("提升", [
        ("/practice", "刷题与错题训练"),
        ("/planning", "个性化学习规划"),
        ("/exams", "模考与估分"),
        ("/interview", "复试 AI 训练"),
    ]),
    ("资讯", [("/news", "资讯与政策提醒")]),
    ("教学", [("/teaching", "老师与任务")]),
]

MODULE_SHORT = {
    "/dashboard": "学习工作台", "/companion": "数字人陪伴督学", "/schools": "智能择校分析",
    "/public-courses": "公共课智能辅导", "/professional-courses": "专业课学习支持",
    "/qa": "AI 答疑与解题", "/practice": "刷题与错题训练", "/planning": "个性化学习规划",
    "/exams": "模考与估分", "/interview": "复试 AI 训练", "/news": "资讯与政策提醒",
    "/teaching": "老师与任务", "/teacher": "教学工作台", "/admin": "平台管理",
    "/account": "账号设置",
}

warnings = []


def sidebar(b, x, y, h, role="STUDENT", path="", mode="demo", compact_nav=False):
    b.box(x, y, SW, h, "", fill=SIDEBAR_FILL, stroke=SIDEBAR_STROKE, bulge=0)
    # brand
    b.box(x + 14, y + 14, 32, 32, "✦", fill=ACCENT2, stroke=ACCENT2, align="center",
          valign="middle", size=16, color="#ffffff")
    b.text(x + 54, y + 16, 150, 18, "研途 AI", size=15, bold=True, color=INK)
    b.text(x + 54, y + 36, 160, 16, "你的考研成长伙伴", size=10, color=MUTED)
    b.line(x + 14, y + 62, x + SW - 14, y + 62, color="#e2ecdf")

    cy = y + 72
    if role == "ADMIN":
        groups = [("平台管理", [("/admin", "平台管理")])]
    elif role == "TEACHER":
        groups = [("教学", [("/teacher", "教学工作台")])]
    else:
        groups = NAV_GROUPS
    for label, items in groups:
        b.text(x + 18, cy, 120, 16, label, size=10.5, color="#93a89b", bold=True)
        cy += 22
        for ipath, iname in items:
            active = ipath == path
            if active:
                b.box(x + 10, cy, SW - 20, 32, "", fill="#dcecdf", stroke="#dcecdf", bulge=1)
                b.box(x + 10, cy + 6, 3, 20, "", fill=ACCENT2, stroke=ACCENT2, bulge=0)
            b.box(x + 22, cy + 8, 15, 15, "▪" if not active else "▸", fill="none", stroke="none",
                  align="center", valign="middle", size=10,
                  color=ACCENT2 if active else "#9ab3a4")
            color = ACCENT2 if active else "#5f7768"
            b.text(x + 44, cy + 6, SW - 60, 20, iname, size=11.5, color=color, bold=active)
            cy += 32
        cy += 8
    b.text(x + 18, y + h - 46, SW - 36, 16, "● 一步一个脚印，向目标靠近。", size=9.5, color="#93a89b")
    return cy


def topbar(b, x, y, path="", mode="demo", role="STUDENT", bell=True):
    b.box(x, y, 1440 - x, TB, "", fill=TOPBAR_FILL, stroke=SIDEBAR_STROKE, bulge=0)
    b.text(x + 18, y + 18, 46, 18, "我的学习空间", size=11, color=MUTED)
    b.text(x + 104, y + 18, 14, 18, "›", size=12, color="#a9bdb3")
    b.text(x + 120, y + 17, 240, 20, MODULE_SHORT.get(path, "页面"), size=12, bold=True, color=INK)

    rx = 1416
    # user + logout
    b.box(rx - 26, y + 14, 28, 28, "我", fill="#e8f1ea", stroke="#cfe1d5", align="center",
          valign="middle", size=11, color=ACCENT2, bulge=3)
    rx -= 38
    b.text(rx - 74, y + 20, 74, 18, "林小满", size=11, color=INK, align="right")
    rx -= 86
    b.box(rx - 20, y + 15, 24, 24, "⏻", fill="none", stroke="none", align="center",
          valign="middle", size=12, color=MUTED)
    rx -= 30
    # role select
    b.box(rx - 86, y + 15, 86, 26, "学生 ▾", fill="#ffffff", stroke=BTN2_STROKE, align="center",
          valign="middle", size=10.5, color=MUTED)
    rx -= 98
    b.text(rx - 62, y + 20, 62, 18, "账号设置", size=10.5, color=ACCENT2, align="right")
    rx -= 74
    if bell:
        b.box(rx - 28, y + 15, 26, 26, "🔔", fill="none", stroke="none", align="center",
              valign="middle", size=12, color=MUTED)
        b.box(rx - 12, y + 14, 14, 14, "3", fill="#d8734a", stroke="#d8734a", align="center",
              valign="middle", size=8, color="#ffffff", bulge=6)
        rx -= 38
    label = "演示模式" if mode == "demo" else "真实接口模式"
    b.box(rx - 118, y + 15, 118, 26, "● " + label + "  切换", fill="#f2f8ef", stroke="#d3e3cf",
          align="center", valign="middle", size=10, color=ACCENT2, bulge=3)
    return TB


def heading(b, x, y, spec):
    eyebrow = spec.get("eyebrow", "M10 · KAOYAN LEARNING SPACE")
    title = spec.get("title", "页面标题")
    sub = spec.get("subtitle", "")
    b.text(x, y, 760, 16, eyebrow, size=9.5, color="#93a89b", bold=True)
    b.text(x, y + 16, 820, 26, title, size=19, bold=True, color=INK)
    b.text(x, y + 44, 900, 18, sub, size=11, color=MUTED)
    b.text(x + CW - 180, y + 4, 180, 18, "11月17日 星期一", size=10.5, color="#9fb2a8", align="right")


def banner(b, x, y, w, text, kind="demo"):
    fill = "#f2f7ec" if kind == "demo" else NOTE_FILL
    stroke = "#cfe0bf" if kind == "demo" else NOTE_STROKE
    color = "#5c7550" if kind == "demo" else NOTE_TEXT
    b.box(x, y, w, 42, "ⓘ   " + text, fill=fill, stroke=stroke, size=10.5, color=color,
          valign="middle", spacing=14)


def note_panel(b, x, y, w, title, lines):
    h = 34 + len(lines) * 20 + 12
    b.box(x, y, w, h, "", fill="#ffffff", stroke=DASH_STROKE, dashed=True, bulge=1)
    b.text(x + 16, y + 10, w - 32, 18, title, size=11.5, bold=True, color="#4c6f5c")
    for i, line in enumerate(lines):
        b.text(x + 16, y + 32 + i * 20, w - 32, 18, "· " + line, size=10.5, color="#5f7768")
    return h


def legend(b, x, y, items):
    cx = x
    for label, fill, stroke in items:
        b.box(cx, y + 4, 14, 14, "", fill=fill, stroke=stroke, bulge=1)
        b.text(cx + 20, y + 2, text_w(label, 10) + 12, 18, label, size=10, color=MUTED)
        cx += 20 + text_w(label, 10) + 26


# ------------------------- block renderers -------------------------

def req_h(spec):
    """minimum sensible height for a card-like block"""
    lines = len(spec.get("lines", []))
    rows = len(spec.get("rows", []))
    has_btn = bool(spec.get("button") or spec.get("buttons"))
    return 22 + 18 + lines * 18 + rows * 46 + (40 if has_btn else 6) + 12


def card(b, x, y, w, h, spec):
    title = spec.get("title", "")
    tag = spec.get("tag")
    lines = spec.get("lines", [])
    rows = spec.get("rows", [])
    button_spec = spec.get("button")
    buttons = spec.get("buttons", [])
    fill = spec.get("fill", CARD_FILL)
    stroke = spec.get("stroke", CARD_STROKE)
    b.box(x, y, w, h, "", fill=fill, stroke=stroke, bulge=1)

    if title:
        b.text(x + 14, y + 10, w - 28, 20, title, size=13, bold=True, color=INK)
    if tag:
        tw = min(text_w(tag, 10) + 18, w - 40)
        b.box(x + w - tw - 14, y + 10, tw, 20, tag, fill=TAG_FILL, stroke=TAG_STROKE,
              align="center", valign="middle", size=10, color=TAG_TEXT, bulge=3)
    cy = y + 38
    for line in lines:
        b.text(x + 14, cy, w - 28, 17, line, size=10.5, color=MUTED)
        cy += 18
    for row in rows:
        b.line(x + 14, cy, x + w - 14, cy, color="#eef4ef")
        if row.get("radio"):
            b.box(x + 14, cy + 13, 18, 18, "", fill="#ffffff", stroke="#b9d3c5", align="center",
                  valign="middle", size=10, color=ACCENT2, bulge=10)
        elif row.get("check") is not None:
            b.box(x + 14, cy + 14, 16, 16, "✓" if row.get("check") else "",
                  fill="#ffffff", stroke="#cfdfd5", align="center", valign="middle", size=10,
                  color=ACCENT2)
        b.text(x + 40, cy + 12, w - 130, 18, row["title"], size=11.5, color=INK)
        if row.get("meta"):
            b.text(x + 40, cy + 30, w - 130, 16, row["meta"], size=10, color=MUTED)
        if row.get("tag"):
            tw = min(text_w(row["tag"], 9.5) + 16, 110)
            tag_fill = TAG_FILL if row.get("tag_active", True) else "#f4f6f2"
            b.box(x + w - tw - 14, cy + 14, tw, 18, row["tag"], fill=tag_fill, stroke=TAG_STROKE,
                  align="center", valign="middle", size=9.5,
                  color=TAG_TEXT if row.get("tag_active", True) else MUTED, bulge=3)
        cy += 46
    if button_spec or buttons:
        by = y + h - 42
        bx = x + 14
        if button_spec:
            bx += button(b, bx, by, button_spec, primary=spec.get("primary", True)) + 10
        for label, primary in buttons:
            bx += button(b, bx, by, label, primary=primary) + 10
        if spec.get("hint"):
            b.text(x + 14, y + h - 24, w - 28, 16, spec["hint"], size=9.5, color="#9fb2a8")


def hero(b, x, y, w, spec):
    h = spec["h"]
    b.box(x, y, w, h, "", fill=HERO_FILL, stroke=HERO_STROKE, bulge=1)
    b.text(x + 24, y + 18, w - 200, 16, spec.get("kicker", ""), size=9.5, color="#7f9a6c", bold=True)
    b.text(x + 24, y + 36, w - 220, 26, spec.get("title", ""), size=17, bold=True, color="#2c5440")
    b.text(x + 24, y + 66, w - 260, 18, spec.get("text", ""), size=11, color="#5f7768")
    if spec.get("button"):
        button(b, x + 24, y + h - 44, spec["button"], primary=True)
    if spec.get("tag"):
        tw = text_w(spec["tag"], 10) + 20
        b.box(x + w - tw - 24, y + 18, tw, 22, spec["tag"], fill="#ffffff", stroke="#cfe3cb",
              align="center", valign="middle", size=10, color="#5c7550", bulge=3)
    b.box(x + w - 150, y + h / 2 - 34, 112, 68, spec.get("art", "插图"), fill="#ffffff",
          stroke="#d9e8d0", align="center", valign="middle", size=11, color="#8aa07a", bulge=1)


def metrics(b, x, y, w, spec):
    items = spec["items"]
    n = len(items)
    gap = 12
    cw = (w - gap * (n - 1)) / n
    h = spec["h"]
    for i, (label, value, note) in enumerate(items):
        bx = x + i * (cw + gap)
        b.box(bx, y, cw, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
        b.text(bx + 14, y + 12, cw - 28, 16, label, size=10, color=MUTED)
        b.text(bx + 14, y + 32, cw - 28, 28, value, size=19, bold=True, color=INK)
        b.text(bx + 14, y + h - 26, cw - 28, 16, note, size=9.5, color="#9fb2a8")
        b.box(bx + cw - 42, y + 12, 28, 28, "◈", fill="#f1f7ee", stroke="#dfead9",
              align="center", valign="middle", size=12, color="#8fae83", bulge=2)


def tabs(b, x, y, w, spec):
    items = spec["items"]
    active = spec.get("active", 0)
    label = spec.get("label")
    cx = x
    if label:
        b.text(cx, y + 6, text_w(label, 10.5) + 12, 18, label, size=10.5, color=MUTED)
        cx += text_w(label, 10.5) + 22
    for i, item in enumerate(items):
        cw = chip(b, cx, y, item, active=(i == active), h=32, size=11.5)
        cx += cw + 8
    return


def form(b, x, y, w, spec):
    h = spec["h"]
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 200, 20, spec["title"], size=13, bold=True, color=INK)
    if spec.get("tag"):
        tw = text_w(spec["tag"], 10) + 18
        b.box(x + w - tw - 14, y + 10, tw, 20, spec["tag"], fill=TAG_FILL, stroke=TAG_STROKE,
              align="center", valign="middle", size=10, color=TAG_TEXT, bulge=3)
    fields = spec.get("fields", [])
    cols = spec.get("cols", 4)
    fw = (w - 28 - 12 * (cols - 1)) / cols
    for i, f in enumerate(fields):
        fx = x + 14 + (i % cols) * (fw + 12)
        fy = y + 40 + (i // cols) * 62
        b.text(fx, fy, fw, 14, f["label"], size=9.5, color="#6f8677")
        val = f.get("value", "")
        b.box(fx, fy + 16, fw, 30, val, fill="#fcfdf9", stroke="#dfe6d8", align="left",
              valign="middle", size=10.5, color="#8a9a95", spacing=8)
        if f.get("select"):
            b.text(fx + fw - 20, fy + 22, 14, 14, "▾", size=9, color=MUTED)
    ay = y + h - 46
    bx = x + 14
    for label, primary in spec.get("actions", []):
        bx += button(b, bx, ay, label, primary=primary) + 10
    if spec.get("hint"):
        b.text(bx + 4, ay + 8, w - (bx - x) - 20, 18, spec["hint"], size=9.5, color="#9fb2a8")
    if spec.get("note"):
        b.text(x + 14, y + h - 18, w - 28, 16, spec["note"], size=9.5, color="#a7b3ab")


def chat(b, x, y, w, h, spec):
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 160, 20, "✦ " + spec.get("title", "对话"), size=13, bold=True, color=INK)
    b.text(x + w - 80, y + 12, 66, 18, "新对话", size=10, color=ACCENT2, align="right")
    b.line(x + 14, y + 36, x + w - 14, y + 36, color="#eef4ef")
    if spec.get("hint"):
        b.text(x + 14, y + 40, w - 28, 14, spec["hint"], size=9, color="#a7b3ab")
    msgs = spec.get("messages", [])
    cy = y + (58 if spec.get("hint") else 44)
    for role, text in msgs:
        bw = (w - 96) * 0.82
        if role == "user":
            b.box(x + w - bw - 22, cy, bw, 54, text, fill="#eaf4ec", stroke="#d5e7d8",
                  size=10, color="#3f5b4c", valign="middle", align="left", spacing=10)
        else:
            b.box(x + 22, cy, bw, 54, text, fill="#f7faf7", stroke="#e2ece5",
                  size=10, color="#3f5b4c", valign="middle", align="left", spacing=10)
        cy += 62
    if spec.get("prompts"):
        px = x + 14
        for p in spec["prompts"][:3]:
            pw = min(text_w(p, 9.5) + 20, (w - 40) / 3)
            b.box(px, y + h - 96, pw, 26, p, fill="#ffffff", stroke=BTN2_STROKE, align="center",
                  valign="middle", size=9.5, color=ACCENT2, bulge=3)
            px += pw + 8
    b.box(x + 14, y + h - 58, w - 92, 34, "输入你的问题…", fill="#fcfdf9", stroke="#dfe6d8",
          align="left", valign="middle", size=10, color="#a4b0a6", spacing=10)
    b.box(x + w - 68, y + h - 58, 54, 34, "发送", fill=BTN_FILL, stroke=BTN_FILL, align="center",
          valign="middle", size=10, color="#ffffff", bold=True, bulge=1)
    b.text(x + 14, y + h - 18, w - 28, 14, spec.get("caption", "演示回复为固定模板，不代表真实模型分析。"),
           size=9, color="#a7b3ab")


def chart(b, x, y, w, h, spec):
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 140, 20, spec["title"], size=13, bold=True, color=INK)
    if spec.get("tag"):
        tw = text_w(spec["tag"], 10) + 16
        b.box(x + w - tw - 14, y + 10, tw, 20, spec["tag"], fill=TAG_FILL, stroke=TAG_STROKE,
              align="center", valign="middle", size=10, color=TAG_TEXT, bulge=3)
    ix, iy, iw, ih = x + 14, y + 36, w - 28, h - 74
    b.box(ix, iy, iw, ih, spec.get("placeholder", "ECharts 图表"), fill="#fbfdfa", stroke="#e4eee7",
          dashed=True, align="center", valign="middle", size=10, color="#9fb2a8", bulge=1)
    if spec.get("caption"):
        b.text(x + 14, y + h - 30, w - 28, 16, spec["caption"], size=9, color="#a7b3ab")


def score(b, x, y, w, h, spec):
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 140, 20, spec["title"], size=13, bold=True, color=INK)
    b.box(x + w - 90, y + 10, 76, 20, "已交卷", fill="#e8f3ec", stroke="#cfe3d6", align="center",
          valign="middle", size=10, color=TAG_TEXT, bulge=3)
    b.box(x + 30, y + 44, 118, 118, str(spec.get("value", "78")) + "\n演示客观题折算分 / 100",
          fill="#f3f9f3", stroke="#cfe3cb", align="center", valign="middle", size=20,
          color=ACCENT2, bulge=20)
    stats = spec.get("stats", [])
    for i, (label, value) in enumerate(stats):
        sx = x + 168 + i * ((w - 190) / max(1, len(stats)))
        b.text(sx, y + 54, 120, 16, label, size=9.5, color=MUTED)
        b.text(sx, y + 72, 140, 22, value, size=13, bold=True, color=INK)


def progress(b, x, y, w, spec):
    h = spec["h"]
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 200, 20, spec["title"], size=13, bold=True, color=INK)
    if spec.get("tag"):
        tw = text_w(spec["tag"], 10) + 16
        b.box(x + w - tw - 14, y + 10, tw, 20, spec["tag"], fill=TAG_FILL, stroke=TAG_STROKE,
              align="center", valign="middle", size=10, color=TAG_TEXT, bulge=3)
    pct = spec.get("pct", 40)
    b.text(x + 14, y + 42, 200, 16, "任务完成进度", size=10, color=MUTED)
    b.text(x + w - 60, y + 42, 46, 16, f"{pct}%", size=10, color=ACCENT2, align="right")
    b.box(x + 14, y + 62, w - 28, 10, "", fill="#eef4ef", stroke="#eef4ef", bulge=4)
    b.box(x + 14, y + 62, (w - 28) * pct / 100, 10, "", fill=ACCENT2, stroke=ACCENT2, bulge=4)


def table(b, x, y, w, h, spec):
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 160, 20, spec["title"], size=13, bold=True, color=INK)
    if spec.get("link"):
        b.text(x + w - 110, y + 12, 96, 18, spec["link"], size=10, color=ACCENT2, align="right")
    heads = spec["headers"]
    rows = spec.get("rows", [])
    n = len(heads)
    tw = (w - 28) / n
    hy = y + 40
    b.box(x + 14, hy, w - 28, 28, "", fill="#f0f6f1", stroke="#e0ece3", bulge=0)
    for i, head in enumerate(heads):
        b.text(x + 20 + i * tw, hy + 6, tw - 8, 16, head, size=10, bold=True, color="#4c6f5c")
    ry = hy + 28
    for row in rows:
        b.line(x + 14, ry, x + w - 14, ry, color="#eef4ef")
        for i, cellv in enumerate(row):
            b.text(x + 20 + i * tw, ry + 6, tw - 10, 16, cellv, size=10, color=MUTED)
        ry += 26


def steps(b, x, y, w, h, spec):
    b.box(x, y, w, h, "", fill=CARD_FILL, stroke=CARD_STROKE, bulge=1)
    b.text(x + 14, y + 10, w - 28, 20, spec["title"], size=13, bold=True, color=INK)
    cy = y + 40
    for i, item in enumerate(spec["items"]):
        b.box(x + 16, cy + 2, 20, 20, str(i + 1), fill="#eef6ee", stroke="#d6e7d6",
              align="center", valign="middle", size=9.5, color=ACCENT2, bulge=10)
        b.text(x + 46, cy + 4, w - 64, 18, item, size=10.5, color="#5f7768")
        cy += 30


def dialog(b, x, y, w, h, spec):
    b.box(x, y, w, h, "", fill="#ffffff", stroke="#b9d0c2", bulge=1)
    b.text(x + 16, y + 12, w - 32, 20, spec["title"], size=13, bold=True, color=INK)
    cy = y + 44
    for label, value in spec.get("fields", []):
        b.text(x + 16, cy, w - 32, 14, label, size=9.5, color="#6f8677")
        b.box(x + 16, cy + 15, w - 32, 30, value, fill="#fcfdf9", stroke="#dfe6d8", align="left",
              valign="middle", size=10, color="#8a9a95", spacing=8)
        cy += 56
    if spec.get("note"):
        b.text(x + 16, cy, w - 32, 16, spec["note"], size=9.5, color="#a7b3ab")
        cy += 20
    bx = x + 16
    for label, primary in spec.get("actions", []):
        bx += button(b, bx, y + h - 44, label, primary=primary) + 10


def state_label(b, x, y, w, text, sub=""):
    b.box(x, y, 4, 22, "", fill=ACCENT, stroke=ACCENT, bulge=0)
    b.text(x + 12, y + 2, w - 20, 20, text, size=12, bold=True, color="#3c5c49")
    if sub:
        b.text(x + 14 + text_w(text, 12), y + 4, w - 220, 18, sub, size=9.5, color="#9fb2a8")


def annotation(b, x, y, w, text, h=30):
    b.box(x, y, w, h, text, fill="#ffffff", stroke=DASH_STROKE, dashed=True, align="left",
          valign="middle", size=10, color="#6d8474", spacing=10)


def block(b, x, y, w, spec):
    t = spec["type"]
    h = spec.get("h", 0)
    if t == "card":
        card(b, x, y, w, h, spec)
    elif t == "hero":
        hero(b, x, y, w, spec)
    elif t == "metrics":
        metrics(b, x, y, w, spec)
    elif t == "tabs":
        tabs(b, x, y, w, spec)
    elif t == "form":
        form(b, x, y, w, spec)
    elif t == "chat":
        chat(b, x, y, w, h, spec)
    elif t == "chart":
        chart(b, x, y, w, h, spec)
    elif t == "score":
        score(b, x, y, w, h, spec)
    elif t == "progress":
        progress(b, x, y, w, spec)
    elif t == "table":
        table(b, x, y, w, h, spec)
    elif t == "steps":
        steps(b, x, y, w, h, spec)
    elif t == "dialog":
        dialog(b, x, y, w, h, spec)
    elif t == "banner":
        banner(b, x, y, w, spec["text"], spec.get("kind", "demo"))
        h = 42
    elif t == "state":
        state_label(b, x, y, w, spec["text"], spec.get("sub", ""))
        h = 24
    elif t == "annotation":
        annotation(b, x, y, w, spec["text"], spec.get("h", 32))
        h = spec.get("h", 32)
    elif t == "spacer":
        pass
    elif t == "row":
        # several cards side by side
        items = spec["items"]
        gap = spec.get("gap", GAP)
        cw = (w - gap * (len(items) - 1)) / len(items)
        for i, item in enumerate(items):
            block(b, x + i * (cw + gap), y, cw, item)
        h = max(i.get("h", 0) for i in items)
    elif t == "split":
        ratio = spec.get("ratio", 0.62)
        gap = GAP
        lw = (w - gap) * ratio
        rw = (w - gap) - lw
        ly = y
        for item in spec["left"]:
            block(b, x, ly, lw, item)
            ly += item.get("h", 0) + 14
        ry = y
        for item in spec["right"]:
            block(b, x + lw + gap, ry, rw, item)
            ry += item.get("h", 0) + 14
        h = max(ly, ry) - y - 14
    elif t == "stack":
        cy = y
        for item in spec["items"]:
            block(b, x, cy, w, item)
            cy += item.get("h", 0) + spec.get("gap", GAP)
        h = cy - y - spec.get("gap", GAP)
    return h


def page(diagram, meta, blocks, notes, note_title="页面要点与交互说明", role="STUDENT", path="",
         mode="demo", footer=True, shell=True, width=1440):
    b = diagram
    # measure content height
    tmp = Diagram("tmp")
    ch = 0
    cy = 0
    for spec in blocks:
        ch = block(tmp, 0, cy, CW, spec)
        cy += ch + GAP
    content_h = cy - GAP

    top = 80
    head_h = 64
    body_y = top + TB + head_h + GAP + (42 + 12 if any(s["type"] == "banner" for s in blocks) else 0)
    shell_h = (body_y - top) + content_h + (34 if footer else 0) + 24

    # title + subtitle
    b.text(0, 4, 1200, 28, meta["title"], size=19, bold=True, color="#1f3d2e")
    b.text(0, 34, 1400, 20, meta["subtitle"], size=10.5, color=MUTED)

    # shell
    b.box(0, top, width, shell_h, "", fill="#f7fbf6", stroke="#cfe0d5", bulge=1)
    if shell:
        sidebar(b, 0, top, shell_h, role=role, path=path, mode=mode)
        topbar(b, SW, top, path=path, mode=mode, role=role)
    b.line(SW, top, SW, top + shell_h, color=SIDEBAR_STROKE)

    y = top + TB + 24
    heading(b, CX, y, meta)
    y += head_h
    for spec in blocks:
        if spec["type"] == "banner":
            banner(b, CX, y, CW, spec["text"], spec.get("kind", "demo"))
            y += 42 + 12
    y = body_y
    for spec in blocks:
        if spec["type"] == "banner":
            continue
        h = block(b, CX, y, CW, spec)
        y += h + GAP
    if footer:
        b.text(CX, y + 2, 520, 16, "研途 AI · 把每一次努力，变成看得见的进步", size=9.5, color="#a7b3ab")
        b.text(CX + CW - 90, y + 2, 90, 16, "清空演示数据", size=9.5, color="#a7b3ab", align="right")

    # notes
    ny = top + shell_h + 26
    nh = note_panel(b, 0, ny, width, note_title, notes)
    legend(b, 0, ny + nh + 10, [
        ("卡片 / 面板", CARD_FILL, CARD_STROKE),
        ("强调区", HERO_FILL, HERO_STROKE),
        ("提示条", "#f2f7ec", "#cfe0bf"),
        ("说明 / 虚线", "#ffffff", DASH_STROKE),
    ])
    return b, top + shell_h + 26 + nh + 46


def overview_page(diagram, title, subtitle):
    pass
