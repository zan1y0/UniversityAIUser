# -*- coding: utf-8 -*-
"""Block renderers that turn a page spec into draw.io wireframe cells."""

from lib import (
    Canvas, esc, text_h, tw, INK, MUTED, FAINT, LINE, BG, WHITE, PRIMARY, SOFT,
    ACCENT, WARN, WARNSOFT, WARNLINE, OK, OKSOFT, DANGER, DANGERSOFT, FONT,
)

PW = 1440
MARGIN = 40
CW = PW - 2 * MARGIN
PAD = 16


# --------------------------------------------------------------------- helpers
def measure(fn, *args):
    probe = Canvas()
    return fn(probe, *args)


def chip_row(c, x, y, items, h=24, size=10):
    cx = x
    for text, fill, color, stroke in items:
        w = tw(text, size) + 20
        c.pill(cx, y, w, h, text, fill, stroke, color, size, 0)
        cx += w + 8


def tag_pill(c, x_right, y, text, fill=SOFT, color=PRIMARY, size=10):
    w = tw(text, size) + 18
    c.pill(x_right - w, y, w, 20, text, fill, fill, color, size, 0)
    return w


def placeholder(c, x, y, w, h, text, size=11):
    c.box(x, y, w, h, text, "#F9FAFB", "#D1D5DB", dash=1, arc=6, size=size,
          color=FAINT, align="center", valign="middle")


# ------------------------------------------------------------------ leaf blocks
def blk_banner(c, b, x, y, w):
    kind = b.get("kind", "demo")
    if kind == "notice":
        fill, stroke, color = "#FFF7ED", WARNLINE, WARN
    else:
        fill, stroke, color = "#F5F3FF", "#DDD6FE", "#5B21B6"
    h = 40
    c.box(x, y, w, h, "ⓘ   " + b["text"], fill, stroke, arc=8, size=11, color=color, pad=14)
    return h


def blk_state(c, b, x, y, w):
    t = b["text"]
    size = 11 if tw(t, 11) + 26 <= 420 else 10
    pw = tw(t, size) + 26
    c.pill(x, y + 2, pw, 22, t, INK, INK, WHITE, size, 1)
    c.label(x + pw + 12, y + 3, w - pw - 12, 20, b.get("sub", ""), 10, FAINT)
    c.hr(x, y + 30, w, LINE, 1)
    return 42


def blk_hero(c, b, x, y, w):
    inner_w = w - 2 * 24
    art_w = 168
    text_w = inner_w - art_w - 24
    need = 22
    if b.get("kicker"):
        need += 22
    if b.get("title"):
        need += 32
    if b.get("text"):
        need += text_h(b["text"], 11, text_w) + 6
    need += 12 + 36 + 20
    h = max(b.get("h", 0) or 0, need)
    c.box(x, y, w, h, "", "#F5F3FF", "#DDD6FE", arc=10)
    yy = y + 22
    if b.get("kicker"):
        c.label(x + 24, yy, text_w, 16, b["kicker"], 10, "#7C3AED", 1)
        yy += 22
    if b.get("title"):
        c.label(x + 24, yy, text_w, 30, b["title"], 21, INK, 1)
        yy += 32
    if b.get("text"):
        hh = text_h(b["text"], 11, text_w)
        c.label(x + 24, yy, text_w, hh, b["text"], 11, MUTED)
        yy += hh + 6
    if b.get("button"):
        c.button(x + 24, yy + 6, tw(b["button"], 11) + 34, 34, b["button"], True)
    if b.get("tag"):
        c.pill(x + 24 + tw(b.get("button") or "", 11) + 46, yy + 11, tw(b["tag"], 10) + 20, 24,
               b["tag"], "#FFFFFF", "#DDD6FE", "#5B21B6", 10, 0)
    if b.get("art"):
        placeholder(c, x + w - 24 - art_w, y + 20, art_w, h - 40, b["art"], 10)
    return h


def blk_metrics(c, b, x, y, w):
    items = b["items"]
    gap = 14
    cw = (w - gap * (len(items) - 1)) / len(items)
    h = b.get("h", 106)
    for i, (label, value, meta) in enumerate(items):
        cx = x + i * (cw + gap)
        c.box(cx, y, cw, h, "", WHITE, LINE, arc=10)
        c.label(cx + 16, y + 16, cw - 32, 16, label, 11, MUTED)
        c.label(cx + 16, y + 38, cw - 32, 32, value, 24, INK, 1)
        c.label(cx + 16, y + h - 26, cw - 32, 16, meta, 10, FAINT)
    return h


