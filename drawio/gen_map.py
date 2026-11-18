# -*- coding: utf-8 -*-
"""Generate contract_map.py: per-page contract operations + database tables."""
import json
import os

ROOT = r"D:\STUDY\UniversityAIUser"
doc = json.load(open(os.path.join(ROOT, "contracts", "openapi.json"), encoding="utf-8"))
OPS = {}
for path, item in doc["paths"].items():
    for method, op in item.items():
        if method in ("get", "post", "put", "patch", "delete"):
            OPS[op["operationId"]] = (method.upper(), path, (op.get("tags") or [""])[0])


def ops_of_tag(tag):
    return [o for o in sorted(OPS) if OPS[o][2] == tag]


def fmt(oid):
    m, p, _ = OPS[oid]
    return "%s %s %s" % (oid, m, p.replace("/api/v1", ""))


# file prefix -> (module label, op ids, tables[(name, note)])
SPEC = {
    "00": ("全部 141 个操作 / 54 张表",
           ["op001", "op002", "op004", "op010", "op072"],
           [("app_users", "账号（varchar(36) 主键、login_name 唯一）"),
            ("sys_roles / sys_permissions", "角色与权限字典"),
            ("sys_user_roles / sys_role_permissions", "角色、权限关联"),
            ("security_audit", "审计日志（action_code、before/after、trace_id）")]),
    "01": ("00 通用技术接口（op001–op010）",
           ["op001", "op004", "op010", "op080", "op081"],
           [("app_users", "会话身份"), ("profiles", "备考资料 payload"),
            ("sys_user_roles", "角色决定侧栏可见性"), ("notifications", "顶栏消息通知")]),
    "02": ("AUTH + 00 通用技术接口",
           ["op001", "op002", "op082", "op083", "op084", "op085", "op115", "op116"],
           [("app_users", "email、email_verified_at、account_status、auth_version"),
            ("auth_tokens", "JWT 会话"), ("email_challenges", "验证码（purpose/email/scope_key）"),
            ("email_send_quota", "发送配额"), ("identity_locks", "身份串行锁")]),
    "03": ("AUTH（邀请激活）",
           ["op117", "op118"],
           [("account_invitations", "token_digest、purpose、expires_at、delivery_status"),
            ("app_users", "account_status → ENABLED"),
            ("sys_user_roles", "激活后写入 STUDENT / TEACHER"),
            ("teacher_accounts", "老师账号登录名分离")]),
    "04": ("M10 学习数据仪表盘",
           ["op072", "op071", "op073", "op074", "op139", "op140", "op141"],
           [("study_events", "学习行为事件（payload）"), ("attempts", "作答记录"),
            ("checkins", "打卡"), ("plans", "计划与任务 payload")]),
    "05": ("M01 AI数字人陪伴督学",
           ["op011", "op012", "op013", "op014", "op015", "op016", "op017", "op018",
            "op097", "op098", "op099", "op100"],
           [("avatar_preferences", "形象与陪伴模式"), ("checkins", "每日打卡"),
            ("reminder_preferences", "督学提醒"), ("study_supervision", "督学设置与任务时间"),
            ("conversations / qa_messages", "数字人对话（op014）")]),
    "06": ("M02 智能择校与专业分析",
           ["op019", "op020", "op021", "op022", "op023", "op024"],
           [("programs", "院校专业目录：报录比、招生人数、分数段都在 payload"),
            ("evaluations", "择校评估与风险提示")]),
    "07": ("M03 公共课智能辅导",
           ["op025", "op026", "op027", "op028", "op029", "op030"],
           [("knowledge_trees", "数学/英语/政治知识树"), ("questions", "题目与评分依据"),
            ("books", "参考书与章节")]),
    "08": ("M04 专业课学习支持",
           ["op031", "op032", "op033", "op034", "op035", "op036"],
           [("subjects", "专业课科目"), ("knowledge_trees", "学科知识树"),
            ("books", "参考书"), ("questions", "真题与考点")]),
    "09": ("M05 AI答疑与解题",
           ["op037", "op038", "op039", "op040", "op041", "op042"],
           [("conversations", "答疑会话"), ("qa_messages", "消息"),
            ("recognitions", "图片识别结果"), ("files", "私有文件元数据（op006–op008）")]),
    "10": ("M06 刷题错题本专项训练",
           ["op043", "op044", "op045", "op046", "op047", "op048", "op049"],
           [("practice_sessions", "练习会话"), ("attempts", "作答与批改"),
            ("wrong_items", "错题归档"), ("questions", "题库")]),
    "11": ("M07 个性化学习规划",
           ["op050", "op051", "op052", "op053", "op054", "op055", "op056"],
           [("plans", "计划 + 任务都在 payload（PATCH 走 op052/op055）"),
            ("profiles", "目标与基础（op004/op005 读取与写回）")]),
    "12": ("M08 模考与估分系统",
           ["op057", "op058", "op059", "op060", "op061", "op062", "op063", "op064"],
           [("papers", "试卷"), ("exam_sessions", "会话与逐题作答 payload"),
            ("exam_submissions", "交卷"), ("exam_results", "成绩"), ("score_snapshots", "估分快照")]),
    "13": ("M09 复试AI训练",
           ["op065", "op066", "op067", "op068", "op069", "op070"],
           [("interviews", "训练会话"), ("interview_turns", "问答轮次"),
            ("interview_reports", "评估报告"), ("files", "语音/录音附件")]),
    "14": ("M11 资讯与政策提醒",
           ["op075", "op076", "op077", "op078", "op079", "op080", "op081"],
           [("notices", "公开政策通知"), ("notice_revisions", "通知历史版本"),
            ("news_follows", "关注设置"), ("milestones", "考试时间节点"),
            ("notifications", "站内通知")]),
    "15": ("T01 教学与指导（op087–op113）",
           ops_of_tag("T01 教学与指导"),
           [("teacher_accounts", "老师账号（登录名分离）"),
            ("teacher_students", "指导关系 status"),
            ("teaching_classes / teaching_class_members", "班级、班级成员"),
            ("teaching_tasks", "任务（class_id、requires_submission、status、version）"),
            ("teaching_submissions", "提交（content≤5000、score 0–100、graded_at）"),
            ("teaching_submission_attachments", "作业附件"), ("teaching_publications", "发布记录")]),
    "16": ("AUTH（邮箱更换）",
           ["op119", "op120", "op004"],
           [("app_users", "email 唯一、email_verified_at"),
            ("email_challenges", "change-email 验证码"), ("auth_tokens", "成功后清 CSRF 会话")]),
    "17": ("ADMIN（op121–op138）",
           ops_of_tag("ADMIN"),
           [("app_users", "account_status：ENABLED / PENDING_ACTIVATION / DISABLED"),
            ("sys_roles / sys_permissions", "角色与权限目录（14 个 ADMIN 权限）"),
            ("sys_user_roles / sys_role_permissions", "分配关系"),
            ("account_invitations", "邀请状态与重发/取消"),
            ("security_audit", "操作日志：reason≤250、trace_id、before→after"),
            ("managed_settings", "系统配置（platform.announcement）")]),
    "18": ("无（前端路由守卫）",
           [],
           [("—", "越权与 404 不读写数据库，纯前端拦截")]),
    "19": ("00 通用技术接口 + 各模块动作",
           ["op001", "op006", "op007", "op008", "op009", "op010"],
           [("jobs / job_commands", "异步任务与等待（op009）"),
            ("event_outbox", "事件发推（op010 SSE）"),
            ("files", "上传与下载（op006–op008）"),
            ("idempotency_records", "写操作幂等")]),
}

lines = ['# -*- coding: utf-8 -*-',
         '"""Per-page contract operations and database tables (generated from',
         'contracts/openapi.json and src/main/resources/db/migration/mysql)."""',
         '',
         'CONTRACT = {']
for key in sorted(SPEC):
    tag, ids, tables = SPEC[key]
    lines.append('    "%s": {' % key)
    lines.append('        "scope": %r,' % tag)
    lines.append('        "ops": [')
    for oid in ids:
        lines.append('            %r,' % fmt(oid))
    lines.append('        ],')
    lines.append('        "tables": [')
    for name, note in tables:
        lines.append('            (%r, %r),' % (name, note))
    lines.append('        ],')
    lines.append('    },')
lines.append('}')
open(os.path.join(ROOT, "drawio", "contract_map.py"), "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
print("wrote contract_map.py; pages:", len(SPEC))
print("total ops referenced:", len({o for _, ids, _ in SPEC.values() for o in ids}))
