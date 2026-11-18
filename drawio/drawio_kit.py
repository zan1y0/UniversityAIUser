# -*- coding: utf-8 -*-
"""Minimal draw.io / mxGraph XML builder + wireframe layout kit for 研途 AI pages."""
import html
import uuid

# ---------------- palette ----------------
INK = "#244a37"
MUTED = "#7c8f86"
ACCENT = "#258d76"
ACCENT2 = "#427c56"
CARD_FILL = "#ffffff"
CARD_STROKE = "#d8e6dc"
SOFT = "#f4f9f4"
SOFT_STROKE = "#e0ece3"
TAG_FILL = "#e9f3ec"
TAG_STROKE = "#cfe3d6"
TAG_TEXT = "#3d7a5c"
BTN_FILL = "#427c56"
BTN_TEXT = "#ffffff"
BTN2_FILL = "#ffffff"
BTN2_STROKE = "#bcd6c6"
NOTE_FILL = "#fff8e1"
NOTE_STROKE = "#e6d7a3"
NOTE_TEXT = "#8a6d1f"
DASH_STROKE = "#9db9ab"
SIDEBAR_FILL = "#f0f6ee"
SIDEBAR_STROKE = "#dbe8dc"
TOPBAR_FILL = "#fbfdfa"
HERO_FILL = "#e8f2e4"
HERO_STROKE = "#cfe3cb"
FONT = "Microsoft YaHei"

BASE = (
    f"fontFamily={FONT};html=1;whiteSpace=wrap;strokeWidth=1;"
)


def esc(value):
    if value is None:
        return ""
    text = str(value)
    text = html.escape(text, quote=False)
    return text.replace("\n", "&lt;br&gt;")


class Diagram:
    def __init__(self, name):
        self.name = name
        self.cells = []
        self._n = 10

    def _id(self):
        self._n += 1
        return f"c{self._n}"

    def shape(self, x, y, w, h, label="", style="", ident=None):
        cid = ident or self._id()
        self.cells.append(
            f'<mxCell id="{cid}" value="{esc(label)}" style="{style}" vertex="1" parent="1">'
            f'<mxGeometry x="{round(x,1)}" y="{round(y,1)}" width="{round(w,1)}" height="{round(h,1)}" as="geometry"/>'
            f"</mxCell>"
        )
        return cid

    def box(self, x, y, w, h, label="", fill=CARD_FILL, stroke=CARD_STROKE, dashed=False,
            align="left", valign="top", size=12, color=INK, bold=False, bulge=1, spacing=10):
        style = BASE + (
            f"rounded={bulge};fillColor={fill};strokeColor={stroke};align={align};verticalAlign={valign};"
            f"fontSize={size};fontColor={color};spacing={spacing};"
        )
        if bold:
            style += "fontStyle=1;"
        if dashed:
            style += "dashed=1;dashPattern=4 4;"
        return self.shape(x, y, w, h, label, style)

    def text(self, x, y, w, h, label, size=12, color=INK, bold=False, align="left",
             valign="top", italic=False, spacing=0):
        style = (
            f"text;html=1;whiteSpace=wrap;fontFamily={FONT};fontSize={size};fontColor={color};"
            f"align={align};verticalAlign={valign};spacing={spacing};"
        )
        if bold:
            style += "fontStyle=1;"
        if italic:
            style += "fontStyle=2;"
        return self.shape(x, y, w, h, label, style)

    def line(self, x1, y1, x2, y2, color="#c9dcd0", width=1, dashed=False):
        style = f"endArrow=none;html=1;strokeColor={color};strokeWidth={width};"
        if dashed:
            style += "dashed=1;dashPattern=4 4;"
        cid = self._id()
        self.cells.append(
            f'<mxCell id="{cid}" style="{style}" edge="1" parent="1">'
            f'<mxGeometry relative="1" as="geometry"><mxPoint x="{round(x1,1)}" y="{round(y1,1)}" as="sourcePoint"/>'
            f'<mxPoint x="{round(x2,1)}" y="{round(y2,1)}" as="targetPoint"/></mxGeometry></mxCell>'
        )
        return cid

    def edge(self, sid, tid, label="", color="#7c9a89", dashed=False, width=1.5, exit=None, entry=None):
        style = (
            f"edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=block;endFill=1;"
            f"strokeColor={color};strokeWidth={width};fontFamily={FONT};fontSize=11;fontColor={MUTED};"
            f"labelBackgroundColor=#ffffff;"
        )
        if dashed:
            style += "dashed=1;dashPattern=5 4;"
        if exit:
            style += f"exitX={exit[0]};exitY={exit[1]};exitDx=0;exitDy=0;"
        if entry:
            style += f"entryX={entry[0]};entryY={entry[1]};entryDx=0;entryDy=0;"
        cid = self._id()
        self.cells.append(
            f'<mxCell id="{cid}" value="{esc(label)}" style="{style}" edge="1" parent="1" '
            f'source="{sid}" target="{tid}"><mxGeometry relative="1" as="geometry"/></mxCell>'
        )
        return cid

    def xml(self, width=1600, height=1100):
        body = "\n".join(self.cells)
        return (
            '<mxfile host="app.diagrams.net" type="device" version="24.7.17">\n'
            f'  <diagram id="{uuid.uuid4()}" name="{esc(self.name)}">\n'
            f'    <mxGraphModel dx="1600" dy="1100" grid="1" gridSize="10" guides="1" tooltips="1" '
            f'connect="1" arrows="0" fold="1" page="1" pageScale="1" pageWidth="{width}" '
            f'pageHeight="{height}" math="0" shadow="0">\n'
            "      <root>\n"
            '        <mxCell id="0"/>\n'
            '        <mxCell id="1" parent="0"/>\n'
            f"{body}\n"
            "      </root>\n"
            "    </mxGraphModel>\n"
            "  </diagram>\n"
            "</mxfile>\n"
        )


def text_w(text, size=12):
    """rough width of a single-line label (CJK aware)."""
    w = 0
    for ch in text:
        w += size * (1.0 if ord(ch) > 127 else 0.55)
    return w


def chip(b, x, y, label, active=False, h=30, size=12, fill=None):
    w = min(text_w(label, size) + 26, 260)
    f = fill or (TAG_FILL if active else "#ffffff")
    stroke = ACCENT2 if active else BTN2_STROKE
    color = ACCENT2 if active else MUTED
    b.box(x, y, w, h, label, fill=f, stroke=stroke, align="center", valign="middle",
          size=size, color=color, bold=active, bulge=1)
    return w


def button(b, x, y, label, primary=True, w=None, h=34, size=12):
    w = w or min(text_w(label, size) + 30, 260)
    if primary:
        b.box(x, y, w, h, label, fill=BTN_FILL, stroke=BTN_FILL, align="center", valign="middle",
              size=size, color=BTN_TEXT, bold=True)
    else:
        b.box(x, y, w, h, label, fill=BTN2_FILL, stroke=BTN2_STROKE, align="center", valign="middle",
              size=size, color=ACCENT2)
    return w
