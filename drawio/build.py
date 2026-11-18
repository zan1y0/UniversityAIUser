# -*- coding: utf-8 -*-
"""Build every route's draw.io wireframe for 研途 AI."""

import os

import lib
from lib import Canvas, esc, text_h, tw, mxfile, page, INK, MUTED, FAINT, LINE, WHITE, PRIMARY, SOFT, ACCENT, WARN, WARNSOFT, WARNLINE, OK, OKSOFT, DANGER
from render import (
    PW, MARGIN, CW, PAD, RENDER, card, chip_row, tag_pill, placeholder,
    page_header, blk_row, blk_split, blk_annotation, blk_state, blk_banner, blk_card,
)
import pages as P
from contract_map import CONTRACT

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")


def measure(fn, *a):
    return fn(Canvas(), *a)


# ------------------------------------------------------------------ bespoke pages
def table(c, x, y, w, headers, rows, widths, rh=30, size=10):
    cw = [w * v for v in widths]
    colx = [x + sum(cw[:i]) for i in range(len(cw))]
    c.box(x, y, w, rh, "", SOFT, SOFT, arc=6)
    for i, hd in enumerate(headers):
        c.label(colx[i] + 10, y + 7, cw[i] - 14, 16, hd, size, PRIMARY, 1)
    cy = y + rh
    for row in rows:
        hh = max(text_h(cell, size, cw[i] - 16) for i, cell in enumerate(row)) + 16
        for i, cell in enumerate(row):
            color = INK if i == 0 else MUTED
            c.label(colx[i] + 10, cy + 8, cw[i] - 14, hh - 12, cell, size, color, 1 if i == 0 else 0)
        cy += hh
        c.hr(x, cy, w, "#F1F5F9")
    return cy - y


