"""List 硕士目录 专业 from the 一级学科 endpoints.

zys.do?mldm&yjxkdm returns the leading majors of that 一级学科 plus the sign token
each later query needs. The endpoint serves 10 rows per query, so for branches with
more majors the tail is not reachable without signing in; the script records the
claimed total (`truncated`) instead of pretending the list is complete.
"""
from __future__ import annotations

import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import chsi_get_json, chsi_post  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ML = [
    ("01", "哲学"), ("02", "经济学"), ("03", "法学"), ("04", "教育学"),
    ("05", "文学"), ("06", "历史学"), ("07", "理学"), ("08", "工学"),
    ("09", "农学"), ("10", "医学"), ("11", "军事学"), ("12", "管理学"),
    ("13", "艺术学"), ("14", "交叉学科"),
]


def main() -> int:
    tag = sys.argv[1] if len(sys.argv) > 1 else "2026"
    d = os.path.join(HERE, "_cache", tag)
    os.makedirs(d, exist_ok=True)
    dys = []
    for c in chsi_get_json("/zsml/code/zyly.json")["dms"]:
        if c.get("mc"):
            dys.append((c["dm"][:2], c["dm"], c["mc"]))
    targets = []
    for dm, mc in ML:
        for xk in chsi_get_json(f"/zsml/code/yjxk/{dm}.json")["dms"]:
            targets.append((dm, xk["dm"], xk["mc"], "xs"))
    for dm, code, mc in dys:
        targets.append((dm, code, mc, "zyxw"))

    out, truncated = [], []
    for dm, xk, mc, xwlx in targets:
        time.sleep(0.35)
        try:
            msg = chsi_post(
                "/zsml/rs/zys.do",
                {"mldm": dm, "yjxkdm": xk, "xwlx": xwlx, "start": 0, "curPage": 1, "pageSize": 10},
            )["msg"]
        except Exception as e:  # noqa: BLE001
            print(f"[majors] {xk} {mc} fail {e}")
            continue
        if not isinstance(msg, dict):
            print(f"[majors] {xk} {mc} -> {msg}")
            continue
        total = msg.get("totalCount") or 0
        for r in msg.get("list") or []:
            out.append(
                {
                    "xwlx": xwlx, "mldm": dm, "yjxkdm": r.get("yjxkdm") or xk,
                    "yjxkmc": r.get("yjxkmc") or mc, "zydm": r.get("zydm"),
                    "zymc": r.get("zymc"), "sign": r.get("sign"),
                }
            )
        if total > len(msg.get("list") or []):
            truncated.append({"yjxkdm": xk, "yjxkmc": mc, "returned": len(msg["list"]), "total": total})
        print(f"[majors] {xk} {mc} {xwlx}: {len(msg.get('list') or [])}/{total}", flush=True)

    with open(os.path.join(d, "majors.jsonl"), "w", encoding="utf-8") as f:
        for r in out:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    with open(os.path.join(d, "majors_truncated.json"), "w", encoding="utf-8") as f:
        json.dump(truncated, f, ensure_ascii=False, indent=1)
    print(f"[majors] wrote {len(out)} majors; {len(truncated)} 一级学科 truncated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
