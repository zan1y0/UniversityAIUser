"""Crawl 掌上考研/中国教育在线 (kaoyan.cn) for 2023-2026 per-major admission data.

Why this source: 研招网 keeps only the current-year catalog, so 2023-2025 cannot be
read from it; kaoyan.cn aggregates each school's official 招生计划 and 复试线 by
year and exposes them as JSON, which is the only machine-readable historical
source found that covers ordinary 本科院校 as well as 双一流.

Endpoints (POST, JSON body, no auth observed):
  /pc/school/schoolScore  {school_id, year, degree_type}
      -> per 院系 x 专业 复试线: politics/english/special_one/special_two/total
  /pc/school/planListV2   {school_id, year, limit}
      -> per 院系 x 专业 招生计划: recruit_number, 学习方式, 考试方式, 学位类型
Static metadata:
  https://static.kaoyan.cn/json/school/code.json        招生单位 -> school_id
  https://static.kaoyan.cn/json/school/<id>/info.json   name/province/985/211/双一流
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
from common import get_bytes  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "_cache", "kaoyan_cn")
API = "https://api.kaoyan.cn"
STATIC = "https://static.kaoyan.cn"
YEARS = [2023, 2024, 2025, 2026]

_lock = threading.Lock()


def post(path: str, payload: dict, retries: int = 4) -> dict:
    import urllib.request

    body = json.dumps(payload).encode()
    for attempt in range(retries):
        req = urllib.request.Request(
            API + path,
            data=body,
            headers={
                "Content-Type": "application/json",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126",
                "Origin": "https://www.kaoyan.cn",
                "Referer": "https://www.kaoyan.cn/",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8", "replace"))
        except Exception:  # noqa: BLE001
            if attempt == retries - 1:
                raise
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError("unreachable")


def get_json(url: str, retries: int = 4) -> dict:
    for attempt in range(retries):
        try:
            return json.loads(get_bytes(url, retries=1).decode("utf-8", "replace"))
        except Exception:  # noqa: BLE001
            if attempt == retries - 1:
                raise
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError("unreachable")


def rows_of(res: dict) -> list[dict]:
    data = res.get("data")
    if isinstance(data, dict):
        return data.get("data") or []
    if isinstance(data, list):
        return data
    return []


def load_schools() -> list[dict]:
    raw = get_json(STATIC + "/json/school/code.json")["data"]
    out = []
    for code, info in raw.items():
        out.append({"school_id": info["id"], "code": code, "name": info["n"], "pid": info.get("pid")})
    out.sort(key=lambda x: x["school_id"])
    return out


def stage_schools(workers: int = 8) -> None:
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, "schools.jsonl")
    done = set()
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    done.add(json.loads(line)["school_id"])
    schools = [s for s in load_schools() if s["school_id"] not in done]
    print(f"[schools] todo {len(schools)} (cached {len(done)})")

    def work(s):
        try:
            info = get_json(f"{STATIC}/json/school/{s['school_id']}/info.json").get("data") or {}
        except Exception:  # noqa: BLE001
            info = {}
        rec = {
            **s,
            "province": info.get("province"),
            "features": info.get("feature") or [],
            "is_985": info.get("is_985"),
            "is_211": info.get("is_211"),
            "is_syl": info.get("is_syl"),
            "is_zihuaxian": info.get("is_zihuaxian"),
            "is_ordinary": info.get("is_ordinary"),
            "school_site": info.get("school_site") or [],
        }
        with _lock:
            with open(path, "a", encoding="utf-8") as f:
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, _ in enumerate(pool.map(work, schools), 1):
            if i % 100 == 0 or i == len(schools):
                print(f"[schools] {i}/{len(schools)} {i/max(time.time()-t0,1e-9):.1f}/s", flush=True)


def stage_data(workers: int = 8, years: list[int] | None = None) -> None:
    os.makedirs(CACHE, exist_ok=True)
    years = years or YEARS
    schools = [json.loads(x) for x in open(os.path.join(CACHE, "schools.jsonl"), encoding="utf-8") if x.strip()]
    done_p = os.path.join(CACHE, "data_done.txt")
    done = set()
    if os.path.exists(done_p):
        with open(done_p, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}
    todo = [s for s in schools if str(s["school_id"]) not in done]
    print(f"[data] schools todo {len(todo)} / {len(schools)}")

    f_score = open(os.path.join(CACHE, "scores.jsonl"), "a", encoding="utf-8")
    f_plan = open(os.path.join(CACHE, "plans.jsonl"), "a", encoding="utf-8")
    done_fh = open(done_p, "a", encoding="utf-8")
    stat = {"score": 0, "plan": 0, "fail": 0}

    def work(s):
        sid = str(s["school_id"])
        got_s = got_p = 0
        for y in years:
            try:
                sc = rows_of(post("/pc/school/schoolScore", {"school_id": sid, "year": y, "degree_type": ""}))
            except Exception:  # noqa: BLE001
                sc = []
                with _lock:
                    stat["fail"] += 1
            try:
                pl = rows_of(post("/pc/school/planListV2", {"school_id": sid, "year": y, "limit": 300}))
            except Exception:  # noqa: BLE001
                pl = []
                with _lock:
                    stat["fail"] += 1
            with _lock:
                for r in sc:
                    r["_school_id"] = s["school_id"]
                    r["_school_name"] = s["name"]
                    r["_year"] = y
                    f_score.write(json.dumps(r, ensure_ascii=False) + "\n")
                for r in pl:
                    r["_school_id"] = s["school_id"]
                    r["_school_name"] = s["name"]
                    r["_year"] = y
                    f_plan.write(json.dumps(r, ensure_ascii=False) + "\n")
                stat["score"] += len(sc)
                stat["plan"] += len(pl)
                got_s += len(sc)
                got_p += len(pl)
        with _lock:
            f_score.flush()
            f_plan.flush()
            done_fh.write(sid + "\n")
            done_fh.flush()
        return got_s + got_p

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, _ in enumerate(pool.map(work, todo), 1):
            if i % 50 == 0 or i == len(todo):
                print(
                    f"[data] {i}/{len(todo)} score={stat['score']} plan={stat['plan']} "
                    f"fail={stat['fail']} {i/max(time.time()-t0,1e-9):.1f} sch/s",
                    flush=True,
                )
    f_score.close()
    f_plan.close()
    done_fh.close()
    meta = {"schools": len(schools), "years": years, "scores": stat["score"], "plans": stat["plan"],
            "fails": stat["fail"], "seconds": round(time.time() - t0, 1)}
    with open(os.path.join(CACHE, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print("[data] DONE", json.dumps(meta, ensure_ascii=False))


def stage_plan_v1(workers: int = 10, years: list[int] | None = None) -> None:
    """planList (v1) keeps older years: it answers year=2023 while planListV2 does not."""
    os.makedirs(CACHE, exist_ok=True)
    years = years or [2023]
    schools = [json.loads(x) for x in open(os.path.join(CACHE, "schools.jsonl"), encoding="utf-8") if x.strip()]
    done_p = os.path.join(CACHE, "plan_v1_done.txt")
    done = set()
    if os.path.exists(done_p):
        with open(done_p, encoding="utf-8") as f:
            done = {x.strip() for x in f if x.strip()}
    todo = [s for s in schools if str(s["school_id"]) not in done]
    print(f"[plan-v1] todo {len(todo)} / {len(schools)} years={years}")
    fh = open(os.path.join(CACHE, "plans_v1.jsonl"), "a", encoding="utf-8")
    done_fh = open(done_p, "a", encoding="utf-8")
    stat = {"rows": 0, "fail": 0}

    def work(s):
        sid = str(s["school_id"])
        for y in years:
            try:
                rows = rows_of(post("/pc/school/planList", {"school_id": sid, "limit": 300, "year": y}))
            except Exception:  # noqa: BLE001
                rows = []
                with _lock:
                    stat["fail"] += 1
            with _lock:
                for r in rows:
                    r["_school_id"] = s["school_id"]
                    r["_school_name"] = s["name"]
                    r["_year"] = y
                    fh.write(json.dumps(r, ensure_ascii=False) + "\n")
                stat["rows"] += len(rows)
                fh.flush()
        with _lock:
            done_fh.write(sid + "\n")
            done_fh.flush()

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i, _ in enumerate(pool.map(work, todo), 1):
            if i % 100 == 0 or i == len(todo):
                print(f"[plan-v1] {i}/{len(todo)} rows={stat['rows']} fail={stat['fail']} "
                      f"{i/max(time.time()-t0,1e-9):.1f} sch/s", flush=True)
    fh.close()
    done_fh.close()
    print(f"[plan-v1] DONE rows={stat['rows']} fails={stat['fail']}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--stage", required=True, choices=["schools", "data", "plan_v1", "all"])
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--years", default=",".join(str(y) for y in YEARS))
    a = ap.parse_args()
    years = [int(x) for x in a.years.split(",") if x.strip()]
    if a.stage in ("schools", "all"):
        stage_schools(a.workers)
    if a.stage in ("data", "all"):
        stage_data(a.workers, years)
    if a.stage == "plan_v1":
        stage_plan_v1(a.workers)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
