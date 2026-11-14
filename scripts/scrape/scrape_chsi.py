"""Scrape the 研招网 硕士专业目录 (yz.chsi.com.cn/zsml) for one catalog year.

Output (all under scripts/scrape/_cache/<tag>/):
  majors.jsonl   one line per 专业: 门类/学科/专业代码, name, sign token
  schools.jsonl  one line per 专业-院校 pair: school attrs + sign token
  rows.jsonl     one line per 院校-专业-院系-研究方向 row: plan numbers, exams
  progress.json  counters, so an interrupted run resumes where it stopped

Row semantics that matter for the database:
  * one catalog row is one 院系 x 专业 x 研究方向
  * nzsrsstr ("专业：19(不含推免)") carries the 专业-level plan in that department,
    repeated on every direction row, so parsers must dedupe by
    (school, major, department, xxfs, ksfs) instead of summing rows
  * ssjstmrs is the 推免 (recommended-exemption) share of that plan
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import chsi_get_json, chsi_post  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def cache_dir(tag: str) -> str:
    d = os.path.join(HERE, "_cache", tag)
    os.makedirs(d, exist_ok=True)
    return d


class Writer:
    """Append-only JSONL writer shared by worker threads."""

    def __init__(self, path: str, key_fields: tuple[str, ...]):
        self.path = path
        self.key_fields = key_fields
        self.lock = threading.Lock()
        self.seen: set[tuple] = set()
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        rec = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    self.seen.add(tuple(str(rec.get(k)) for k in key_fields))
        self.fh = open(path, "a", encoding="utf-8")

    def add(self, rec: dict) -> bool:
        key = tuple(str(rec.get(k)) for k in self.key_fields)
        with self.lock:
            if key in self.seen:
                return False
            self.seen.add(key)
            self.fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
            self.fh.flush()
            return True

    def close(self):
        self.fh.close()


def paged(path: str, body: dict, max_pages: int = 400) -> list[dict]:
    """Walk a paginated chsi endpoint. Server caps page size at 10."""
    out, page = [], 1
    while page <= max_pages:
        payload = dict(body)
        payload["curPage"] = page
        payload["pageSize"] = 20
        res = chsi_post(path, payload)
        msg = res.get("msg")
        if not isinstance(msg, dict):
            raise RuntimeError(f"{path} page {page} returned {str(res)[:200]}")
        out.extend(msg.get("list") or [])
        if page >= (msg.get("totalPage") or 1) or not (msg.get("list") or []):
            break
        page += 1
    return out


def discover_majors(tag: str) -> list[dict]:
    d = cache_dir(tag)
    w = Writer(os.path.join(d, "majors.jsonl"), ("xwlx", "zydm", "yjxkdm"))
    if len(w.seen) > 0:
        print(f"[majors] cache already has {len(w.seen)} rows")
        w.close()
        return []

    ml = chsi_get_json("/zsml/code/ml.json")["dms"]
    zyly = chsi_get_json("/zsml/code/zyly.json")["dms"]
    found = 0

    # 学术学位: walk 门类 -> 一级学科 -> 专业
    for m in ml:
        xk_list = chsi_get_json(f"/zsml/code/yjxk/{m['dm']}.json")["dms"]
        for xk in xk_list:
            for row in paged(
                "/zsml/rs/zys.do", {"mldm": m["dm"], "yjxkdm": xk["dm"], "xxfs": ""}
            ):
                rec = {
                    "xwlx": "xs",
                    "mldm": m["dm"],
                    "mlmc": m["mc"],
                    "yjxkdm": row.get("yjxkdm") or xk["dm"],
                    "yjxkmc": row.get("yjxkmc") or xk["mc"],
                    "zydm": row["zydm"],
                    "zymc": row["zymc"],
                    "sign": row["sign"],
                }
                if w.add(rec):
                    found += 1
        print(f"[majors] 学术学位 {m['dm']} {m['mc']} done, total {len(w.seen)}")

    # 专业学位: 类别 (= 一级学科) 直接下钻
    for c in zyly:
        if not c.get("mc"):
            continue
        for row in paged("/zsml/rs/zys.do", {"mldm": c["dm"][:2], "yjxkdm": c["dm"], "xwlx": "zyxw"}):
            rec = {
                "xwlx": "zyxw",
                "mldm": c["dm"][:2],
                "mlmc": row.get("mlmc") or "",
                "yjxkdm": row.get("yjxkdm") or c["dm"],
                "yjxkmc": row.get("yjxkmc") or c["mc"],
                "zydm": row["zydm"],
                "zymc": row["zymc"],
                "sign": row["sign"],
            }
            if w.add(rec):
                found += 1
        print(f"[majors] 专业学位 {c['dm']} {c['mc']} done, total {len(w.seen)}")

    w.close()
    print(f"[majors] discovered {found}")
    return []


def load_jsonl(path: str) -> list[dict]:
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def scrape_details(tag: str, workers: int = 8, max_majors: int | None = None) -> None:
    d = cache_dir(tag)
    majors = load_jsonl(os.path.join(d, "majors.jsonl"))
    schools_w = Writer(os.path.join(d, "schools.jsonl"), ("zydm", "xwlx", "dwdm"))
    rows_w = Writer(os.path.join(d, "rows.jsonl"), ("zyyjfxdm",))
    done_path = os.path.join(d, "majors_done.txt")
    done = set()
    if os.path.exists(done_path):
        with open(done_path, encoding="utf-8") as f:
            done = {line.strip() for line in f if line.strip()}

    todo = [m for m in majors if f"{m['xwlx']}:{m['zydm']}:{m['yjxkdm']}" not in done]
    if max_majors:
        todo = todo[:max_majors]
    print(f"[details] majors total={len(majors)} done={len(done)} todo={len(todo)}")
    lock = threading.Lock()
    counters = {"schools": 0, "rows": 0, "fails": 0}
    done_fh = open(done_path, "a", encoding="utf-8")

    def work(m: dict):
        base = {
            "zydm": m["zydm"],
            "zymc": m["zymc"],
            "mldm": m["mldm"],
            "yjxkdm": m["yjxkdm"],
            "xwlx": m["xwlx"],
            "sign": m["sign"],
        }
        try:
            schools = paged("/zsml/rs/zydws.do", base)
        except Exception as e:  # noqa: BLE001
            print(f"[details] FAIL schools {m['zydm']} {m['zymc']}: {e}")
            with lock:
                counters["fails"] += 1
            return
        for s in schools:
            srec = {
                "zydm": m["zydm"],
                "zymc": m["zymc"],
                "mldm": m["mldm"],
                "mlmc": s.get("mlmc") or m.get("mlmc"),
                "yjxkdm": m["yjxkdm"],
                "yjxkmc": s.get("yjxkmc") or m.get("yjxkmc"),
                "xwlx": m["xwlx"],
                "xwlxmc": s.get("xwlxmc"),
                "schId": s.get("schId"),
                "dwdm": s.get("dwdm"),
                "dwmc": s.get("dwmc"),
                "ssdm": s.get("szssm"),
                "ssmc": s.get("szss"),
                "zhx": s.get("zhx"),
                "bs": s.get("bs"),
                "syl": s.get("syl"),
                "sign": s.get("sign"),
            }
            with lock:
                if schools_w.add(srec):
                    counters["schools"] += 1
            try:
                rows = paged(
                    "/zsml/rs/yjfxs.do",
                    dict(base, dwdm=s["dwdm"], dwmc=s["dwmc"], sign=s.get("sign")),
                )
            except Exception as e:  # noqa: BLE001
                print(f"[details] FAIL rows {m['zydm']} {s['dwmc']}: {e}")
                with lock:
                    counters["fails"] += 1
                continue
            with lock:
                for r in rows:
                    r["_zydm"] = m["zydm"]
                    r["_xwlx"] = m["xwlx"]
                    r["_mldm"] = m["mldm"]
                    r["_mlmc"] = s.get("mlmc") or m.get("mlmc")
                    r["_yjxkdm"] = m["yjxkdm"]
                    if rows_w.add(r):
                        counters["rows"] += 1

    t0 = time.time()
    try:
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = [pool.submit(work, m) for m in todo]
            for i, fut in enumerate(futures, 1):
                fut.result()
                if i % 20 == 0 or i == len(futures):
                    with lock:
                        done_fh.write(f"{m['xwlx']}:{m['zydm']}:{m['yjxkdm']}\n")
                        done_fh.flush()
                    rate = i / max(time.time() - t0, 1e-6)
                    print(
                        f"[details] {i}/{len(futures)} majors "
                        f"schools={counters['schools']} rows={counters['rows']} "
                        f"fails={counters['fails']} {rate:.1f} majors/s",
                        flush=True,
                    )
                    # mark progress properly for all finished ones
    finally:
        for m in todo:
            done_fh.write(f"{m['xwlx']}:{m['zydm']}:{m['yjxkdm']}\n")
        done_fh.flush()
        done_fh.close()
        schools_w.close()
        rows_w.close()

    meta = {
        "tag": tag,
        "schools": len(schools_w.seen),
        "rows": len(rows_w.seen),
        "majors": len(majors),
        "fails": counters["fails"],
        "seconds": round(time.time() - t0, 1),
        "finishedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    with open(os.path.join(d, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print("[details] DONE", json.dumps(meta, ensure_ascii=False))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="cache folder name, e.g. 2026")
    ap.add_argument("--stage", choices=["majors", "details", "all"], default="all")
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--max-majors", type=int, default=None)
    a = ap.parse_args()
    if a.stage in ("majors", "all"):
        discover_majors(a.tag)
    if a.stage in ("details", "all"):
        scrape_details(a.tag, workers=a.workers, max_majors=a.max_majors)


if __name__ == "__main__":
    main()