def blk_tabs(c, b, x, y, w):
    items = b["items"]
    active = b.get("active", 0)
    h = 40
    cx = x
    for i, text in enumerate(items):
        bw = tw(text, 11) + 34
        if i == active:
            c.pill(cx, y, bw, h, text, PRIMARY, PRIMARY, WHITE, 11, 1)
        else:
            c.pill(cx, y, bw, h, text, WHITE, LINE, MUTED, 11, 0)
        cx += bw + 8
    if b.get("label"):
        c.label(cx + 6, y + 10, 200, 20, b["label"], 10, FAINT)
    return h


def blk_chart(c, b, x, y, w):
    return card(c, b, x, y, w, kind="chart")


def blk_card(c, b, x, y, w):
    return card(c, b, x, y, w)


def blk_steps(c, b, x, y, w):
    return card(c, b, x, y, w, kind="steps")


def blk_progress(c, b, x, y, w):
    return card(c, b, x, y, w, kind="progress")


def blk_score(c, b, x, y, w):
    return card(c, b, x, y, w, kind="score")


def blk_form(c, b, x, y, w):
    return card(c, b, x, y, w, kind="form")


def blk_chat(c, b, x, y, w):
    return card(c, b, x, y, w, kind="chat")


def blk_table(c, b, x, y, w):
    return card(c, b, x, y, w, kind="table")


def blk_dialog(c, b, x, y, w):
    h = measure(dialog_body, b, 0, 0, w)
    c.box(x, y, w, h, "", "#111827", "#111827", arc=10, sw=0)
    c.box(x + 6, y + 6, w - 12, h - 12, "", "#F3F4F6", "#F3F4F6", arc=8)
    dialog_body(c, b, x, y, w)
    c.label(x, y + h + 4, w, 14, "模态 dialog · 打开时页面其余内容不可交互", 9, FAINT, align="center")
    return h + 18


def blk_annotation(c, b, x, y, w):
    h = text_h(b["text"], 10, w - 100) + 20
    c.box(x, y, w, h, "", "#FFFBEB", "#FDE68A", dash=1, arc=6)
    c.label(x + 10, y + 8, 60, 16, "说明", 10, "#92400E", 1)
    c.label(x + 62, y + 8, w - 74, h - 16, b["text"], 10, "#92400E")
    return h


RENDER = {
    "banner": blk_banner,
    "state": blk_state,
    "hero": blk_hero,
    "metrics": blk_metrics,
    "tabs": blk_tabs,
    "chart": blk_chart,
    "card": blk_card,
    "steps": blk_steps,
    "progress": blk_progress,
    "score": blk_score,
    "form": blk_form,
    "chat": blk_chat,
    "table": blk_table,
    "dialog": blk_dialog,
    "annotation": blk_annotation,
}


# ----------------------------------------------------------------- card engine
def card_height(b, w, kind="card"):
    return measure(card_body, b, 0, 0, w, kind)


def card(c, b, x, y, w, kind="card"):
    m = card_height(b, w, kind)
    given = b.get("h") or 0
    if given and not b.get("fill") and m + 12 < given:
        h = m
    else:
        h = max(m, given)
    fill = b.get("fill", WHITE)
    stroke = b.get("stroke", LINE)
    c.box(x, y, w, h, "", fill, stroke, arc=10)
    if b.get("accent"):
        c.box(x, y, 4, h, "", b["accent"], b["accent"], arc=0)
    card_body(c, b, x, y, w, kind, h)
    return h


