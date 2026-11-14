"""Choose which 招生单位 to crawl for the 2026 catalog.

Selection is reproducible from names, not hand-typed codes: every 985 is always
included, then 双一流 schools in the user's target provinces, capped per
province so one region cannot dominate. Codes come from the harvested 院校库.
"""
from __future__ import annotations

import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

C985 = [
    "北京大学", "清华大学", "中国人民大学", "北京航空航天大学", "北京理工大学",
    "北京师范大学", "中国农业大学", "中央民族大学", "南开大学", "天津大学",
    "大连理工大学", "东北大学", "吉林大学", "哈尔滨工业大学", "复旦大学",
    "同济大学", "上海交通大学", "华东师范大学", "南京大学", "东南大学",
    "浙江大学", "中国科学技术大学", "厦门大学", "山东大学", "中国海洋大学",
    "武汉大学", "华中科技大学", "湖南大学", "中南大学", "中山大学", "华南理工大学",
    "四川大学", "电子科技大学", "重庆大学", "西安交通大学", "西北工业大学",
    "西北农林科技大学", "兰州大学", "国防科技大学",
]

# 考研热度高、数据价值大的 211 / 双一流 / 强校（按地区成组，便于按需扩表）
EXTRA = [
    # 北京
    "北京交通大学", "北京工业大学", "北京科技大学", "北京化工大学", "北京邮电大学",
    "北京林业大学", "北京中医药大学", "首都师范大学", "北京外国语大学", "中国传媒大学",
    "中央财经大学", "对外经济贸易大学", "中国政法大学", "华北电力大学", "中国矿业大学(北京)",
    "中国石油大学(北京)", "中国地质大学(北京)", "北京协和医学院", "中国科学院大学",
    "中国社会科学院大学", "中央美术学院", "北京电影学院",
    # 天津 / 河北
    "天津医科大学", "河北工业大学", "燕山大学", "河北大学",
    # 东北
    "哈尔滨工程大学", "东北林业大学", "东北农业大学", "东北师范大学", "延边大学",
    "大连海事大学", "辽宁大学", "中国医科大学", "东北财经大学", "沈阳药科大学",
    "吉林大学", "哈尔滨医科大学",
    # 上海
    "华东理工大学", "东华大学", "上海大学", "上海财经大学", "上海外国语大学",
    "上海中医药大学", "上海海洋大学", "上海体育大学", "上海音乐学院", "华东政法大学",
    # 江苏
    "苏州大学", "南京航空航天大学", "南京理工大学", "中国矿业大学", "南京邮电大学",
    "河海大学", "江南大学", "南京林业大学", "南京信息工程大学", "南京农业大学",
    "南京医科大学", "中国药科大学", "南京师范大学", "南京中医药大学",
    # 浙江
    "宁波大学", "浙江工业大学", "杭州电子科技大学", "浙江师范大学", "中国美术学院",
    # 安徽 / 福建 / 江西
    "合肥工业大学", "安徽大学", "安徽医科大学", "福州大学", "福建师范大学", "南昌大学",
    "江西财经大学", "江西师范大学",
    # 山东 / 河南
    "中国石油大学(华东)", "青岛大学", "山东科技大学", "山东师范大学", "郑州大学",
    "河南大学", "河南师范大学",
    # 湖北 / 湖南
    "武汉理工大学", "中国地质大学(武汉)", "华中农业大学", "华中师范大学",
    "中南财经政法大学", "湖北大学", "武汉科技大学", "湘潭大学", "湖南师范大学",
    "南华大学",
    # 广东 / 广西
    "暨南大学", "华南师范大学", "华南农业大学", "广州中医药大学", "南方医科大学",
    "深圳大学", "南方科技大学", "广东工业大学", "广州大学", "广西大学",
    # 川渝 / 贵州 / 云南
    "西南交通大学", "西南财经大学", "四川农业大学", "成都理工大学", "西南石油大学",
    "成都中医药大学", "四川师范大学", "西南大学", "重庆医科大学", "西南政法大学",
    "重庆邮电大学", "贵州大学", "云南大学", "昆明理工大学", "云南师范大学",
    # 西北
    "西北大学", "西安电子科技大学", "长安大学", "陕西师范大学", "西安理工大学",
    "西安建筑科技大学", "西北政法大学", "兰州理工大学", "西北师范大学",
    "宁夏大学", "新疆大学", "石河子大学", "青海大学", "西藏大学",
]

EXCLUDE = {"国防科技大学"}

MAX_PER_PROVINCE = 14


def main() -> int:
    lib = json.load(open(os.path.join(HERE, "_cache", "schools_library.json"), encoding="utf-8"))
    by_name = {}
    for r in lib:
        by_name.setdefault(r["dwmc"], r)
    chosen: dict[str, dict] = {}

    def take(name: str) -> bool:
        r = by_name.get(name)
        if not r or not r.get("dwdm"):
            return False
        chosen[r["dwdm"]] = r
        return True

    for n in C985:
        if n in EXCLUDE:
            continue
        take(n)

    per_prov: dict[str, int] = {}
    for r in chosen.values():
        per_prov[r["ssdm"]] = per_prov.get(r["ssdm"], 0) + 1
    skipped = []
    for n in EXTRA:
        r = by_name.get(n)
        if not r:
            skipped.append(n)
            continue
        if r["dwdm"] in chosen:
            continue
        prov = r["ssdm"]
        if per_prov.get(prov, 0) >= MAX_PER_PROVINCE:
            skipped.append(n)
            continue
        chosen[r["dwdm"]] = r
        per_prov[prov] = per_prov.get(prov, 0) + 1

    out = sorted(chosen.values(), key=lambda x: (x["ssdm"], x["dwdm"]))
    dst = os.path.join(HERE, "_cache", "target_2026.json")
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"[target] selected {len(out)} schools -> {dst}")
    print("[target] by province:", {k: v for k, v in sorted(per_prov.items()) if v})
    missing = [n for n in C985 + EXTRA if n not in by_name and n not in EXCLUDE]
    if missing:
        print("[target] names not found in 院校库:", missing)
    if skipped:
        print(f"[target] skipped by cap/dup: {len(skipped)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
