"""Build the 2026 研招网 硕士专业目录 dataset school-by-school.

Why school-by-school: yz.chsi.com.cn only serves the first 10 rows of any
paged catalog query (later pages answer {"msg":"请登录"}), so the only way to
enumerate everything is to shrink each query until it fits in 10 rows. Querying
one 招生单位 at a time gives at most a handful of 专业 per 门类, and at most a
handful of 研究方向 per 专业, so nearly every query fits.

Stages (each resumable, all cached under scripts/scrape/_cache/<tag>/):
  schools  : enumerate 招生单位 codes from the 专业 -> 院校 endpoint
  majors   : for every school, list its 专业 via zys.do?dwdm=...
  rows     : for every (school, 专业), list 院系/方向/计划/科目 via yjfxs.do
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
from common import chsi_post  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
MLLIST = [
    ("01", "哲学", "xs"),
    ("02", "经济学", "xs"),
    ("03", "法学", "xs"),
    ("04", "教育学", "xs"),
    ("05", "文学", "xs"),
    ("06", "历史学", "xs"),
    ("07", "理学", "xs"),
    ("08", "工学", "xs"),
    ("09", "农学", "xs"),
    ("10", "医学", "xs"),
    ("11", "军事学", "xs"),
    ("12", "管理学", "xs"),
    ("13", "艺术学", "xs"),
    ("14", "交叉学科", "xs"),
]


def cache(tag: str) -> str:
    d = os.path.join(HERE, "_cache", tag)
    os.makedirs(d, exist_ok=True)
    return d


def read_jsonl(path: str) -> list[dict]:
    if not os.path.exists(path):
        return []
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    return out


class Jsonl:
    def __init__(self, path: str, keys: tuple[str, ...]):
        self.path, self.keys = path, keys
        self.lock = threading.Lock()
        self.seen = {tuple(str(r.get(k)) for k in keys) for r in read_jsonl(path)}
        self.fh = open(path, "a", encoding="utf-8")

    def add(self, rec: dict) -> bool:
        k = tuple(str(rec.get(x)) for x in self.keys)
        with self.lock:
            if k in self.seen:
                return False
            self.seen.add(k)
            self.fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
            self.fh.flush()
            return True

    def close(self):
        self.fh.close()


def page10(path: str, body: dict) -> tuple[list[dict], int]:
    """Fetch the only page the endpoint will serve; report the claimed total."""
    res = chsi_post(path, dict(body, start=0, curPage=1, pageSize=10))
    msg = res.get("msg")
    if not isinstance(msg, dict):
        raise RuntimeError(str(msg)[:80])
    return msg.get("list") or [], int(msg.get("totalCount") or 0)


def stage_schools(tag: str, workers: int = 6):
    """Enumerate 招生单位: union of schools appearing under every 门类."""
    d = cache(tag)
    w = Jsonl(os.path.join(d, "schools.jsonl"), ("dwdm",))
    if len(w.seen) > 50:
        print(f"[schools] cached {len(w.seen)}")
        w.close()
        return
    results, lock = [], threading.Lock()

    def work(m):
        dm = m[0]
        try:
            rows, _ = page10("/zsml/rs/zydws.do", {"mldm": dm, "xwlx": "xs"})
        except Exception as e:  # noqa: BLE001
            print(f"[schools] 门类 {dm} failed: {e}")
            return
        with lock:
            for r in rows:
                rec = {
                    "dwdm": r.get("dwdm"),
                    "dwmc": r.get("dwmc"),
                    "schId": r.get("schId"),
                    "ssdm": r.get("szssm"),
                    "ssmc": r.get("szss"),
                    "zhx": r.get("zhx"),
                    "bs": r.get("bs"),
                    "syl": r.get("syl"),
                }
                if w.add(rec):
                    results.append(rec)
        print(f"[schools] 门类 {dm} {m[1]} -> {len(rows)} (total {w.seen.__len__()})")

    with ThreadPoolExecutor(max_workers=workers) as pool:
        list(pool.map(work, MLLIST))
    w.close()
    print(f"[schools] done, {len(w.seen)} schools")


def stage_majors(tag: str, workers: int = 8):
    d = cache(tag)
    schools = read_jsonl(os.path.join(d, "schools.jsonl"))
    w = Jsonl(os.path.join(d, "school_majors.jsonl"), ("dwdm", "zydm", "xwlx"))
    done_p = os.path.join(d, "majors_done.txt")
    done = set()
    if os.path.exists(done_p):
        with open(done_p, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}
    todo = [s for s in schools if s["dwdm"] not in done]
    print(f"[majors] schools={len(schools)} done={len(done)} todo={len(todo)}")
    lock = threading.Lock()
    done_fh = open(done_p, "a", encoding="utf-8")
    fails = [0]

    def work(s):
        got = 0
        for dm, mlmc, xwlx in MLLIST:
            try:
                rows, total = page10(
                    "/zsml/rs/zys.do", {"mldm": dm, "xwlx": xwlx, "dwdm": s["dwdm"]}
                )
            except Exception as e:  # noqa: BLE001
                print(f"[majors] {s['dwmc']} {dm} fail: {e}")
                with lock:
                    fails[0] += 1
                continue
            with lock:
                for r in rows:
                    w.add(
                        {
                            "dwdm": s["dwdm"],
                            "dwmc": s["dwmc"],
                            "ssdm": s["ssdm"],
                            "ssmc": s["ssmc"],
                            "zhx": s["zhx"],
                            "bs": s["bs"],
                            "syl": s["syl"],
                            "xwlx": xwlx,
                            "mldm": dm,
                            "mlmc": mlmc,
                            "yjxkdm": r.get("yjxkdm"),
                            "yjxkmc": r.get("yjxkmc"),
                            "zydm": r.get("zydm"),
                            "zymc": r.get("zymc"),
                            "sign": r.get("sign"),
                            "truncated": total > len(rows),
                            "total": total,
                        }
                    )
                got += len(rows)
        with lock:
            done_fh.write(s["dwdm"] + "\n")
            done_fh.flush()
        return got

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, n in enumerate(pool.map(work, todo), 1):
            if i % 10 == 0 or i == len(todo):
                print(
                    f"[majors] {i}/{len(todo)} schools, pairs={len(w.seen)} "
                    f"fails={fails[0]} {i/max(time.time()-t0,1e-9):.1f} sch/s",
                    flush=True,
                )
    done_fh.close()
    w.close()
    print(f"[majors] done pairs={len(w.seen)}")


def stage_rows(tag: str, workers: int = 8, limit: int | None = None):
    d = cache(tag)
    pairs = read_jsonl(os.path.join(d, "school_majors.jsonl"))
    w = Jsonl(os.path.join(d, "rows.jsonl"), ("zyyjfxdm",))
    done_p = os.path.join(d, "rows_done.txt")
    done = set()
    if os.path.exists(done_p):
        with open(done_p, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}
    todo = [p for p in pairs if f"{p['dwdm']}:{p['zydm']}:{p['xwlx']}" not in done]
    if limit:
        todo = todo[:limit]
    print(f"[rows] pairs={len(pairs)} done={len(done)} todo={len(todo)}")
    lock = threading.Lock()
    done_fh = open(done_p, "a", encoding="utf-8")
    fails = [0]

    def work(p):
        body = {
            "zydm": p["zydm"],
            "zymc": p["zymc"],
            "dwdm": p["dwdm"],
            "dwmc": p["dwmc"],
            "xwlx": p["xwlx"],
            "mldm": p["mldm"],
            "yjxkdm": p["yjxkdm"],
            "sign": p["sign"],
            "dwlxs": ["all"],
            "xxfs": "",
            "tydxs": "",
            "jsggjh": "",
            "jsxbjh": "",
        }
        try:
            rows, total = page10("/zsml/rs/yjfxs.do", body)
        except Exception as e:  # noqa: BLE001
            print(f"[rows] {p['dwmc']} {p['zydm']} fail: {e}")
            with lock:
                fails[0] += 1
            return
        with lock:
            for r in rows:
                r["_dwdm"] = p["dwdm"]
                r["_dwmc"] = p["dwmc"]
                r["_ssdm"] = p.get("ssdm")
                r["_ssmc"] = p.get("ssmc")
                r["_zhx"] = p.get("zhx")
                r["_bs"] = p.get("bs")
                r["_syl"] = p.get("syl")
                r["_mldm"] = p["mldm"]
                r["_mlmc"] = p["mlmc"]
                r["_yjxkdm"] = p["yjxkdm"]
                r["_yjxkmc"] = p["yjxkmc"]
                r["_xwlx"] = p["xwlx"]
                r["_total"] = total
                r["_truncated"] = total > len(rows)
                w.add(r)
        with lock:
            done_fh.write(f"{p['dwdm']}:{p['zydm']}:{p['xwlx']}\n")
            done_fh.flush()

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, _ in enumerate(pool.map(work, todo), 1):
            if i % 50 == 0 or i == len(todo):
                print(
                    f"[rows] {i}/{len(todo)} rows={len(w.seen)} fails={fails[0]} "
                    f"{i/max(time.time()-t0,1e-9):.1f} pair/s",
                    flush=True,
                )
    done_fh.close()
    w.close()
    meta = {
        "tag": tag,
        "schools": len(read_jsonl(os.path.join(d, "schools.jsonl"))),
        "pairs": len(pairs),
        "rows": len(w.seen),
        "fails": fails[0],
        "seconds": round(time.time() - t0, 1),
        "finishedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    with open(os.path.join(d, "rows_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print("[rows] DONE", json.dumps(meta, ensure_ascii=False))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="2026")
    ap.add_argument("--stage", required=True, choices=["schools", "majors", "rows", "all"])
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--limit", type=int, default=None)
    a = ap.parse_args()
    if a.stage in ("schools", "all"):
        stage_schools(a.tag, a.workers)
    if a.stage in ("majors", "all"):
        stage_majors(a.tag, a.workers)
    if a.stage in ("rows", "all"):
        stage_rows(a.tag, a.workers, a.limit)


if __name__ == "__main__":
    main()
