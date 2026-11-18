# -*- coding: utf-8 -*-
"""Audit drawio specs against contracts/openapi.json and Flyway schema."""
import json
import re
import glob
import os

ROOT = r"D:\STUDY\UniversityAIUser"

# ---------------------------------------------------------------- openapi
doc = json.load(open(os.path.join(ROOT, "contracts", "openapi.json"), encoding="utf-8"))
ops = {}
for path, item in doc["paths"].items():
    for method, op in item.items():
        if method in ("get", "post", "put", "patch", "delete"):
            ops[op.get("operationId")] = (method.upper(), path, (op.get("tags") or [""])[0])

print("operations:", len(ops))
by_tag = {}
for oid, (m, p, tag) in sorted(ops.items()):
    by_tag.setdefault(tag, []).append(oid)
for tag in sorted(by_tag):
    ids = sorted(by_tag[tag])
    print("%-24s %3d  %s..%s" % (tag, len(ids), ids[0], ids[-1]))

# ---------------------------------------------------------------- schema
tables = {}
for f in sorted(glob.glob(os.path.join(ROOT, "src", "main", "resources", "db", "migration", "mysql", "*.sql"))):
    s = open(f, encoding="utf-8").read()
    for m in re.finditer(r"CREATE TABLE(?: IF NOT EXISTS)?\s+`?(\w+)`?\s*\((.*?)\n\)", s, re.S | re.I):
        name, body = m.group(1), m.group(2)
        cols = re.findall(r"^\s*`(\w+)`", body, re.M)
        tables.setdefault(name, []).extend(cols)
print("\ntables:", len(tables))
for t in sorted(tables):
    print("%-26s %s" % (t, ", ".join(tables[t][:12])))

# ---------------------------------------------------------------- spec refs
print("\n---- op references in drawio specs ----")
for f in ("pages.py", "build.py"):
    src = open(os.path.join(ROOT, "drawio", f), encoding="utf-8").read()
    for m in re.finditer(r"op\d{3}", src):
        oid = m.group(0)
        real = ops.get(oid)
        ctx = src[max(0, m.start() - 40):m.end() + 14].replace("\n", " ")
        flag = "" if real else "  <== NOT IN CONTRACT"
        print("%s %s | %s -> %s%s" % (f, oid, real, ctx.strip()[-54:], flag))
