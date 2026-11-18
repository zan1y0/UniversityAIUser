# -*- coding: utf-8 -*-
"""Low level draw.io primitives + text metrics for the wireframe builder."""

INK = "#1F2937"
MUTED = "#6B7280"
FAINT = "#9CA3AF"
LINE = "#E5E7EB"
BG = "#F8FAFC"
WHITE = "#FFFFFF"
PRIMARY = "#4F46E5"
SOFT = "#EEF2FF"
ACCENT = "#0EA5E9"
WARN = "#B45309"
WARNSOFT = "#FFF7ED"
WARNLINE = "#FDBA74"
OK = "#047857"
OKSOFT = "#ECFDF5"
DANGER = "#B91C1C"
DANGERSOFT = "#FEF2F2"

FONT = "Microsoft YaHei"


def esc(value):
    s = str(value)
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")
    return s.replace("\n", "&lt;br&gt;")


def char_w(ch, size):
    return size * (1.0 if ord(ch) > 0x2E7F else 0.56)


def tw(text, size):
    return sum(char_w(c, size) for c in str(text))


def line_count(text, size, width):
    """Greedy estimate of how many wrapped lines a string needs."""
    total = 0
    for part in str(text).split("\n"):
        if not part:
            total += 1
            continue
        acc = 0.0
        lines = 1
        for ch in part:
            w = char_w(ch, size)
            if acc + w > max(width, 1):
                lines += 1
                acc = w
            else:
                acc += w
        total += lines
    return max(total, 1)


def text_h(text, size, width, lead=1.5):
    return int(line_count(text, size, width) * size * lead + 2)


def _fmt(v):
    if isinstance(v, float):
        return ("%.2f" % v).rstrip("0").rstrip(".")
    return str(v)