def card_body(c, b, x, y, w, kind="card", h=None):
    pad = PAD
    cw = w - 2 * pad
    cy = y + 14
    title = b.get("title")
    tag = b.get("tag")
    if title:
        tagw = tw(tag, 10) + 24 if tag else 0
        c.label(x + pad, cy, cw - tagw - 8, 20, title, 13, INK, 1)
        if tag:
            tag_pill(c, x + w - pad, cy + 1, tag)
        cy += 26
    elif tag:
        tag_pill(c, x + w - pad, cy, tag)
        cy += 26

    if b.get("lead"):
        for line in b["lead"]:
            lh = text_h(line, 11, cw)
            c.label(x + pad, cy, cw, lh, line, 11, INK)
            cy += lh + 5

    if kind == "chart":
        ph = b.get("ph", 150)
        available = (h or (y + 300)) - cy - (58 if b.get("caption") else 20) - 14
        ph = max(ph, available)
        placeholder(c, x + pad, cy, cw, ph, b.get("placeholder", "图表占位"), 10)
        cy += ph + 10
        if b.get("caption"):
            ch = text_h(b["caption"], 10, cw)
            c.label(x + pad, cy, cw, ch, b["caption"], 10, MUTED)
            cy += ch
        return cy + 14 - y

    if kind == "score":
        c.label(x + pad, cy + 6, cw, 16, b.get("label", "演示客观题折算分"), 10, FAINT)
        c.label(x + pad, cy + 24, cw, 44, b.get("value", "0"), 34, PRIMARY, 1)
        cy += 76
        if b.get("stats"):
            cols = len(b["stats"])
            sw = cw / cols
            for i, (k, v) in enumerate(b["stats"]):
                c.label(x + pad + i * sw, cy, sw - 8, 16, k, 10, FAINT)
                c.label(x + pad + i * sw, cy + 16, sw - 8, 18, v, 11, INK, 1)
            cy += 40
        if b.get("extra"):
            cy += 8 + text_h(b["extra"], 10, cw)
        return cy + 14 - y

    if kind == "progress":
        c.label(x + pad, cy, cw, 16, b.get("value_text", ""), 11, MUTED)
        cy += 20
        bar_h = 8
        c.box(x + pad, cy, cw, bar_h, "", "#E5E7EB", "#E5E7EB", arc=0)
        pct = max(0.0, min(1.0, (b.get("pct", 0) or 0) / 100.0))
        if pct > 0:
            c.box(x + pad, cy, max(cw * pct, 6), bar_h, "", PRIMARY, PRIMARY, arc=0)
        cy += bar_h + 10
        if b.get("note"):
            cy += text_h(b["note"], 10, cw) + 4
        return cy + 14 - y

    if kind == "steps":
        for i, item in enumerate(b.get("items", [])):
            c.cell("", "ellipse;html=1;fillColor=%s;strokeColor=%s;" % (SOFT, SOFT), x + pad, cy + 1, 20, 20)
            c.label(x + pad, cy + 2, 20, 16, str(i + 1), 10, PRIMARY, 1, align="center")
            ih = text_h(item, 11, cw - 32)
            c.label(x + pad + 28, cy + 1, cw - 28, ih, item, 11, INK)
            cy += max(ih, 20) + 8
        return cy + 8 - y

    if kind == "chat":
        msgs = b.get("messages", [])
        for role, text in msgs:
            size = 11
            bw = min(cw * 0.88, tw(text, size) + 34)
            bh = text_h(text, size, bw - 28) + 20
            if role == "assistant":
                c.box(x + pad, cy, bw, bh, text, "#F3F4F6", "#F3F4F6", arc=10, size=size, color=INK, pad=12, valign="middle")
            else:
                c.box(x + w - pad - bw, cy, bw, bh, text, PRIMARY, PRIMARY, arc=10, size=size, color=WHITE, pad=12, valign="middle")
            cy += bh + 8
        if b.get("prompts"):
            c.label(x + pad, cy, cw, 14, "快捷提问", 9, FAINT)
            cy += 16
            px = x + pad
            for p in b["prompts"]:
                pw = tw(p, 9) + 26
                if px + pw > x + w - pad:
                    px = x + pad
                    cy += 26
                c.pill(px, cy, pw, 22, p, WHITE, LINE, MUTED, 9, 0)
                px += pw + 6
            cy += 30
        c.box(x + pad, cy, cw - 74, 36, b.get("input", "输入你的问题…"), WHITE, LINE, arc=8, size=10, color=FAINT, pad=10)
        c.button(x + w - pad - 66, cy, 66, 36, b.get("send", "发送"), True)
        cy += 46
        return cy + 6 - y

    if kind == "table":
        headers = b.get("headers", [])
        rows = b.get("rows", [])
        n = max(len(headers), 1)
        colw = [cw * (0.34 if i == 0 else 0.66 / (n - 1)) for i in range(n)] if n > 1 else [cw]
        colx = [x + pad + sum(colw[:i]) for i in range(n)]
        rh = 34
        c.box(x + pad, cy, cw, rh, "", SOFT, SOFT, arc=6)
        for i, htext in enumerate(headers):
            c.label(colx[i] + 8, cy + 8, colw[i] - 12, 18, htext, 10, PRIMARY, 1)
        cy += rh
        for row in rows:
            for i, cell in enumerate(row):
                size = 10
                color = WARN if ("提示" in headers[i] if i < len(headers) else False) else INK
                hh = text_h(cell, size, colw[i] - 16)
                c.label(colx[i] + 8, cy + 8, colw[i] - 12, hh, cell, size, color)
            cy += max(rh, text_h(" ".join(map(str, row)), 10, cw) + 16)
            c.hr(x + pad, cy, cw, "#F3F4F6", 0)
        return cy + 12 - y

    # generic rows / lines / fields / actions
    if b.get("fields"):
        cols = b.get("cols", 2)
        gap = 14
        fw = (cw - gap * (cols - 1)) / cols
        for i, f in enumerate(b["fields"]):
            fx = x + pad + (i % cols) * (fw + gap)
            fy = cy + (i // cols) * 56
            c.label(fx, fy, fw, 14, f["label"], 10, MUTED)
            c.field(fx, fy + 16, fw, f.get("value", ""), 11, f.get("fh", 32), select=f.get("select"))
        rows_used = (len(b["fields"]) + cols - 1) // cols
        cy += rows_used * 56 + 4

    if b.get("textarea"):
        ta = b["textarea"]
        if isinstance(ta, str):
            ta = {"label": ta}
        th = ta.get("h", 64)
        c.label(x + pad, cy, cw, 14, ta.get("label", ""), 10, MUTED)
        c.box(x + pad, cy + 16, cw, th, ta.get("value", ""), WHITE, LINE, arc=8,
              size=10, color=FAINT, pad=10, valign="top")
        cy += th + 26

    if b.get("rows"):
        for row in b["rows"]:
            if isinstance(row, str):
                row = {"title": row}
            indent = 0
            if "check" in row:
                c.check(x + pad, cy + 3, row["check"])
                indent = 24
            if "radio" in row:
                c.radio(x + pad, cy + 3, row.get("selected", False), "")
                indent = 24
            tagw = tw(row["tag"], 10) + 26 if row.get("tag") else 0
            twidth = cw - indent - tagw - 12
            th = text_h(row["title"], 12, twidth)
            c.label(x + pad + indent, cy, twidth, th, row["title"], 12, INK, 0)
            rh = th
            if row.get("meta"):
                mh = text_h(row["meta"], 10, twidth)
                c.label(x + pad + indent, cy + th + 2, twidth, mh, row["meta"], 10, MUTED)
                rh = th + mh + 2
            if row.get("tag"):
                col = MUTED
                fill = "#F3F4F6"
                if row["tag"] in ("已完成", "对", "稳定", "已学完"):
                    col, fill = OK, OKSOFT
                elif row["tag"] in ("已超期", "薄弱", "错", "已关注"):
                    col, fill = WARN, WARNSOFT
                elif row["tag"] in ("待完成", "—"):
                    col, fill = MUTED, "#F3F4F6"
                c.pill(x + w - pad - tagw + 8, cy + 1, tagw - 8, 20, row["tag"], fill, fill, col, 10, 0)
            cy += max(rh, 30) + 8
            c.hr(x + pad, cy - 2, cw, "#F3F4F6", 0)

    if b.get("lines"):
        for line in b["lines"]:
            lh = text_h(line, 11, cw)
            c.label(x + pad, cy, cw, lh, line, 11, MUTED)
            cy += lh + 4

    if b.get("placeholder"):
        ph = b.get("ph", 120)
        placeholder(c, x + pad, cy, cw, ph, b["placeholder"], 10)
        cy += ph + 8

    if b.get("pct") is not None and kind != "progress":
        pct = max(0.0, min(1.0, b["pct"] / 100.0))
        c.box(x + pad, cy, cw, 8, "", "#E5E7EB", "#E5E7EB", arc=0)
        c.box(x + pad, cy, max(cw * pct, 6), 8, "", PRIMARY, PRIMARY, arc=0)
        cy += 16

    btns = list(b.get("buttons", []))
    if b.get("button"):
        btns.append((b["button"], b.get("primary", True)))
    if b.get("actions"):
        btns.extend(b["actions"])
    if btns:
        cy += 4
        bx = x + pad
        for text, primary in btns:
            bw = tw(text, 11) + 32
            if bx + bw > x + w - pad:
                bx = x + pad
                cy += 38
            c.button(bx, cy, bw, 32, text, primary)
            bx += bw + 8
        cy += 40

    if b.get("hint"):
        hh = text_h(b["hint"], 10, cw - 24) + 16
        c.box(x + pad, cy, cw, hh, b["hint"], WARNSOFT, WARNLINE, arc=6, size=10, color=WARN, pad=10)
        cy += hh + 4

    return cy + 14 - y


def dialog_body(c, b, x, y, w):
    pad = 18
    cw = w - 24 - 2 * pad
    cy = y + 26
    c.label(x + 18 + pad, cy, cw - 30, 20, b.get("title", "Dialog"), 13, INK, 1)
    c.label(x + w - 44, cy - 1, 24, 20, "×", 16, MUTED, 0, align="center")
    cy += 30
    if b.get("sub"):
        cy += text_h(b["sub"], 10, cw) + 6
    for label, placeholder in b.get("fields", []):
        c.label(x + 18 + pad, cy, cw, 14, label, 10, MUTED)
        c.field(x + 18 + pad, cy + 16, cw, placeholder, 11, 32)
        cy += 56
    if b.get("note"):
        nh = text_h(b["note"], 10, cw) + 14
        c.box(x + 18 + pad, cy, cw, nh, b["note"], WARNSOFT, WARNLINE, arc=6, size=10, color=WARN, pad=8)
        cy += nh + 6
    bx = x + w - 18 - pad
    for text, primary in reversed(b.get("actions", [])):
        bw = tw(text, 11) + 30
        bx -= bw
        c.button(bx, cy, bw, 32, text, primary)
        bx -= 8
    cy += 40
    return cy + 14 - y


# ------------------------------------------------------------------ containers
def blk_split(c, b, x, y, w):
    ratio = b.get("ratio", 0.5)
    gap = 18
    lw = (w - gap) * ratio
    rw = w - gap - lw
    lh = measure(stack, b.get("left", []), x, y, lw)
    rh = measure(stack, b.get("right", []), x + lw + gap, y, rw)
    stack(c, b.get("left", []), x, y, lw)
    stack(c, b.get("right", []), x + lw + gap, y, rw)
    return max(lh, rh)


def stack(c, blocks, x, y, w):
    cy = y
    for b in blocks:
        cy += RENDER[b["type"]](c, b, x, cy, w) + 16
    return cy - y - 16


def blk_row(c, b, x, y, w):
    items = b["items"]
    gap = 16
    cw = (w - gap * (len(items) - 1)) / len(items)
    kinds = []
    hs = []
    for blk in items:
        t = blk.get("kind") or blk["type"]
        kinds.append(t)
        hs.append(RENDER[t](Canvas(), blk, 0, 0, cw))
    mh = max(hs)
    h = 0
    for i, blk in enumerate(items):
        cx = x + i * (cw + gap)
        t = kinds[i]
        if t in ("card", "chat") and hs[i] < mh:
            blk = dict(blk, h=mh, fill=True)
        h = max(h, RENDER[t](c, blk, cx, y, cw))
    return h


RENDER.update({"split": blk_split, "row": blk_row})


# ------------------------------------------------------------------ page frame
def page_header(c, spec):
    c.label(MARGIN, 26, 900, 18, "研途 AI · 页面 UI 线框图", 11, FAINT, 1)
    c.label(MARGIN, 46, CW - 480, 42, spec["meta"].get("title", ""), 26, INK, 1)
    chips = []
    if spec.get("path"):
        chips.append(("路由  " + spec["path"], "#EEF2FF", PRIMARY, "#DDD6FE"))
    if spec.get("role"):
        chips.append(("角色  " + spec["role"], "#F0FDF4", OK, "#BBF7D0"))
    if spec.get("mode"):
        chips.append(("模式  " + ("demo" if spec["mode"] == "demo" else "live"), "#E0F2FE", "#0369A1", "#BAE6FD"))
    chip_row(c, MARGIN + 640, 30, chips, 24, 10)
    sub = spec["meta"].get("subtitle", "")
    sh = text_h(sub, 11, CW - 480)
    c.label(MARGIN, 96, CW - 480, sh, sub, 11, MUTED)
    c.hr(0, 140, PW, LINE, 0)
    return 170


def render_wireframe(spec):
    c = Canvas()
    top = page_header(c, spec)
    cy = top + 8
    for b in spec["blocks"]:
        cy += RENDER[b["type"]](c, b, MARGIN, cy, CW) + 22
    height = int(cy + 30)
    c.box(0, 0, PW, height, "", WHITE, WHITE, arc=0, sw=0)
    # move frame to back: it was appended last, draw.io keeps order, so re-insert
    frame = c.cells[-1]
    c.cells.insert(0, frame)
    c.cells.pop()
    header_cells = []
    return c, height
