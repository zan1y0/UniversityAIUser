# -*- coding: utf-8 -*-
import re

s = open(r"D:\STUDY\UniversityAIUser\database\model\01_schema.mysql.sql", encoding="utf-8").read()
cur = ("", "")
for line in s.splitlines():
    st = line.strip()
    m = re.match(r"-- (.+?) \| (.+)$", st)
    if m:
        cur = (m.group(1), m.group(2))
    t = re.match(r"CREATE TABLE IF NOT EXISTS `(\w+)`", st)
    if t:
        print("%-32s %-12s %s" % (t.group(1), cur[1], cur[0]))

print("---- columns of key tables ----")
for tab in ("app_users", "sys_user_roles", "teacher_students", "teaching_tasks"):
    block = re.search(r"CREATE TABLE IF NOT EXISTS `%s` \((.*?)\n\)" % tab, s, re.S)
    print("##", tab)
    print(block.group(1)[:900] if block else "NOT FOUND")