class Canvas(object):
    def __init__(self):
        self.cells = []
        self.n = 0

    def nid(self, prefix="c"):
        self.n += 1
        return "%s%d" % (prefix, self.n)

    def cell(self, value, style, x, y, w, h, ident=None):
        cid = ident or self.nid()
        self.cells.append(
            '<mxCell id="%s" value="%s" style="%s" vertex="1" parent="1">'
            '<mxGeometry x="%s" y="%s" width="%s" height="%s" as="geometry"/></mxCell>'
            % (cid, esc(value), style, _fmt(x), _fmt(y), _fmt(w), _fmt(h))
        )
        return cid

    # ---------------------------------------------------------------- shapes
    def box(self, x, y, w, h, value="", fill=WHITE, stroke=LINE, dash=0, arc=12,
            size=12, color=INK, bold=0, align="left", valign="middle", pad=10, sw=1):
        style = (
            "rounded=1;arcSize=%d;html=1;whiteSpace=wrap;fillColor=%s;strokeColor=%s;"
            "strokeWidth=%d;dashed=%d;fontFamily=%s;fontSize=%d;fontColor=%s;fontStyle=%d;"
            "align=%s;verticalAlign=%s;spacingLeft=%d;spacingRight=%d;spacingTop=0;spacingBottom=0;"
            % (arc, fill, stroke, sw, dash, FONT, size, color, bold, align, valign, pad, pad)
        )
        return self.cell(value, style, x, y, w, h)

    def label(self, x, y, w, h, value, size=12, color=INK, bold=0, align="left",
              valign="middle", lead=None):
        style = (
            "text;html=1;whiteSpace=wrap;fontFamily=%s;fontSize=%d;fontColor=%s;fontStyle=%d;"
            "align=%s;verticalAlign=%s;spacing=0;%s"
            % (FONT, size, color, bold, align, valign, (lead and "lineHeight=%d;" % lead) or "")
        )
        return self.cell(value, style, x, y, w, h)

    def text(self, x, y, w, value, size=12, color=INK, bold=0, align="left", valign="top", lead=150):
        h = text_h(value, size, w, lead / 100.0)
        self.label(x, y, w, h, value, size, color, bold, align, valign, lead)
        return h

    def pill(self, x, y, w, h, value, fill=WHITE, stroke=LINE, color=INK, size=11, bold=0):
        style = (
            "rounded=1;arcSize=50;html=1;whiteSpace=wrap;fillColor=%s;strokeColor=%s;strokeWidth=1;"
            "fontFamily=%s;fontSize=%d;fontColor=%s;fontStyle=%d;align=center;verticalAlign=middle;"
            % (fill, stroke, FONT, size, color, bold)
        )
        return self.cell(value, style, x, y, w, h)

    def button(self, x, y, w, h, value, primary=True, size=11):
        if primary:
            return self.pill(x, y, w, h, value, PRIMARY, PRIMARY, WHITE, size, 1)
        return self.pill(x, y, w, h, value, WHITE, LINE, INK, size, 0)

    def field(self, x, y, w, value, size=11, h=32, prefix=None, color=MUTED, select=False):
        self.box(x, y, w, h, value, WHITE, LINE, arc=10, size=size, color=color, pad=9)
        if select:
            self.label(x + w - 20, y + 6, 14, 20, "▾", 11, FAINT, 0, align="center")
        if prefix:
            self.label(x + 9, y + 8, w - 18, 14, prefix, 10, FAINT)

    def checkbox(self, x, y, checked, text, size=11, color=INK):
        self.box(x, y, 13, 13, "✓" if checked else "", WHITE if not checked else PRIMARY,
                 PRIMARY if checked else LINE, arc=6, size=9, color=WHITE, align="center", pad=0)
        self.label(x + 20, y - 1, 500, 16, text, size, color)

    def radio(self, x, y, selected, text, size=12):
        fill = PRIMARY if selected else WHITE
        self.cell("", "ellipse;html=1;fillColor=%s;strokeColor=%s;strokeWidth=1;" % (fill, PRIMARY if selected else "#D1D5DB"), x, y, 13, 13)
        self.label(x + 21, y - 2, 560, 18, text, size, INK)

    def check(self, x, y, done):
        if done:
            self.box(x, y, 15, 15, "✓", OK, OK, arc=6, size=10, color=WHITE, align="center", pad=0)
        else:
            self.box(x, y, 15, 15, "", WHITE, "#D1D5DB", arc=6, size=10, pad=0)

    def hr(self, x, y, w, color=LINE, dash=0):
        style = "line;html=1;strokeColor=%s;strokeWidth=1;dashed=%d;" % (color, dash)
        self.cells.append(
            '<mxCell id="%s" value="" style="%s" edge="1" parent="1">'
            '<mxGeometry x="%s" y="%s" width="%s" height="1" relative="0" as="geometry">'
            '<mxPoint x="%s" y="%s" as="sourcePoint"/><mxPoint x="%s" y="%s" as="targetPoint"/>'
            "</mxGeometry></mxCell>"
            % (self.nid("e"), style, _fmt(x), _fmt(y), _fmt(w), _fmt(x), _fmt(y), _fmt(x + w), _fmt(y + 1))
        )

    def arrow(self, x1, y1, x2, y2, color=MUTED, dash=0, label=""):
        style = "edge;html=1;endArrow=block;endSize=7;strokeColor=%s;strokeWidth=1.2;dashed=%d;fontFamily=%s;fontSize=10;fontColor=%s;rounded=0;" % (
            color, dash, FONT, MUTED)
        self.cells.append(
            '<mxCell id="%s" value="%s" style="%s" edge="1" parent="1">'
            '<mxGeometry relative="0" as="geometry"><mxPoint x="%s" y="%s" as="sourcePoint"/>'
            '<mxPoint x="%s" y="%s" as="targetPoint"/></mxGeometry></mxCell>'
            % (self.nid("e"), esc(label), style, _fmt(x1), _fmt(y1), _fmt(x2), _fmt(y2))
        )


def page(name_diagram, width, height, canvas, model_page=True):
    body = "".join(canvas.cells)
    return (
        '<diagram id="%s" name="%s"><mxGraphModel dx="%d" dy="%d" grid="1" gridSize="8" guides="1" '
        'tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="%d" '
        'pageHeight="%d" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>'
        "%s</root></mxGraphModel></diagram>"
        % (name_diagram[0], esc(name_diagram[1]), width, int(height * 0.8), width, height, body)
    )


def mxfile(diagrams):
    return (
        '<mxfile host="app.diagrams.net" agent="UniversityAIUser-wireframe" version="24.7.17" type="device">'
        + "".join(diagrams)
        + "</mxfile>"
    )
