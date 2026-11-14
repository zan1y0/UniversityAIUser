"""Shared HTTP session for scraping public Chinese graduate-admission catalogs.

No third-party dependencies: urllib + http.cookiejar only. A real cookie session
is required: yz.chsi.com.cn answers "请登录" on paged POSTs when each request
arrives without the session cookie established by the catalog page.
"""
from __future__ import annotations

import gzip
import json
import random
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import http.cookiejar

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)
CHSI_BASE = "https://yz.chsi.com.cn"
CHSI_HEADERS = {
    "User-Agent": UA,
    "Referer": CHSI_BASE + "/zsml/",
    "Origin": CHSI_BASE,
    "X-Requested-With": "XMLHttpRequest",
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

JAR = http.cookiejar.CookieJar()
OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(JAR))
# The host answers "访问太频繁" above roughly 2 requests/second, and "请登录"
# whenever a paged request goes past row 10. A global gate keeps us polite.
MIN_INTERVAL = 0.55
_last = [0.0]
_gate = threading.Lock()
_warm = [False]


def _throttle():
    with _gate:
        gap = time.time() - _last[0]
        if gap < MIN_INTERVAL:
            time.sleep(MIN_INTERVAL - gap)
        _last[0] = time.time()


def _open(req, timeout=45, retries=4):
    last = None
    for attempt in range(retries):
        _throttle()
        try:
            with OPENER.open(req, timeout=timeout) as r:
                raw = r.read()
                if r.headers.get("Content-Encoding") == "gzip":
                    raw = gzip.decompress(raw)
                return raw
        except urllib.error.HTTPError as e:
            last = e
            if e.code in (403, 429, 500, 502, 503, 504):
                time.sleep(2 ** attempt + random.random())
                continue
            raise
        except Exception as e:  # noqa: BLE001 - network layer
            last = e
            time.sleep(2 ** attempt + random.random())
    raise RuntimeError(f"fetch failed after {retries} tries: {last}")


def warm_up():
    """Visit the catalog page once so the session cookie is established."""
    if _warm[0]:
        return
    req = urllib.request.Request(
        CHSI_BASE + "/zsml/",
        headers={
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9",
        },
    )
    _open(req)
    _warm[0] = True


def chsi_post(path: str, data: dict) -> dict:
    warm_up()
    body = urllib.parse.urlencode(data, doseq=True).encode()
    req = urllib.request.Request(CHSI_BASE + path, data=body, headers=CHSI_HEADERS)
    raw = _open(req).decode("utf-8", "replace")
    try:
        return json.loads(raw)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"non-JSON from {path}: {raw[:200]}") from e


def chsi_get_json(path: str) -> dict:
    warm_up()
    req = urllib.request.Request(CHSI_BASE + path, headers=CHSI_HEADERS)
    return json.loads(_open(req).decode("utf-8", "replace"))


def get_bytes(url: str, referer: str | None = None, retries: int = 4) -> bytes:
    """Raw bytes for binary downloads such as PDF attachments."""
    headers = {
        "User-Agent": UA,
        "Accept": "application/pdf,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    return _open(req, retries=retries)


def get_text(url: str, referer: str | None = None, retries: int = 4) -> str:
    headers = {
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    raw = _open(req, retries=retries)
    for enc in ("utf-8", "gb18030"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", "replace")
