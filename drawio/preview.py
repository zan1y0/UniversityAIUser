# -*- coding: utf-8 -*-
"""Rough PNG preview of a generated .drawio page (for layout self-check only)."""

import os
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "C:\\Windows\\Fonts\\msyh.ttc"
BOLD_PATH = "C:\\Windows\\Fonts\\msyhbd.ttc"
_cache = {}


def font(size, bold=False):
    key = (size, bold)
    if key not in _cache:
        path = BOLD_PATH if bold else FONT_PATH
        _cache[key] = ImageFont.truetype(path, max(int(size), 6), index=0)
    return _cache[key]


def style_map(style):
    out = {}
    for part in (style or "").split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def parse(path, diagram_index=0, scale=0.62):
    tree = ET.parse(path)
    root = tree.getroot()
    diagram = root.findall("diagram")[diagram_index]
    model = diagram.find("mxGraphModel")
    W = int(model.get("pageWidth"))
    H = int(model.get("pageHeight"))
    cells = []
    for c in model.find("root").findall("mxCell"):
        if c.get("vertex") != "1":
            continue
        g = c.find("mxGeometry")
        if g is None:
            continue
        cells.append((c, g))
    return W, H, cells


def render(path, out_png, diagram_index=0, scale=0.62):
    W, H, cells = parse(path, diagram_index)
    img = Image.new("RGB", (int(W * scale), int(H * scale)), "white")
    d = ImageDraw.Draw(img)
    for c, g in cells:
        st = style_map(c.get("style"))
        x = float(g.get("x", 0)) * scale
        y = float(g.get("y", 0)) * scale
        w = float(g.get("width", 0)) * scale
        h = float(g.get("height", 0)) * scale
        shape = (c.get("style") or "").split(";")[0]
        fill = st.get("fillColor")
        stroke = st.get("strokeColor")
        if fill in ("none", None):
            fillc = None
        elif fill.startswith("#"):
            fillc = fill
        else:
            fillc = None
        if stroke in ("none", None):
            strokec = None
        elif stroke.startswith("#"):
            strokec = stroke
        else:
            strokec = "#999999"
        radius = 0
        if shape == "ellipse":
            d.ellipse([x, y, x + w, y + h], fill=fillc, outline=strokec)
            continue
        if shape == "text":
            radius = 0
        elif shape == "line":
            continue
        else:
            radius = min(12 * scale, 4) if st.get("rounded") == "1" else 0
        if shape != "text" or fillc or strokec:
            if radius > 0.5:
                d.rounded_rectangle([x, y, x + w, y + h], radius=radius, fill=fillc, outline=strokec, width=1)
            else:
                d.rectangle([x, y, x + w, y + h], fill=fillc, outline=strokec, width=1)

        value = c.get("value") or ""
        if not value:
            continue
        text = value
        lines = text.split("<br>")
        size = float(st.get("fontSize", 12) or 12) * scale * 1.06
        fc = st.get("fontColor", "#1F2937")
        if not fc.startswith("#"):
            fc = "#1F2937"
        bold = st.get("fontStyle") == "1"
        f = font(size, bold)
        pad = float(st.get("spacingLeft", 0) or 0) * scale
        if st.get("whiteSpace") == "wrap":
            avail = max(w - 2 * pad - 4, 24)
            wrapped = []
            for line in lines:
                cur = ""
                for ch in line:
                    if cur and d.textlength(cur + ch, font=f) > avail:
                        wrapped.append(cur)
                        cur = ch
                    else:
                        cur += ch
                wrapped.append(cur)
            lines = wrapped
        lead = size * 1.35
        total = lead * len(lines)
        align = st.get("align", "left")
        valign = st.get("verticalAlign", "middle")
        if valign == "top":
            ty = y + 2 * scale
        elif valign == "bottom":
            ty = y + h - total - 2 * scale
        else:
            ty = y + (h - total) / 2
        for line in lines:
            tw_ = d.textlength(line, font=f)
            if align == "center":
                tx = x + (w - tw_) / 2
            elif align == "right":
                tx = x + w - tw_ - pad - 2 * scale
            else:
                tx = x + pad + 1 * scale
            d.text((tx, ty), line, font=f, fill=fc)
            ty += lead
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    img.save(out_png)
    return out_png


if __name__ == "__main__":
    src = sys.argv[1]
    out = sys.argv[2]
    idx = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    sc = float(sys.argv[4]) if len(sys.argv) > 4 else 0.62
    print(render(src, out, idx, sc))
