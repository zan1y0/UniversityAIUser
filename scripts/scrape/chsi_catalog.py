"""Collect the 2026 研招网 硕士专业目录 (yz.chsi.com.cn/zsml) into local caches.

Server behaviour that shapes this design (verified against the live site):
  * every paged catalog query only serves rows at start=0; later pages answer
    {"msg":"请登录"} until one signs in, so a query must fit in 10 rows
  * zys.do needs 门类 + 一级学科, zydws.do needs a 专业, yjfxs.do needs 专业 + 院校
  * the host answers "访问太频繁" above roughly 1-2 requests/second, so a global
    gate serialises calls instead of bursting

Stages (resumable, cached under scripts/scrape/_cache/<tag>/):
  pairs : for each 专业, the schools the catalog lists on its first page
  rows  : per 专业 x 院校, the 院系/方向/计划/科目 rows
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
            if not line:
                continue
            try:
                out.append(json.loads(line))
            except json.JSONDecodeError:
                pass
    return out


class Jsonl:
    """Append-only JSONL with an in-memory key set, safe across threads."""

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


def call(path: str, body: dict, retries: int = 6) -> tuple[list[dict], int]:
    """One start=0 query. Backs off on the host's rate-limit message."""
    for attempt in range(retries):
        try:
            res = chsi_post(path, dict(body, start=0, curPage=1, pageSize=10))
        except Exception:  # noqa: BLE001 - network layer
            if attempt == retries - 1:
                raise
            time.sleep(2 ** attempt)
            continue
        msg = res.get("msg")
        if isinstance(msg, dict):
            return msg.get("list") or [], int(msg.get("totalCount") or 0)
        text = str(msg)
        if "频繁" in text or "稍后" in text:
            time.sleep(min(20, 2 ** attempt + 1))
            continue
        raise RuntimeError(text[:60])
    raise RuntimeError("retries exhausted")


def stage_pairs(tag: str, workers: int = 2, first_only: bool = True):
    """One query per 专业: the schools the catalog lists first (max 10 rows)."""
    d = cache(tag)
    majors = read_jsonl(os.path.join(d, "majors.jsonl"))
    pairs = Jsonl(os.path.join(d, "pairs.jsonl"), ("dwdm", "zydm", "xwlx"))
    done_p = os.path.join(d, "pairs_done.txt")
    done = set()
    if os.path.exists(done_p):
        with open(done_p, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}
    todo = [m for m in majors if f"{m['xwlx']}:{m['zydm']}" not in done]
    print(f"[pairs] majors={len(majors)} done={len(done)} todo={len(todo)} firstOnly={first_only}")
    lock = threading.Lock()
    done_fh = open(done_p, "a", encoding="utf-8")
    stat = {"fail": 0, "unlisted": 0}

    def work(m: dict):
        base = {
            "zydm": m["zydm"], "zymc": m["zymc"], "mldm": m["mldm"],
            "yjxkdm": m["yjxkdm"], "xwlx": m["xwlx"], "sign": m["sign"],
            "dwmc": "", "ssdm": "", "xxfs": "", "tydxs": "", "jsggjh": "",
            "jsxbjh": "", "dwlxs[0]": "all",
        }
        try:
            rows, total = call("/zsml/rs/zydws.do", base)
        except Exception as e:  # noqa: BLE001
            print(f"[pairs] {m['zydm']} {m['zymc']} fail: {e}")
            with lock:
                stat["fail"] += 1
            return
        anchors: list[str] = []
        while True:
            with lock:
                for s in rows:
                    pairs.add(
                        {
                            "dwdm": s.get("dwdm"), "dwmc": s.get("dwmc"),
                            "schId": s.get("schId"), "ssdm": s.get("szssm"),
                            "ssmc": s.get("szss"), "zhx": s.get("zhx"),
                            "bs": s.get("bs"), "syl": s.get("syl"),
                            "zydm": m["zydm"], "zymc": m["zymc"], "xwlx": m["xwlx"],
                            "mldm": m["mldm"], "yjxkdm": m["yjxkdm"], "sign": m["sign"],
                            "majorTotal": total,
                        }
                    )
                if total > len(rows):
                    stat["unlisted"] += total - len(rows)
            if first_only:
                break
            anchor = rows[-1].get("dwdm") if rows else None
            if not anchor or anchor in anchors or len(anchors) > 60:
                break
            anchors.append(anchor)
            try:
                rows, total = call("/zsml/rs/zydws.do", {**base, "dwdm": anchor})
            except Exception as e:  # noqa: BLE001
                print(f"[pairs] page fail {m['zydm']} anchor {anchor}: {e}")
                with lock:
                    stat["fail"] += 1
                break
        with lock:
            done_fh.write(f"{m['xwlx']}:{m['zydm']}\n")
            done_fh.flush()

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, _ in enumerate(pool.map(work, todo), 1):
            if i % 25 == 0 or i == len(todo):
                print(
                    f"[pairs] {i}/{len(todo)} pairs={len(pairs.seen)} "
                    f"unlisted={stat['unlisted']} fail={stat['fail']} "
                    f"{i/max(time.time()-t0,1e-9):.2f} major/s",
                    flush=True,
                )
    done_fh.close()
    pairs.close()
    print(f"[pairs] done pairs={len(pairs.seen)} unlistedSchoolSlots={stat['unlisted']}")