def draw_overview(c, y0):
    y = y0
    c.pill(MARGIN, y, 132, 24, "页面地图", INK, INK, WHITE, 11, 1)
    c.label(MARGIN + 144, y + 3, 900, 18, "路由 / 组件 / 模块编码 / 可见性 一览", 10, FAINT)
    y += 40

    # legend
    lx = MARGIN
    items = [("学生工作空间", "#EEF2FF", PRIMARY), ("教学空间", "#ECFDF5", OK),
             ("管理后台", "#FEF2F2", DANGER), ("公开页", "#F5F3FF", "#6D28D9"),
             ("异常页", "#F3F4F6", MUTED)]
    for text, fill, color in items:
        c.pill(lx, y, tw(text, 10) + 22, 22, text, fill, fill, color, 10, 0)
        lx += tw(text, 10) + 30
    y += 40

    # band 1: shell
    c.box(MARGIN, y, CW, 96, "", "#FCFCFD", LINE, arc=10)
    c.label(MARGIN + 18, y + 12, 420, 18, "① 应用外壳  App.vue（除 /login、/activate 外全部套用）", 11, INK, 1)
    parts = [("侧边栏 .sidebar", 210), (".topbar 顶栏", 250), (".page-heading 标题区", 290),
             ("demo-banner / notice", 230), ("RouterView 内容区", 250), ("footer 页脚", 130)]
    total = sum(w for _, w in parts)
    avail = CW - 36 - 8 * (len(parts) - 1)
    px = MARGIN + 18
    for text, wid in parts:
        real = wid * avail / total
        placeholder(c, px, y + 40, real, 40, text, 10)
        px += real + 8
    y += 116

    # band 2: 11 modules
    c.label(MARGIN, y, 600, 18, "② 学习模块  M01–M11（学生侧栏按 概览 / 学习 / 提升 / 资讯 分组）", 11, INK, 1)
    y += 24
    mods = [("M10", "学习工作台", "/dashboard"), ("M01", "数字人陪伴督学", "/companion"),
            ("M02", "智能择校与专业分析", "/schools"), ("M03", "公共课智能辅导", "/public-courses"),
            ("M04", "专业课学习支持", "/professional-courses"), ("M05", "AI 答疑与解题", "/qa"),
            ("M06", "刷题与错题专项训练", "/practice"), ("M07", "个性化学习规划", "/planning"),
            ("M08", "模考与估分系统", "/exams"), ("M09", "复试 AI 训练", "/interview"),
            ("M11", "资讯与政策提醒", "/news")]
    cols = 4
    bw = (CW - 12 * (cols - 1)) / cols
    for i, (code, title, path) in enumerate(mods):
        bx = MARGIN + (i % cols) * (bw + 12)
        by = y + (i // cols) * 72
        c.box(bx, by, bw, 62, "", "#F5F3FF", "#DDD6FE", arc=8)
        c.label(bx + 12, by + 8, 60, 18, code, 10, "#7C3AED", 1)
        c.label(bx + 52, by + 8, bw - 64, 18, title, 11, INK, 1)
        c.label(bx + 12, by + 32, bw - 24, 16, path, 9, MUTED)
    y += ((len(mods) + cols - 1) // cols) * 72 + 16

    # band 3: other roles / public
    c.label(MARGIN, y, 700, 18, "③ 其他工作空间与公开页", 11, INK, 1)
    y += 24
    others = [("T01", "教学工作台 /teacher", "教师视角", "#ECFDF5", OK),
              ("T02", "老师与任务 /teaching", "学生视角", "#ECFDF5", OK),
              ("ADMIN", "平台管理 /admin", "ADMIN 专属", "#FEF2F2", DANGER),
              ("", "账号设置 /account", "live 已登录", "#F3F4F6", MUTED),
              ("", "登录 / 注册 /login", "公开", "#F5F3FF", "#6D28D9"),
              ("", "账号激活 /activate", "邀请链接", "#F5F3FF", "#6D28D9"),
              ("", "无访问权限 /forbidden", "越权拦截", "#F3F4F6", MUTED),
              ("", "404 未匹配路由", "兜底", "#F3F4F6", MUTED)]
    bw = (CW - 12 * 3) / 4
    for i, (code, title, note, fill, color) in enumerate(others):
        bx = MARGIN + (i % 4) * (bw + 12)
        by = y + (i // 4) * 66
        c.box(bx, by, bw, 56, "", fill, fill, arc=8)
        c.label(bx + 12, by + 8, bw - 24, 18, (code + " · " if code else "") + title, 10, color, 1)
        c.label(bx + 12, by + 30, bw - 24, 14, note, 9, MUTED)
    y += 2 * 66 + 14

    # band 4: guard flow
    c.label(MARGIN, y, 700, 18, "④ 路由守卫 router.beforeEach 判定顺序", 11, INK, 1)
    y += 24
    steps = ["进入路由", "live 且无会话 → /login?redirect=…", "读取 meta.role 与角色",
             "越权 → roleHome 或 /forbidden", "demo 禁 教学 / 管理 / 账号", "通过 → 渲染外壳"]
    sx = MARGIN
    sw = (CW - 10 * 5) / 6
    for i, text in enumerate(steps):
        bx = sx + i * (sw + 10)
        c.box(bx, y, sw, 66, text, WHITE, PRIMARY if i in (0, 5) else LINE, arc=8, size=9,
              color=INK if i in (0, 5) else MUTED, align="center", pad=8)
        if i < len(steps) - 1:
            c.arrow(bx + sw + 1, y + 33, bx + sw + 9, y + 33, MUTED)
    y += 82

    # band 5: live contract
    c.box(MARGIN, y, CW, 96, "", "#FFFBEB", "#FDE68A", dash=1, arc=10)
    c.label(MARGIN + 18, y + 12, 700, 18, "⑤ 真实接口模式的关键替换", 11, "#92400E", 1)
    c.label(MARGIN + 18, y + 36, CW - 36, 52,
            "App.vue 中：live 模式且当前路由属于 M01–M11 时，RouterView 被 "
            "<ContractWorkspace :key=\"route.path\" /> 替换，由契约操作（op019 / op072 …）渲染表单、任务轮询、事件流与结果；\n"
            "因此每个学习模块在 live 模式下的真实页面是「契约工作台」，演示模式下的详细 UI 见 04–14 号文件。",
            10, "#92400E")
    y += 112
    return y - y0


def draw_shell(c, y0):
    y = y0
    c.pill(MARGIN, y, 152, 24, "应用外壳线框", INK, INK, WHITE, 11, 1)
    c.label(MARGIN + 164, y + 3, 900, 18, "frontend/src/App.vue · 1440 × 760 视口示意", 10, FAINT)
    y += 40
    fx, fy, fw, fh = MARGIN, y, CW, 640
    c.box(fx, fy, fw, fh, "", WHITE, "#CBD5E1", arc=8)

    # sidebar
    sw = 236
    c.box(fx, fy, sw, fh, "", "#FBFBFE", "#E2E8F0", arc=8)
    c.label(fx + 18, fy + 18, sw - 36, 22, "研途 AI", 17, INK, 1)
    c.label(fx + 18, fy + 42, sw - 36, 14, "你的考研成长伙伴", 9, FAINT)
    c.hr(fx + 18, fy + 66, sw - 36, LINE)
    groups = [
        ("概览", [("M10", "学习工作台", True)]),
        ("学习", [("M01", "数字人陪伴督学", False), ("M02", "智能择校与专业分析", False),
                  ("M03", "公共课智能辅导", False), ("M04", "专业课学习支持", False),
                  ("M05", "AI 答疑与解题", False)]),
        ("提升", [("M06", "刷题与错题专项", False), ("M07", "个性化学习规划", False),
                  ("M08", "模考与估分系统", False), ("M09", "复试 AI 训练", False)]),
        ("资讯", [("M11", "资讯与政策提醒", False)]),
    ]
    cy = fy + 78
    for gname, items in groups:
        c.label(fx + 18, cy, 100, 14, gname, 9, FAINT, 1)
        cy += 18
        for code, title, active in items:
            if active:
                c.box(fx + 10, cy - 2, sw - 20, 28, "", SOFT, "#DDD6FE", arc=8)
            c.label(fx + 18, cy + 4, 34, 14, code, 9, PRIMARY if active else FAINT, 1)
            c.label(fx + 50, cy + 3, sw - 60, 16, title, 10, INK if active else MUTED, 1 if active else 0)
            cy += 28
        cy += 8
    c.box(fx + 10, fy + fh - 66, sw - 20, 48, "当前角色：学生\n演示模式 · 本地数据", "#F8FAFC", LINE, arc=8, size=9, color=MUTED, pad=8)

    # topbar
    tx = fx + sw
    tw_ = fw - sw
    c.box(tx, fy, tw_, 58, "", WHITE, "#E2E8F0", arc=8)
    c.label(tx + 20, fy + 20, 320, 18, "我的学习空间  ›  学习工作台", 11, MUTED)
    bx = tx + tw_ - 20
    for text, wid in [("退出", 44), ("林小满 · 学生", 96), ("角色：学生 ▾", 88), ("账号设置", 62), ("🔔 3", 46), ("切换为真实接口", 96)]:
        bx -= wid
        c.pill(bx, fy + 15, wid, 26, text, WHITE if text != "切换为真实接口" else SOFT, LINE if text != "切换为真实接口" else "#DDD6FE", MUTED if text != "切换为真实接口" else PRIMARY, 9, 0)
        bx -= 8

    # heading band
    hy = fy + 58
    c.box(tx, hy, tw_, 92, "", WHITE, "#E2E8F0", arc=0)
    c.label(tx + 20, hy + 14, 600, 14, "M10 · KAOYAN LEARNING SPACE", 9, "#7C3AED", 1)
    c.label(tx + 20, hy + 30, 500, 26, "学习工作台", 20, INK, 1)
    c.label(tx + 20, hy + 60, 640, 16, "把每天的努力集中在一页：学习时长、练习正确率、今日任务与快捷入口。", 10, MUTED)
    c.label(tx + tw_ - 200, hy + 16, 180, 14, "2026-11-17 周一", 10, FAINT)

    # demo banner + content
    by = hy + 92
    c.box(tx + 20, by + 12, tw_ - 40, 34, "ⓘ  当前为前端演示。示例院校、学习资料与 AI 回复均不代表真实业务结果；本地学习操作仅保存在本浏览器。", "#F5F3FF", "#DDD6FE", arc=6, size=9, color="#5B21B6", pad=10)
    cy = by + 58
    c.box(tx + 20, cy, tw_ - 40, 300, "", "#FAFAFB", "#CBD5E1", dash=1, arc=8)
    c.label(tx + 36, cy + 14, 620, 18, "内容区（App.vue 的条件渲染）", 11, INK, 1)
    c.label(tx + 36, cy + 36, tw_ - 80, 60,
            "live 模式 + module 以 M 开头 → <ContractWorkspace :key=\"route.path\" />（契约工作台，见 19 号文件）\n"
            "其余情况 → <RouterView />（/dashboard、/schools … 的演示视图，见 04–14 号文件）\n"
            "教学 / 管理 / 账号 / 异常页始终走 RouterView。", 9, MUTED)
    placeholder(c, tx + 36, cy + 104, (tw_ - 80) / 2 - 10, 170, "ContractWorkspace\n动作 chips + 契约表单 + 任务轮询 + 结果", 9)
    placeholder(c, tx + 36 + (tw_ - 80) / 2 + 10, cy + 104, (tw_ - 80) / 2 - 10, 170, "具体页面 View\n（演示模式下的 11 个学习模块）", 9)

    # footer
    c.hr(tx + 20, fy + fh - 42, tw_ - 40, LINE)
    c.label(tx + 20, fy + fh - 32, 520, 16, "研途 AI · 把每一次努力，变成看得见的进步", 9, FAINT)
    c.label(tx + tw_ - 200, fy + fh - 32, 180, 16, "清空演示数据", 9, PRIMARY, 0, align="right")

    y = fy + fh + 26

    # overlays row
    c.label(MARGIN, y, 700, 18, "全局浮层（不依赖路由，按条件挂载）", 11, INK, 1)
    y += 26
    ow = (CW - 16 * 2) / 3
    # floating companion
    ox = MARGIN
    c.box(ox, y, ow, 250, "", WHITE, LINE, arc=10)
    c.label(ox + 14, y + 12, ow - 28, 18, "FloatingCompanion", 11, INK, 1)
    c.label(ox + 14, y + 32, ow - 28, 14, "非 /login 且（demo 或 STUDENT）：右下角可拖动 orb", 9, FAINT)
    c.box(ox + 14, y + 56, ow - 150, 178, "研小途 面板\n─────────\n对话区\nSupervisionPanel\n督学模式三选\n预览督学反馈\n管理任务时间", "#F8FAFC", LINE, arc=8, size=9, color=MUTED, pad=10)
    c.cell("", "ellipse;html=1;fillColor=%s;strokeColor=%s;strokeWidth=2;" % (SOFT, PRIMARY), ox + ow - 108, y + 150, 78, 78)
    c.label(ox + ow - 118, y + 176, 98, 14, "orb + 未读角标", 8, PRIMARY, 1, align="center")

    # notification bell
    ox = MARGIN + ow + 16
    c.box(ox, y, ow, 250, "", WHITE, LINE, arc=10)
    c.label(ox + 14, y + 12, ow - 28, 18, "NotificationBell · 消息通知", 11, INK, 1)
    c.label(ox + 14, y + 32, ow - 28, 14, "live + STUDENT 才渲染；未读 > 99 显示 99+", 9, FAINT)
    c.box(ox + 14, y + 56, ow - 28, 178, "", "#F8FAFC", LINE, arc=8)
    c.box(ox + 26, y + 68, ow - 52, 24, "消息通知                              ×", WHITE, LINE, arc=6, size=9, color=INK, pad=8)
    for i in range(3):
        c.hr(ox + 26, y + 104 + i * 42, ow - 52, "#F1F5F9")
        c.label(ox + 26, y + 92 + i * 42, ow - 52, 14, "标题 · 未读 · 北京时间", 9, MUTED, 1)
        c.label(ox + 26, y + 106 + i * 42, ow - 52, 12, "内容摘要两行，分页每页 10 条", 8, FAINT)

    # toast + storage error
    ox = MARGIN + 2 * (ow + 16)
    c.box(ox, y, ow, 250, "", WHITE, LINE, arc=10)
    c.label(ox + 14, y + 12, ow - 28, 18, "toast 与本地存储异常", 11, INK, 1)
    c.label(ox + 14, y + 32, ow - 28, 14, "写入 localStorage 失败时顶部出现常驻 notice", 9, FAINT)
    c.box(ox + 14, y + 60, ow - 28, 46, "storageError：无法保存本地演示数据，刷新后会丢失。", "#FEF2F2", "#FECACA", arc=8, size=9, color=DANGER, pad=10)
    c.box(ox + 14, y + 118, ow - 28, 38, "已记录今天的坚持  ·  toast 4 秒自动消失", "#111827", "#111827", arc=8, size=9, color=WHITE, pad=10)
    c.box(ox + 14, y + 168, ow - 28, 62, "全局状态：app.mode（demo / live）、app.user、app.storageError、toast 队列", "#F8FAFC", LINE, arc=8, size=9, color=MUTED, pad=10)

    y += 250 + 20
    return y - y0


def draw_login(c, y0):
    y = y0
    c.pill(MARGIN, y, 132, 24, "登录 / 注册", INK, INK, WHITE, 11, 1)
    c.label(MARGIN + 144, y + 3, 800, 18, "全屏 auth-page · 无侧栏 / 无顶栏 · 1440 × 824", 10, FAINT)
    y += 40
    fx, fy, fw, fh = MARGIN, y, CW, 824
    c.box(fx, fy, fw, fh, "", "#FAFAFF", "#E2E8F0", arc=10)
    # glows
    c.cell("", "ellipse;html=1;fillColor=#EEF2FF;strokeColor=none;opacity=70;", fx + 60, fy + 60, 420, 420)
    c.cell("", "ellipse;html=1;fillColor=#FDF4FF;strokeColor=none;opacity=60;", fx + fw - 460, fy + 320, 420, 380)
    # fireflies
    import random
    rnd = random.Random(7)
    for _ in range(26):
        px = fx + rnd.randint(30, fw - 40)
        py = fy + rnd.randint(30, fh - 40)
        c.cell("", "ellipse;html=1;fillColor=#FDE68A;strokeColor=none;", px, py, 4, 4)

    # brand
    c.label(fx, fy + 44, fw, 26, "研途 AI", 19, INK, 1, align="center")
    c.label(fx, fy + 70, fw, 16, "陪你，向理想生长", 10, MUTED, 0, align="center")

    # card
    cwid = 430
    cx = fx + (fw - cwid) / 2
    cyy = fy + 96
    chh = 676
    c.box(cx, cyy, cwid, chh, "", WHITE, LINE, arc=14)
    c.cell("", "ellipse;html=1;fillColor=#EEF2FF;strokeColor=#DDD6FE;", cx + cwid / 2 - 22, cyy + 26, 44, 44)
    c.label(cx + cwid / 2 - 22, cyy + 38, 44, 18, "✦", 16, PRIMARY, 1, align="center")
    c.label(cx, cyy + 82, cwid, 14, "A LITTLE LIGHT, A LONG WAY", 9, FAINT, 0, align="center")
    c.label(cx, cyy + 100, cwid, 26, "每一份努力，都有微光", 17, INK, 1, align="center")
    c.label(cx, cyy + 128, cwid, 14, "登录后继续你的学习记录；未注册可先注册账号。", 9, MUTED, 0, align="center")

    # tabs
    tabw = (cwid - 64 - 8) / 2
    c.pill(cx + 32, cyy + 154, tabw, 34, "登录", SOFT, "#DDD6FE", PRIMARY, 11, 1)
    c.pill(cx + 32 + tabw + 8, cyy + 154, tabw, 34, "注册", WHITE, LINE, MUTED, 11, 0)

    fy2 = cyy + 202
    def field_at(label, value, fh_=32):
        nonlocal fy2
        c.label(cx + 32, fy2, cwid - 64, 13, label, 9, MUTED)
        c.box(cx + 32, fy2 + 15, cwid - 64, fh_, value, WHITE, LINE, arc=8, size=10, color=FAINT, pad=10)
        fy2 += 15 + fh_ + 12

    field_at("昵称（仅注册时）", "例如 林小满")
    field_at("邮箱", "you@example.com")
    field_at("邮箱验证码（设置密码时）", "6 位验证码        获取验证码", 32)
    field_at("密码", "至少 8 位，字母 + 数字        显示", 32)
    field_at("确认密码（注册 / 重置）", "再次输入密码", 32)
    c.checkbox(cx + 32, fy2 + 2, True, "我已阅读并同意《用户协议》与《隐私说明》")
    c.label(cx + cwid - 112, fy2 - 2, 80, 16, "忘记密码", 9, PRIMARY, 0, align="right")
    fy2 += 26
    c.button(cx + 32, fy2, cwid - 64, 40, "登录后继续学习", True, 12)
    fy2 += 52
    c.label(cx + 32, fy2, cwid - 64, 14, "安心连接，守护每一次成长", 9, FAINT, 0, align="center")
    c.button(cx + 32, fy2 + 22, cwid - 64, 38, "先逛逛学习空间 ↗", False, 11)

    c.label(fx, fy + fh - 44, fw, 16, "不必光芒万丈，只要一直向前。", 10, FAINT, 0, align="center")

    # side annotations
    ax = fx + fw - 250
    c.box(ax, fy + 130, 226, 180, "表单规则\n· 邮箱格式校验\n· 密码 ≤ 72 字节且字母 + 数字\n· 两次密码一致\n· 验证码 6 位、60 秒可重发\n· 提交中禁用按钮并显示 loading", WHITE, LINE, arc=8, size=9, color=MUTED, pad=10)
    c.box(ax, fy + 330, 226, 200, "成功后\n· setMode('live')\n· 按 route.query.redirect 跳转\n· 无 redirect 时跳 roleHome(role)\n· ADMIN → /admin\n· TEACHER → /teacher\n· STUDENT → /dashboard", WHITE, LINE, arc=8, size=9, color=MUTED, pad=10)
    c.box(MARGIN + 10, fy + 180, 226, 210, "页内状态\n· 模式：登录 / 注册 / 设置密码\n· 重置密码时 h1 变为「重置密码」\n· demo 按钮「先逛逛学习空间」\n  直接以演示模式进入 /dashboard\n· 密码可见性切换 eye 图标", WHITE, LINE, arc=8, size=9, color=MUTED, pad=10)
    y = fy + fh + 24
    return y - y0


def draw_activate(c, y0):
    y = y0
    c.pill(MARGIN, y, 132, 24, "账号激活", INK, INK, WHITE, 11, 1)
    c.label(MARGIN + 144, y + 3, 900, 18, "/activate · 公开路由（meta.public）· 全屏无壳 · 邀请邮箱只读", 10, FAINT)
    y += 40
    fx, fy, fw, fh = MARGIN, y, CW, 660
    c.box(fx, fy, fw, fh, "", "#F8FAFF", "#E2E8F0", arc=10)
    cwid = 470
    cx = fx + (fw - cwid) / 2
    cyy = fy + 48
    chh = 552
    c.box(cx, cyy, cwid, chh, "", WHITE, LINE, arc=14)
    c.label(cx, cyy + 34, cwid, 26, "激活你的账号", 18, INK, 1, align="center")
    c.label(cx, cyy + 64, cwid, 14, "请使用邀请邮箱完成验证并设置密码。", 9, MUTED, 0, align="center")
    fy2 = cyy + 96

    def field_at(label, value, note=None):
        nonlocal fy2
        c.label(cx + 36, fy2, cwid - 72, 13, label, 9, MUTED)
        readonly = note == "readonly"
        c.box(cx + 36, fy2 + 15, cwid - 72, 32, value, "#F8FAFC" if readonly else WHITE, LINE, arc=8, size=10, color=MUTED, pad=10)
        if note == "code":
            c.label(cx + cwid - 150, fy2 + 22, 110, 18, "获取验证码", 9, PRIMARY, 0, align="right")
        fy2 += 59

    field_at("邀请邮箱（只读）", "teacher@example.com", "readonly")
    field_at("旧账号密码（迁移旧账号时填写）", "若为历史账号，请输入原密码")
    field_at("运维凭据（可选）", "由管理员提供的临时凭据")
    field_at("邮箱验证码", "6 位验证码", "code")
    field_at("设置新密码", "至少 8 位，字母 + 数字")
    field_at("确认新密码", "再次输入")
    c.button(cx + 36, fy2 + 6, cwid - 72, 40, "完成激活", True, 12)
    c.label(cx + 36, fy2 + 56, cwid - 72, 14, "激活成功后可直接登录，邀请状态变为已激活。", 9, FAINT, 0, align="center")

    c.box(MARGIN + 10, fy + 70, 220, 240, "进入方式\n· 邮件邀请链接携带 token\n· 无 token 时提示链接失效\n· 激活成功后关闭邀请\n· 失败原因：token 过期 /\n  邮箱不匹配 / 已激活", WHITE, LINE, arc=8, size=9, color=MUTED, pad=10)
    c.box(fx + fw - 240, fy + 70, 220, 240, "激活后身份\n· 老师：进入 /teacher，\n  侧栏只保留教学工作台\n· 学生：进入 /dashboard\n· 记录激活时间与操作来源\n  写入审计日志", WHITE, LINE, arc=8, size=9, color=MUTED, pad=10)
    y = fy + fh + 24
    return y - y0


def draw_errors(c, y0):
    y = y0
    c.pill(MARGIN, y, 192, 24, "异常页面 /forbidden 与 404", INK, INK, WHITE, 11, 1)
    c.label(MARGIN + 204, y + 3, 800, 18, "两页都仍在外壳内（保留侧栏与顶栏），用于越权拦截与未匹配路由兜底", 10, FAINT)
    y += 40
    half = (CW - 18) / 2
    fh = 400
    # forbidden
    c.box(MARGIN, y, half, fh, "", "#FCFCFD", LINE, arc=10)
    c.label(MARGIN + 14, y + 12, half - 28, 16, "ForbiddenView · /forbidden", 10, FAINT, 1)
    c.box(MARGIN + 40, y + 56, half - 80, 300, "", WHITE, LINE, arc=12)
    c.cell("", "ellipse;html=1;fillColor=#FEF2F2;strokeColor=#FECACA;", MARGIN + half / 2 - 28, y + 90, 56, 56)
    c.label(MARGIN + half / 2 - 28, y + 106, 56, 24, "!", 22, DANGER, 0, align="center")
    c.label(MARGIN + 60, y + 164, half - 120, 30, "无访问权限", 20, INK, 1, align="center")
    c.label(MARGIN + 60, y + 200, half - 120, 34, "当前账号的角色不能访问这个页面。\n如需开通，请联系管理员。", 10, MUTED, 0, align="center")
    c.button(MARGIN + half / 2 - 70, y + 268, 140, 36, "返回首页", True, 11)
    c.label(MARGIN + 60, y + 316, half - 120, 14, "target = app.user ? roleHome(user) : '/login'", 9, FAINT, 0, align="center")

    # 404
    ox = MARGIN + half + 18
    c.box(ox, y, half, fh, "", "#FCFCFD", LINE, arc=10)
    c.label(ox + 14, y + 12, half - 28, 16, "NotFoundView · /:pathMatch(.*)*", 10, FAINT, 1)
    c.box(ox + 40, y + 56, half - 80, 300, "", WHITE, LINE, arc=12)
    c.label(ox + 40, y + 86, half - 80, 54, "404", 40, "#CBD5E1", 1, align="center")
    c.label(ox + 60, y + 148, half - 120, 30, "这个页面不存在", 18, INK, 1, align="center")
    c.label(ox + 60, y + 184, half - 120, 34, "链接可能已失效，或路径拼写有误。\n请从左侧导航选择学习模块。", 10, MUTED, 0, align="center")
    c.button(ox + half / 2 - 80, y + 252, 160, 36, "返回工作台", True, 11)
    c.label(ox + 60, y + 300, half - 120, 14, "未匹配任何路由时兜底，不再抛出空白页", 9, FAINT, 0, align="center")
    y += fh + 22

    c.box(MARGIN, y, CW, 84, "", WARNSOFT, WARNLINE, dash=1, arc=8)
    c.label(MARGIN + 16, y + 10, CW - 32, 16, "触发条件", 10, WARN, 1)
    c.label(MARGIN + 16, y + 30, CW - 32, 44,
            "路由守卫：meta.role 与 app.user.role 不匹配 → 有 roleHome 时跳角色首页，无可用首页时跳 /forbidden；\n"
            "demo 模式访问 /teacher、/teaching、/admin、/account 直接跳 /login；live 模式未登录跳 /login?redirect=<当前路径>。", 9, WARN)
    y += 100
    return y - y0


def draw_contract(c, y0):
    y = y0
    c.pill(MARGIN, y, 226, 24, "真实模式 · 契约工作台", INK, INK, WHITE, 11, 1)
    c.label(MARGIN + 238, y + 3, 900, 18, "ContractWorkspace.vue 在 live 模式下替换 M01–M11 的全部页面内容", 10, FAINT)
    y += 40
    # action chips
    c.box(MARGIN, y, CW, 76, "", "#F5F3FF", "#DDD6FE", arc=10)
    c.label(MARGIN + 16, y + 12, 700, 16, "契约动作（按 tag 前缀匹配当前模块，排除 /api/v1/study- 路径）", 10, "#5B21B6", 1)
    px = MARGIN + 16
    for text in ["op019 专业目录检索", "op072 学习数据总览", "op060 逐题保存答案", "op038 题目图片识别", "op006 上传文件", "op007 读取文件", "op008 删除文件"]:
        pw = tw(text, 9) + 20
        c.pill(px, y + 36, pw, 24, text, WHITE, "#DDD6FE", "#5B21B6", 9, 0)
        px += pw + 6
    y += 92

    # form + result split
    lw = (CW - 18) * 0.46
    rw = CW - 18 - lw
    lh = 0
    # left: dynamic form
    fh = 380
    c.box(MARGIN, y, lw, fh, "", WHITE, LINE, arc=10)
    c.label(MARGIN + 16, y + 12, lw - 100, 18, "契约表单（由 op.body 的 ContractField 动态生成）", 11, INK, 1)
    c.label(MARGIN + 16, y + 34, lw - 32, 14, "字段类型：string / number / enum / boolean / array / file", 9, FAINT)
    cy = y + 56
    for label, value, kind in [("major", "计算机技术", "string"), ("score", "340", "number"),
                               ("region", "华东", "enum"), ("includeBackup", "true", "boolean"),
                               ("attachments", "选择文件…", "file")]:
        c.label(MARGIN + 16, cy, 120, 14, label, 9, MUTED, 1)
        c.label(MARGIN + 150, cy, lw - 174, 14, kind, 8, FAINT, 0, align="right")
        c.box(MARGIN + 16, cy + 16, lw - 32, 30, value, WHITE, LINE, arc=8, size=10, color=INK, pad=10)
        cy += 54
    c.button(MARGIN + 16, cy + 4, lw - 32, 36, "执行契约操作", True, 11)
    c.label(MARGIN + 16, cy + 50, lw - 32, 14, "校验失败时字段下方内联报错，不发起请求。", 9, FAINT)

    # right: job / result
    ox = MARGIN + lw + 18
    c.box(ox, y, rw, 156, "", WHITE, LINE, arc=10)
    c.label(ox + 16, y + 12, rw - 120, 18, "任务轮询", 11, INK, 1)
    c.label(ox + rw - 96, y + 12, 80, 18, "waitJob", 9, FAINT, 0, align="right")
    c.box(ox + 16, y + 40, rw - 32, 8, "", "#E5E7EB", "#E5E7EB", arc=0)
    c.box(ox + 16, y + 40, (rw - 32) * 0.64, 8, "", PRIMARY, PRIMARY, arc=0)
    c.label(ox + 16, y + 56, rw - 32, 14, "job_id：轮询进度 64% · 已用 12s · 可「停止等待」", 9, MUTED)
    c.label(ox + 16, y + 76, rw - 32, 14, "状态：QUEUED → RUNNING → SUCCEEDED / FAILED", 9, MUTED)
    c.label(ox + 16, y + 96, rw - 32, 14, "subscribeEvents：SSE / WebSocket 推送中间态与事件", 9, MUTED)
    c.button(ox + 16, y + 116, 96, 28, "停止等待", False, 9)

    c.box(ox, y + 172, rw, 132, "", WHITE, LINE, arc=10)
    c.label(ox + 16, y + 184, rw - 32, 18, "结果区（ContractResult 递归渲染）", 11, INK, 1)
    c.box(ox + 16, y + 210, rw - 32, 82,
          "{\n  \"items\": [ { \"school\": \"华东师范大学\", \"line\": 355 } ],\n  \"note\": \"来源与口径以接口返回为准\"\n}",
          "#0F172A", "#0F172A", arc=8, size=9, color="#E2E8F0", pad=10)

    c.box(ox, y + 320, rw, 60, "", WHITE, LINE, arc=10)
    c.label(ox + 16, y + 330, rw - 32, 18, "错误与重试", 10, INK, 1)
    c.label(ox + 120, y + 332, rw - 136, 16, "error：契约返回的业务错误码与可读说明", 9, MUTED, 0, align="right")
    c.button(ox + 16, y + 350, 96, 24, "重试上次操作", False, 9)

    y += fh + 20

    # extras / module map
    c.box(MARGIN, y, CW, 118, "", "#FFFBEB", "#FDE68A", dash=1, arc=10)
    c.label(MARGIN + 16, y + 10, 800, 18, "模块差异（extras）与关键实现", 10, "#92400E", 1)
    c.label(MARGIN + 16, y + 34, CW - 32, 76,
            "moduleMap：/companion→M01、/schools→M02、/public-courses→M03、/professional-courses→M04、/qa→M05、/practice→M06、/planning→M07、/exams→M08、/interview→M09、/dashboard→M10、/news→M11。\n"
            "默认选中动作：M02 用 op019（专业目录检索），其余模块用 op072（学习数据总览，属 M10）；M07 额外追加 op004 / op005（profile 读写），M01、M05、M09 额外追加 op006 / op007 / op008（文件上传、读取、删除）。\n"
            "op.multipart 走上传（题目图片、录音），提交前展示文件名与大小；等待中可中止，卸载时清理 AbortController 与订阅。", 9, "#92400E")
    y += 134
    return y - y0


PAGE_NAMES = {
    "00": "页面地图与路由总览", "01": "全局布局与应用外壳", "02": "登录与注册 / 重置密码",
    "03": "账号激活", "04": "学习工作台", "05": "数字人陪伴督学", "06": "智能择校与专业分析",
    "07": "公共课智能辅导", "08": "专业课学习支持", "09": "AI 答疑与解题", "10": "刷题与错题训练",
    "11": "个性化学习规划", "12": "模考与估分系统", "13": "复试 AI 训练", "14": "资讯与政策提醒",
    "15": "教学工作台", "16": "账号设置", "17": "平台管理", "18": "无权限与 404",
    "19": "真实模式契约工作台",
}


def draw_contractmap(c, y0):
    y = y0
    cards = [
        ("结论 1 · 布局", "逐屏对照 frontend/src/views 与 App.vue：demo 模式的区块、顺序、文案、状态与实现一致；"
                         "live 模式 M01–M11 由 ContractWorkspace 接管（19 号文件）。", PRIMARY),
        ("结论 2 · 接口", "contracts/openapi.json 共 128 paths / 141 operations；图中 op 编号与路径全部可查。"
                          "首版有 5 处语义标注有误，已修正（op038/039、op060–062、op006–008、op072、示例 ID）。", "#0F766E"),
        ("结论 3 · 数据表", "Flyway 共 54 张表。模块表是 id + owner_id + payload(LONGTEXT) 文档式，"
                            "页面结构化字段存于 payload JSON；账号、权限、教学、审计为关系表。", "#B45309"),
    ]
    cw3 = (CW - 2 * 14) / 3
    hmax = 0
    for i, (title, text, color) in enumerate(cards):
        x = MARGIN + i * (cw3 + 14)
        hh = 34 + text_h(text, 9, cw3 - 28) + 14
        hmax = max(hmax, hh)
    for i, (title, text, color) in enumerate(cards):
        x = MARGIN + i * (cw3 + 14)
        c.box(x, y, cw3, hmax, "", WHITE, LINE, arc=10)
        c.label(x + 14, y + 12, cw3 - 28, 18, title, 11, color, 1)
        c.label(x + 14, y + 34, cw3 - 28, hmax - 48, text, 9, MUTED)
    y += hmax + 22

    rows = []
    for key in sorted(CONTRACT):
        info = CONTRACT[key]
        ops = info["ops"]
        if ops:
            first = ops[0].split()[0].replace("op", "")
            last = ops[-1].split()[0].replace("op", "")
            rng = ("op%s–op%s" % (first, last)) if len(ops) > 1 else ("op" + first)
        else:
            rng = "—"
        tables = "、".join(t[0] for t in info["tables"])
        rows.append(["%s %s" % (key, PAGE_NAMES.get(key, "")), info["scope"], "%d" % len(ops) if ops else "0", tables])
    c.label(MARGIN, y, 700, 18, "页面 → 契约操作 → 数据表", 12, INK, 1)
    y += 26
    y += table(c, MARGIN, y, CW, ["页面", "契约范围", "操作数", "主要数据表"], rows, [0.14, 0.36, 0.07, 0.43], size=9)
    y += 20

    c.box(MARGIN, y, CW, 96, "", "#F8FAFC", LINE, dash=1, arc=10)
    c.label(MARGIN + 16, y + 12, CW - 32, 18, "对照方法与仍需注意的两点", 10, INK, 1)
    c.label(MARGIN + 16, y + 34, CW - 32, 54,
            "方法：以 contracts/openapi.json 的 operationId / path / tag 为准逐个核对页面操作，以 Flyway 迁移文件的 CREATE TABLE 为准核对字段；"
            "不臆造接口与表。\n"
            "注意 1：模块数据以 payload JSON 落库，报录比、任务、逐题答案等字段没有独立列，做后端校验时按 payload schema 约束。\n"
            "注意 2：ContractWorkspace 的默认动作 op072 属 M10，M07 的 extras 用 profile 接口，均是实现现状而非契约分组。", 9, MUTED)
    y += 112
    return y - y0


CUSTOM = {
    "overview": draw_overview,
    "shell": draw_shell,
    "login": draw_login,
    "activate": draw_activate,
    "errors": draw_errors,
    "contract": draw_contract,
    "contractmap": draw_contractmap,
}


# ------------------------------------------------------------------ notes sheet
def draw_contract_block(c, y, W, info):
    c.label(40, y, 600, 18, "接口契约与数据表", 12, INK, 1)
    y += 26
    h = text_h(info["scope"], 10, W - 120)
    c.label(40, y, W - 120, h, "契约范围：" + info["scope"], 10, PRIMARY, 1)
    y += h + 8
    if info["ops"]:
        for op in info["ops"]:
            c.label(56, y, W - 140, 15, "· " + op, 9, MUTED)
            y += 15
    else:
        c.label(56, y, W - 140, 15, "· 无（不调用接口）", 9, MUTED)
        y += 15
    y += 8
    for name, note in info["tables"]:
        c.label(56, y, 250, 15, "· " + name, 9, INK, 1)
        c.label(312, y, W - 372, 15, note, 9, FAINT)
        y += 15
    y += 10
    c.hr(40, y, W - 80, "#F1F5F9")
    return y + 14


def build_notes(spec):
    W = 1120
    c = Canvas()
    y = 46
    c.label(40, y, W - 80, 26, "设计说明 · " + spec["name"], 19, INK, 1)
    y += 34
    c.label(40, y, W - 80, 16, spec["subtitle"], 10, MUTED)
    y += 30
    c.hr(40, y, W - 80, LINE)
    y += 18

    rows = []
    for k, label in (("path", "路由"), ("role", "角色"), ("mode", "模式"), ("component", "组件")):
        if spec.get(k):
            rows.append((label, spec[k]))
    rows.append(("文件", spec["file"] + ".drawio"))
    for label, value in rows:
        c.label(40, y, 70, 16, label, 10, FAINT, 1)
        c.label(116, y, W - 160, 16, value, 10, INK)
        y += 22
    y += 8

    c.label(40, y, 600, 18, "结构与交互备注", 12, INK, 1)
    y += 26
    for i, note in enumerate(spec.get("notes", []), 1):
        h = text_h(note, 10, W - 120)
        c.label(40, y, 26, 16, "%02d" % i, 9, PRIMARY, 1)
        c.label(66, y, W - 120, h, note, 10, MUTED)
        y += h + 8
        c.hr(40, y - 2, W - 80, "#F1F5F9")
    y += 10

    if spec.get("extra_notes"):
        c.label(40, y, 600, 18, "数据与接口", 12, INK, 1)
        y += 24
        for note in spec["extra_notes"]:
            h = text_h(note, 10, W - 120)
            c.label(40, y, W - 120, h, "· " + note, 10, MUTED)
            y += h + 6
        y += 8

    key = spec["file"][:2]
    if key in CONTRACT:
        y = draw_contract_block(c, y, W, CONTRACT[key])

    c.box(40, y, W - 80, 66, "布局与文案取自 frontend/src 现有实现；操作编号、请求路径与数据表已逐项对照 "
                              "contracts/openapi.json（141 operations）与 Flyway 迁移（54 张表）。"
                              "模块表多为 id + owner_id + payload(LONGTEXT) 文档式存储，页面字段落在 payload JSON 内。",
          WARNSOFT, WARNLINE, dash=1, arc=8, size=10, color=WARN, pad=12)
    y += 82
    height = int(y + 20)
    c.box(0, 0, W, height, "", WHITE, WHITE, arc=0, sw=0)
    frame = c.cells.pop()
    c.cells.insert(0, frame)
    return c, height


# ------------------------------------------------------------------ assemble
def compose(spec):
    if spec.get("custom"):
        c = Canvas()
        top = page_header(c, spec)
        used = CUSTOM[spec["custom"]](c, top + 8)
        height = int(top + 8 + used + 30)
        c.box(0, 0, PW, height, "", WHITE, WHITE, arc=0, sw=0)
        frame = c.cells.pop()
        c.cells.insert(0, frame)
    else:
        c = Canvas()
        top = page_header(c, spec)
        cy = top + 8
        for b in spec["blocks"]:
            cy += RENDER[b["type"]](c, b, MARGIN, cy, CW) + 22
        height = int(cy + 30)
        c.box(0, 0, PW, height, "", WHITE, WHITE, arc=0, sw=0)
        frame = c.cells.pop()
        c.cells.insert(0, frame)

    notes_canvas, notes_h = build_notes(spec)
    diagrams = [
        page(("page1", "页面线框"), PW, height, c),
        page(("notes", "设计说明"), 1120, notes_h, notes_canvas),
    ]
    return mxfile(diagrams), height


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    index = []
    for spec in sorted(P.PAGES, key=lambda s: s["file"]):
        xml, h = compose(spec)
        path = os.path.join(OUT, spec["file"] + ".drawio")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(xml)
        index.append((spec["file"], h, len(xml)))
        print("ok %-52s %5dpx %7d bytes" % (spec["file"], h, len(xml)))
    print("\n%d files -> %s" % (len(index), OUT))


if __name__ == "__main__":
    main()