def stage_rows(tag: str, workers: int = 2, limit: int | None = None,
               only: list[str] | None = None, pairs_file: str | None = None):
    """Per 专业 x 院校 rows: 院系, 方向, 计划, 推免, 考试科目."""
    d = cache(tag)
    src = os.path.join(d, pairs_file or "pairs.jsonl")
    pairs = read_jsonl(src)
    if only:
        allow = set(only)
        pairs = [p for p in pairs if p.get("dwdm") in allow]
    rows_w = Jsonl(os.path.join(d, "rows.jsonl"), ("zyyjfxdm",))
    done_p = os.path.join(d, "rows_done.txt")
    done = set()
    if os.path.exists(done_p):
        with open(done_p, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}
    todo = [p for p in pairs if f"{p['dwdm']}:{p['zydm']}:{p['xwlx']}" not in done]
    if limit:
        todo = todo[:limit]
    print(f"[rows] source={os.path.basename(src)} pairs={len(pairs)} done={len(done)} todo={len(todo)}")
    lock = threading.Lock()
    done_fh = open(done_p, "a", encoding="utf-8")
    stat = {"fail": 0, "trunc": 0}

    def work(p: dict):
        body = {
            "zydm": p["zydm"], "zymc": p.get("zymc"), "dwdm": p["dwdm"],
            "dwmc": p.get("dwmc"), "xwlx": p["xwlx"], "mldm": p["mldm"],
            "yjxkdm": p["yjxkdm"], "sign": p.get("sign"), "dwlxs": ["all"],
        }
        try:
            rows, total = call("/zsml/rs/yjfxs.do", body)
        except Exception as e:  # noqa: BLE001
            print(f"[rows] {p.get('dwmc')} {p['zydm']} fail: {e}")
            with lock:
                stat["fail"] += 1
            return
        stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        with lock:
            for r in rows:
                r.update(
                    {
                        "_dwdm": p["dwdm"], "_dwmc": p.get("dwmc"),
                        "_ssdm": p.get("ssdm"), "_ssmc": p.get("ssmc"),
                        "_zhx": p.get("zhx"), "_bs": p.get("bs"), "_syl": p.get("syl"),
                        "_mldm": p["mldm"], "_yjxkdm": p["yjxkdm"], "_xwlx": p["xwlx"],
                        "_total": total, "_truncated": total > len(rows),
                        "_retrievedAt": stamp,
                    }
                )
                rows_w.add(r)
            if total > len(rows):
                stat["trunc"] += 1
        with lock:
            done_fh.write(f"{p['dwdm']}:{p['zydm']}:{p['xwlx']}\n")
            done_fh.flush()

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, _ in enumerate(pool.map(work, todo), 1):
            if i % 50 == 0 or i == len(todo):
                print(
                    f"[rows] {i}/{len(todo)} rows={len(rows_w.seen)} trunc={stat['trunc']} "
                    f"fail={stat['fail']} {i/max(time.time()-t0,1e-9):.3f} pair/s",
                    flush=True,
                )
    done_fh.close()
    rows_w.close()
    meta = {
        "tag": tag, "source": os.path.basename(src), "pairs": len(pairs),
        "rows": len(rows_w.seen), "fails": stat["fail"],
        "truncatedPairs": stat["trunc"], "seconds": round(time.time() - t0, 1),
        "finishedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    with open(os.path.join(d, "rows_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print("[rows] DONE", json.dumps(meta, ensure_ascii=False))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="2026")
    ap.add_argument("--stage", required=True, choices=["pairs", "rows", "all"])
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--limit", type=int, default=None)
    ap.add_argument("--schools", default=None, help="comma separated dwdm allow-list")
    ap.add_argument("--pairs-file", default=None, help="pairs jsonl name under the tag cache")
    ap.add_argument("--walk-pairs", action="store_true", help="walk every school page (slow)")
    a = ap.parse_args()
    only = a.schools.split(",") if a.schools else None
    if a.stage in ("pairs", "all"):
        stage_pairs(a.tag, a.workers, first_only=not a.walk_pairs)
    if a.stage in ("rows", "all"):
        stage_rows(a.tag, a.workers, a.limit, only, a.pairs_file)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
