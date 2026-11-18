# ZAP 零基础入门与项目实战（结合 UniversityAIUser）

> 读者画像：会用命令行启动自己的项目，但完全没接触过 Web 安全与 OWASP ZAP。
> 目标：3 小时内能独立完成一次“只扫自己项目”的扫描、读懂警报、写出报告；同时建立够用的 Web 安全直觉。
> 环境：Windows 11 + OWASP ZAP 2.17.0（中文界面）+ 本项目（Spring Boot 8080 / Vue 5173）。

---

## 0. 先看这一节：你现在应该做什么

你截图里停在 **“自动扫描”** 页面，目标是 `http://127.0.0.1:5173/login`。**先别点“攻击”。** 原因有三个：

1. 这个站要**登录**才能用，自动扫描是“匿名从首页爬”，登录后的页面它一个都看不到；
2. 项目对 `POST /api/v1/**` 有 **CSRF 校验**，自动扫描发的 POST 会成片 403，你会得到一屏“看起来像漏洞、其实不是”的警报；
3. 自动扫描会**主动发攻击载荷**，可能往你 H2 库里写脏数据（注册假账号、建垃圾任务）。

正确顺序是：

```
先跑起项目 → ZAP 改代理端口(避开 8080) → 手动探索录制 → 被动扫描看警报
→ 小范围主动扫描 → 人工复现/判定 → 出报告 → 恢复数据库
```

每一节的标题都对应你该做的一步，照着做即可。**能跳过的内容我都标了“可选”。**

---

## 1. 先建立直觉：Web 安全到底在防什么

不讲术语，先用一个比喻把整件事串起来。

### 1.1 一个网站 = 一栋办公楼

| 现实中的楼 | 网站里的对应物 |
|---|---|
| 大楼入口、门禁 | 登录、认证（Authentication） |
| 你的工牌能开哪几扇门 | 授权（Authorization）、角色 RBAC |
| 前台登记本 | 会话（Session）、Token |
| 访客说的话、填的表 | 用户输入（参数、表单、JSON、上传文件） |
| 档案室 | 数据库 |
| 大楼的对外公告栏 | 前端页面（HTML/JS） |
| 保安 | 反向代理 / WAF / 各种校验 |

Web 安全就是一句话：**别人说的一切都不可信，必须在“能造成伤害的那一步”之前检查清楚。**

- 访客说“我是 3 号员工” → 不能信，要看工牌（认证）。
- 访客说“我要进 5 楼档案室” → 要看他工牌够不够级别（授权）。
- 访客递来的表格里写着“顺便把 3 号档案室的门打开” → 不能当命令执行（注入）。
- 访客留下的便签被前台当成“内部通知”抄到公告栏 → 公告栏被污染（XSS）。

### 1.2 三个必须记住的词

| 词 | 通俗解释 | 被破坏的后果 |
|---|---|---|
| **机密性 Confidentiality** | 不该看的看不到 | 别人成绩、手机号、题库泄露 |
| **完整性 Integrity** | 不该改的改不了 | 成绩被改、任务被删、公告被替换 |
| **可用性 Availability** | 该能用的时候能用 | 服务被打挂、被刷爆额度 |

安全做的所有事，本质都是在保护这三样。

### 1.3 攻击者怎么找入口：攻击面（Attack Surface）

对你这个项目，攻击面就是这几类：

```
浏览器地址栏/路由        /login /dashboard /admin ...
所有 URL 参数            ?page=1&keyword=xxx
所有请求体字段           {"email":"...","password":"..."}
所有 Cookie              SESSION、AUTH_TOKEN
所有请求头               Origin、Referer、X-CSRF-Token、Content-Type
文件上传/下载            作业附件
第三方回调/跳转          redirect、returnUrl
错误信息/日志            堆栈、SQL、内部路径
```

**扫描器做的事，就是把这几个入口挨个塞“奇怪的东西”，看系统反应。**
它不懂你的业务，只懂模式匹配 —— 所以它一定会**误报**，也一定会**漏报**。这是后面判定警报的前提。

### 1.4 OWASP Top 10 速览（不求背，求有印象）

| 排名 | 类别 | 一句话 | 你项目里对应什么 |
|---|---|---|---|
| A01 | 权限控制失效 | 登录了，但能看到别人的数据 | 学生 A 能否读学生 B 的任务 |
| A02 | 加密机制失效 | 密码明文、HTTP 传密码 | BCrypt 存密码、上线必须 HTTPS |
| A03 | 注入 | 用户输入被当代码/命令执行 | SQL 拼接、`${}`、命令拼接 |
| A04 | 不安全设计 | 流程本身有漏洞 | 验证码可爆破、重置密码可枚举 |
| A05 | 安全配置错误 | 默认口令、调试开关、多余端口 | dev 配置带到生产、错误页泄露堆栈 |
| A06 | 脆弱/过时组件 | 依赖库有已知 CVE | `npm audit`、Maven 依赖 |
| A07 | 身份认证失效 | 弱密码、会话可固定、无限试错 | 登录限流、JWT 撤销、Cookie 属性 |
| A08 | 软件与数据完整性失效 | 反序列化、供应链 | 依赖来源、上传文件校验 |
| A09 | 日志与监控失效 | 被打了不知道 | traceId、审计日志 |
| A10 | SSRF | 让服务器替你去访问内网 | 任何“填 URL 帮我抓取”的功能 |

> 你项目目前没有“填 URL 抓取”的功能，A10 可以先放着；重点看 A01、A03、A05、A07。

---

## 2. ZAP 是什么：一个会“拆快递”的中转站

### 2.1 原理（这张图理解了就懂一半）

```
浏览器  ──►  ZAP（本地代理 127.0.0.1:8090）  ──►  你的应用（5173 / 8080）
                 │
                 ├─ 全部请求/响应都抄一份 → 历史（History）
                 ├─ 边看边挑毛病        → 被动扫描（Passive Scan）
                 └─ 主动塞奇怪东西      → 主动扫描（Active Scan）
```

ZAP 本质是一个**本地 HTTP 代理**。浏览器把请求先发给它，它转发给服务器；响应也先回它，再回浏览器。因为它在中间，所以：

- 能**看见**所有流量（包括 Cookie、Token、JSON 字段）；
- 能**修改**流量（改参数、改请求头、重放请求）；
- 能**自动**分析（被动扫描）和**自动攻击**（主动扫描）。

**它不是黑客工具，是“你自己网站的体检仪”。** 用它扫别人网站 = 违法，见第 12 节。

### 2.2 必须认识的 10 个概念

| 概念 | 中文界面叫什么 | 通俗解释 |
|---|---|---|
| Session | 会话 | 一次扫描的“工程文件”，保存站点树、历史、警报 |
| Sites Tree | 站点树 | 左边那棵树，按域名/路径组织的“地图” |
| History | 历史 | 所有经过 ZAP 的请求流水账 |
| Request/Response | 请求/响应 | 顶部两个页签，看某一条流量的原文 |
| Passive Scan | 被动扫描 | 只“看”不改，流量经过就顺手检查（如响应头缺失） |
| Active Scan | 主动扫描 | 往参数里塞 payload（`' OR 1=1`、`<script>`…）并观察反应 |
| Spider | 传统爬虫 | 解析 HTML 里的链接，一页页跟 |
| AJAX Spider | AJAX 爬虫 | 开真实浏览器执行 JS，能爬到 Vue/React 单页应用 |
| Context | 上下文 | 把一组 URL 圈起来，统一配认证、会话、登录 |
| Alert | 警报 | 一条“疑似漏洞”，带风险等级和置信度 |
| Scan Policy | 扫描策略 | 主动扫描“用哪些攻击、开多猛”的开关集合 |
| Mode | 模式（标准/保护/攻击） | ZAP 的行为约束，见 3.3 |

### 2.3 风险等级 ≠ 真实严重程度

ZAP 给每条警报标 **风险（High/Medium/Low/Informational）** 和 **置信度（High/Medium/Low）**。

- **风险**：这类问题“一般有多严重”；
- **置信度**：ZAP “有多确定”自己没看错。

所以你会看到 `Medium / Low confidence` 这种组合 —— 意思是“这类问题通常中等严重，但我只有五成把握”。**置信度 Low 的先别慌，先复现。**

---

## 3. 第 0 步：把你的项目跑起来

### 3.1 启动后端（8080）

```powershell
# 在项目根目录 D:\STUDY\UniversityAIUser
./scripts/start-local.ps1
```

首次启动、数据库是空的时候，需要先给演示账号设密码（否则 seed 账号无法登录）：

```powershell
$env:LOCAL_STUDENT_PASSWORD = "你自己定的测试密码"
./scripts/start-local.ps1
```

脚本会 `mvn package -DskipTests` 然后运行 `target/kaoyan-ai-server-0.0.1-SNAPSHOT.jar`，profile 为 `local`，数据库是 `.local-data/kaoyan.mv.db`（H2 文件库）。

### 3.2 启动前端（5173）

另开一个窗口：

```powershell
cd frontend
npm run dev
```

### 3.3 验证真的通了

```powershell
Invoke-WebRequest http://127.0.0.1:5173 -UseBasicParsing | Select-Object StatusCode
Invoke-WebRequest http://127.0.0.1:8080/api/v1/session/csrf -UseBasicParsing | Select-Object StatusCode
```

两个都返回 `200` 才算通。**注意**：你之前截图时 5173/8080 都没在监听，ZAP 扫一个“没人接电话”的端口只会得到连接失败。

### 3.4 关键：确认前端是“真实接口模式”

这个项目前端有 **demo / live 两种模式**。demo 模式不连后端，页面数据是假的 —— 那样扫出来的东西毫无意义。

打开 `http://127.0.0.1:5173/login`，确认能真的登录进 dashboard（而不是“演示模式”提示）。如果页面提示演示模式，找登录页的模式切换入口切到真实接口。

### 3.5 扫描前先备份数据库（30 秒，能省你 2 小时）

主动扫描会往库里写垃圾数据。先停掉后端，复制一份：

```powershell
Copy-Item .local-data\kaoyan.mv.db .local-data\kaoyan.mv.db.bak
```

扫完想恢复：停后端 → 把 `.bak` 覆盖回去 → 重启。想彻底重来：停后端 → 删掉 `.local-data` 整个目录 → 重新启动（`seed-local` 会重建演示数据）。

### 3.6 准备三个账号

权限类问题是这个项目最值得测的（学生 / 教师 / 管理员三角色）。提前准备：

| 角色 | 用途 |
|---|---|
| 学生 A | 主扫描账号 |
| 学生 B | 测“A 能不能看 B 的数据”（IDOR/水平越权） |
| 教师 / 管理员 | 测“低权限能不能进高权限接口”（垂直越权） |

本地 seed 里有 `student@example.test`、`student2@example.test`。教师/管理员账号看 `docs/统一邮箱认证与三角色RBAC-交付.md` 里的初始化说明；没有就用管理员后台自己造一个，或用注册流程造学生账号。

---

## 4. 第 1 步：ZAP 初始设置（只做一次）

### 4.1 把代理端口从 8080 改成 8090 —— 这一步不做，后面全是坑

ZAP 默认监听 **8080**，而你的后端**正好是 8080**。两个程序抢一个端口，必然有一个起不来，或者 ZAP 把自己的流量发给自己形成死循环。

> **例外（你本机就是这种情况）**：如果“工具 → 选项 → 网络 → 本地代理”里显示的已经是 **8081**，说明安装时被人改过 —— **保持 8081 即可，不用改成 8090**。判断标准只有一条：这个端口 ≠ 8080，且没被别的程序占用。8090 只是"随便挑一个空闲端口"的举例。

操作（仅当当前端口是 8080 时）：

1. 菜单 **工具 → 选项**（Options）
2. 左侧选 **网络 → 本地代理**（Network → Local Proxy）
3. 地址填 `127.0.0.1`（比 `localhost` 更稳，避免解析到 IPv6 的 `::1`），端口填一个空闲端口（如 **8090**）
4. 确定

配置完可以用下面这条命令确认"各占各的"：

```powershell
netstat -ano | Select-String 'LISTENING' | Select-String ':(8080|8081|8090|5173)\s'
# 某些受限 shell（如被沙箱包裹的）里 Get-NetTCPConnection / Get-Process 会静默返回空，
# 此时上面这条 netstat 是可靠的替代品；也别忘了直接用浏览器打开 5173 验证。
```

以后所有“浏览器代理设置”都指 `127.0.0.1:8090`。

### 4.2 新建一个会话文件

菜单 **文件 → 新建会话**，保存到：

```
D:\STUDY\UniversityAIUser\.local-data\zap\scan-2026-11-14.session
```

**不要**把 `.session` 提交进 git（里面有请求原文、Cookie、Token）。建议在 `.gitignore` 里加一行 `.local-data/`（如果还没有的话）。

会话文件的意义：关掉 ZAP 再打开，站点树、历史、警报都还在，可以接着分析、出报告。

### 4.3 模式保持“标准模式”

ZAP 有三种模式（工具栏那个下拉框）：

| 模式 | 行为 | 什么时候用 |
|---|---|---|
| 标准模式 Standard | 按你点的按钮干活 | **默认用它** |
| 保护模式 Protect | 只碰“范围内（Scope）”的 URL | 想强制自己别手滑扫到外网时 |
| 攻击模式 Attack | 对**所有**经过的流量主动攻击 | **永远别在你日常浏览器上用** |

攻击模式的坑：如果你把系统代理设成 ZAP 又开了攻击模式，你访问的任何网站都会被 ZAP 打一遍 —— 包括学校教务、邮箱。**只用标准模式，靠“选中节点再右键扫描”来控制范围。**

### 4.4 （可选）关掉 HUD

ZAP 启动的浏览器顶部会有一条半透明面板（HUD）。它有用但会挡视线。不习惯就 **工具 → 选项 → HUD** 里关掉，不影响扫描。

### 4.5 语言与字体

界面是中文就没问题。如果某些面板中文乱码，属于字体/编码显示问题，不影响扫描结果；报告里用英文术语反而更好搜索。

---

## 5. 第 2 步：手动探索（最重要的一步）

### 5.1 为什么不用“自动扫描”

你的应用是 **Vue 单页应用 + 登录墙 + CSRF**：

- 传统爬虫只会解析 HTML 里的 `<a href>`，Vue 的页面切换是 JS 路由，它爬不到；
- AJAX 爬虫能执行 JS，但**过不了登录**，只能在登录页打转；
- 自动扫描的“攻击”阶段会对登录接口狂发请求，触发限流（10 次/分钟），后面全是 429。

所以：**用“手动探索”让 ZAP 边看边记，你正常操作，它把每条 API 都录下来。** 这是所有真实项目渗透测试的标准起手式。

### 5.2 操作步骤

1. 打开 **快速开始** 页签；
2. 点 **手动探索**（Manual Explore）；
3. URL 填 `http://127.0.0.1:5173/login`；
4. 勾选“使用传统爬虫”（可选，聊胜于无）；
5. AJAX 爬虫先**不勾**（它慢且噪音大，手动逛更准）；
6. 点 **启动浏览器**。

ZAP 会启动一个自带代理配置的浏览器（Firefox 或 Chrome，取决于你装的版本）。**接下来所有操作都在这个浏览器里做**，不要用你平时的 Chrome —— 那个没走 ZAP 代理，ZAP 看不见。

### 5.3 在这个浏览器里“正常用一遍”

按顺序点，每进一个页面停 2 秒（让 XHR 发完）：

```
/login         → 登录（学生 A）
/dashboard     → 首页
/companion     → 学习陪伴
/schools       → 院校
/public-courses → 公共课
/professional-courses → 专业课
/qa            → 答疑
/practice      → 练习（点几道题，别提交）
/planning      → 规划
/exams         → 考试（只看列表）
/interview     → 面试
/news          → 资讯
/account       → 个人中心
```

顺手做几个**读操作**，让带参数的接口暴露出来：

- 列表翻页（`?page=2`）；
- 搜索框输入一个词（`?keyword=xxx`）；
- 切换筛选/排序；
- 打开任意一条详情（URL 里会出现 uuid）。

**不要做**（会产生副作用或花钱）：

- 删除任何东西；
- 提交作业/答卷；
- 触发 AI 生成（如果接了模型，一次生成就是真金白银）；
- 连续点登录（会触发限流，把你自己锁在门外）。

### 5.4 观察“录到了什么”

回到 ZAP：

- 左侧 **站点树** 应该长出 `127.0.0.1` 下的 `5173` 和 `8080` 两棵树；
- 底部 **历史** 页签里是流水账，按 URL 过滤 `api/v1` 能看到所有接口调用；
- 点任意一条，顶部 **请求/响应** 页签显示原文。

判断录制成功的标准：**历史里能看到 `POST /api/v1/auth/login` 返回 200，并且响应头里有 `Set-Cookie: AUTH_TOKEN=...`。** 看不到就说明登录没走 ZAP 代理，回到 5.2 重来。

### 5.5 把目标“圈进范围”（可选但推荐）

在站点树上右键 `http://127.0.0.1:5173` → **包括在范围中**（Include in Scope）；对 `8080` 同样操作。之后切到“保护模式”也不会扫到别处，报告里也只统计范围内目标。

---

## 6. 第 3 步：读懂被动扫描

### 6.1 它在你逛的时候已经干活了

被动扫描不需要你点任何按钮。它只检查“响应里有什么”，典型规则：

- 响应头缺 `Content-Security-Policy`、`X-Frame-Options`、`Strict-Transport-Security`；
- Cookie 缺 `Secure` / `HttpOnly` / `SameSite`；
- 响应里出现疑似注释、内网 IP、邮箱、堆栈、版本号；
- 响应里引用了有已知漏洞的前端库版本。

打开底部 **警报**（Alerts）页签，数字就是条数。

### 6.2 一条警报怎么读

点开任意一条，右侧详情区有这几块（中文版顺序可能略有不同）：

| 字段 | 含义 | 你该怎么用 |
|---|---|---|
| 名称 Name | 规则名，如 “Content Security Policy (CSP) Header Not Set” | 拿去搜官方文档 |
| 风险 Risk | High/Medium/Low/Info | 先看 High/Medium |
| 置信度 Confidence | ZAP 有多确定 | Low 的先复现再下结论 |
| URL | 命中的具体地址 | 点开看是哪条请求 |
| 参数 Parameter | 命中的参数名 | 没有参数说明是“整条响应”的问题 |
| 攻击 Attack | 实际发出去的 payload | 主动扫描才有 |
| 证据 Evidence | 响应里匹配到的片段 | **最有用**，直接告诉你它看到了什么 |
| 解决方案 Solution | 官方建议 | 通常是通用建议，要结合你的代码 |
| 参考 References | 链接 | CWE / OWASP 页面 |

### 6.3 最重要的一课：警报要分三类

拿到任何一条警报，先归类，再决定要不要改代码：

1. **真问题（要修）**：能复现、有实际影响。比如“注册接口能枚举邮箱”。
2. **环境噪音（不用修，但要记录）**：本地 HTTP 所以 Cookie 没 `Secure`；Vite dev server 不设 CSP；H2 控制台。**生产环境根本不会这样。**
3. **误报（不用修，但要能解释）**：扫描器不懂业务。比如“缺少 Anti-CSRF Token”——你明明有 CSRF，只是它没带 token 所以被 403 挡了。

> 写报告时，第 2、3 类也要写进去，但要写清“为什么不修”。这比只列漏洞专业得多。

### 6.4 你项目里“大概率会出现”的被动警报

先给你打个预防针，看到这些别慌（详见第 10 节表格）：

- `Content Security Policy (CSP) Header Not Set`
- `X-Frame-Options Header Not Set`（5173 的页面会有；8080 的 Spring 默认一般有）
- `Cookie Without Secure Flag`（本地 HTTP，必然）
- `Absence of Anti-CSRF Tokens`（大概率误报）
- `Information Disclosure - Suspicious Comments`（Vite 开发产物）
- `Modern Web Application`（Info，只是在说“这是个 SPA”）
- `Server Leaks Version Information`（Tomcat 的 `Server` 头）

---

## 7. 第 4 步：主动扫描（小范围、只读优先）

### 7.1 先明确风险

主动扫描会真的发攻击载荷。对你的项目意味着：

- 可能写入垃圾数据（注册、创建、提交）；
- 可能触发登录限流，把测试账号锁几分钟；
- 可能让 AI 模块产生真实调用费用；
- 可能把 H2 库搞脏（所以 3.5 的备份很重要）。

### 7.2 正确的选法：选中“一个接口”，不是“整个站点”

1. 在 **历史** 里过滤 `api/v1`，挑一条 **GET** 请求（比如列表、详情）；
2. 右键这条请求 → **攻击 → 主动扫描**（Attack → Active Scan）；
3. 弹窗里确认“起始 URL”就是这一条，点 **开始**；
4. 底部 **主动扫描** 页签看进度，随时可以停。

**为什么只选 GET**：GET 按设计就是只读的，扫它不会写数据；POST/PUT/DELETE 扫起来会真的改库。等你熟悉了，再单独挑一两个 POST 接口、在备份过的库上扫。

**为什么不要右键整棵树**：那会把登录、注册、上传、AI 接口全扫一遍，噪音和副作用都不可控。

### 7.3 扫描策略（Scan Policy）怎么调

第一次用默认策略就行。想更克制：

**分析 → 扫描策略**（或 工具 → 选项 → 主动扫描）里可以：

- 关掉 `SQL Injection` 之外的“高风险破坏性”规则；
- 把线程数从默认降到 2~5（本地 H2 扛不住高并发，线程太多会 500 一片，反而误导你）；
- 设置“每个参数最多发多少请求”。

### 7.4 扫描时你会看到大量 403 / 429 / 500

这是**预期行为**，不是漏洞：

- `403 CSRF_REJECTED`：主动扫描的 POST 没带 `X-CSRF-Token`；
- `429 RATE_LIMITED`：登录限流生效了（说明限流是好的）；
- `500`：某些 payload 让后端抛异常，但 `ApiAdvice` 只回一句“服务发生异常，请通过 traceId 查询日志”，**不回堆栈** —— 这本身是正确设计。

判断标准：**看响应体有没有泄露内部信息**，而不是看状态码。

---

## 8. 第 5 步：复现与判定（这一步决定你是不是真的会了）

### 8.1 用“手动请求编辑器”改一个字节再发

1. 历史里右键任意请求 → **打开/发送到 → 手动请求编辑器**（Manual Request Editor）；
2. 上半部分是原始请求文本，改参数、改 header、改 Cookie；
3. 点 **发送**，下半部分看响应。

这是安全测试最核心的动作：**控制变量，改一处，看差异。**

### 8.2 三个必练的手工实验

**实验 1：不带 CSRF Token 发 POST**

- 复制一条正常的 `POST /api/v1/auth/login`；
- 删掉 `X-CSRF-Token` 头，重发；
- 预期：`403`，响应体里是 `CSRF_REJECTED`。
- 说明：CSRF 防护在工作。如果不带 token 还能成功，才是真漏洞。

**实验 2：换一个角色访问**

- 用学生 A 登录，复制一条 `/api/v1/...` 的 GET；
- 用学生 B 的 Cookie 重放（或直接改路径里的 uuid）；
- 预期：要么 404（数据不属于你），要么 403；**如果返回了 B 的数据，就是水平越权（IDOR），高危。**
- 再用学生账号访问 `/api/v1/admin/**` 或 `/api/v1/teacher/**`，预期 `403 FORBIDDEN`；**如果 200，就是垂直越权，高危。**

**实验 3：伪造 Origin 看 CORS**

- 复制一条带 Cookie 的 GET；
- 加请求头 `Origin: https://evil.example`；
- 看响应有没有 `Access-Control-Allow-Origin: https://evil.example` + `Access-Control-Allow-Credentials: true`。
- 预期：没有（白名单里只有 5173）。**有的话就是严重问题**：任意网站都能带着用户 Cookie 读你的接口。

### 8.3 判定四问

每条警报过一遍：

1. **能复现吗？** 手工重放一次，结果一致吗？
2. **有实际影响吗？** 泄露了什么？能改什么？能拿谁的数据？
3. **需要什么前提？** 要登录？要受害者点链接？要内网？
4. **生产环境也存在吗？** 还是只是 dev 配置？

四问都过了，才写进“确认漏洞”。

---

## 9. 第 6 步：出报告

菜单 **报告 → 生成报告**：

1. 模板选 **Traditional HTML Report**（最通用）或 **Modern HTML Report**（好看）；
2. 范围选“站点”或“上下文”，确认只包含 `127.0.0.1`；
3. 生成到：

```
docs/security/2026-11-14-zap-report.html
```

4. 报告里会有：风险汇总、每条警报的请求/响应、解决方案。

**给谁看很重要**：

- 给自己：HTML 够了；
- 给老师/答辩：把 High/Medium 摘出来，配“复现步骤 + 影响 + 修复建议 + 复测结果”，别甩一份 200 页的自动报告；
- 给团队：在 `docs/security/` 下另写一份 `YYYY-MM-DD-扫描结论.md`，只列“确认/误报/环境噪音”三类。

---

## 10. 结合你的项目：预期结果清单

### 10.1 这些是“已经做对了”的，别再当漏洞报

| 你已经有的防护 | 代码位置 | 扫描时的表现 |
|---|---|---|
| JWT 放 HttpOnly Cookie，`SameSite=Lax`，`Path=/api/v1` | `JwtService.java:116-118` | 警报若说“Cookie 无 HttpOnly”多半指别的 cookie |
| 所有非 GET 的 `/api/v1/**` 校验 CSRF，token 存 Session 不落 Cookie | `SecurityConfiguration.java:41-43`、`SessionCsrfTokenRepository.java` | 不带 token 的 POST 一律 403 |
| 401/403 统一响应，500 只回 traceId | `ApiAdvice.java:51-55` | 主动扫描打不出堆栈 |
| CORS 白名单 + `allowCredentials(true)` | `SecurityConfiguration.java:35-39` + `application.yml` | 伪造 Origin 不会回 ACAO |
| 登录限流 10 次/分钟/IP | `EmailAuthService.java:168-174` | 狂发登录会 429 |
| BCrypt(12) 存密码 | `SecurityConfiguration.java:30` | 没有明文密码 |
| 上传 10MB 上限 | `application.yml` | 超大文件 413/400 |
| 后端只监听 127.0.0.1 | `application.yml`（`server.address`） | 局域网访问不到，天然缩小攻击面 |
| Vue 组件未使用 `v-html` | `frontend/src/**/*.vue` | XSS 面很小 |
| 前端构建 `sourcemap: false` | `frontend/vite.config.ts` | 生产包不泄露源码 |

> 提示：`JwtService` 里 `secure` 取的是 `${server.servlet.session.cookie.secure:false}`。**上线必须置 true 并全站 HTTPS**，否则 Cookie 会在 HTTP 明文里传。

### 10.2 扫描大概率会报、但属于“环境噪音”的

| 警报 | 为什么出现 | 处理 |
|---|---|---|
| `Content Security Policy (CSP) Header Not Set` | Vite dev server 不设 CSP | 生产在 Nginx/网关加 CSP |
| `X-Frame-Options Header Not Set` | 5173 的 HTML 由 Vite 返回 | Spring 默认有；生产统一加 |
| `Cookie Without Secure Flag` | 本地是 `http://` | 生产 `secure=true` |
| `Strict-Transport-Security Header Not Set` | 没有 HTTPS | 上 HTTPS 后再加 HSTS |
| `Permissions-Policy Header Not Set` | 没配 | 网关加一行 |
| `Information Disclosure - Suspicious Comments` | Vite 开发产物带注释 | 生产构建会压缩 |
| `Modern Web Application` | 识别出 SPA | Info，忽略 |
| `Server Leaks Version Information` | Tomcat 回 `Server` 头 | 生产可关 |

### 10.3 需要你人工确认的 6 件事（扫描器测不出来，但最值钱）

**① 注册接口的邮箱枚举（很可能成立）**

`EmailAuthService.java:65-71` 在邮箱已存在时抛 `409 ACCOUNT_EXISTS`。攻击者可以拿邮箱列表批量试，判断谁注册过。

- 怎么测：注册一个已存在的邮箱，看是否明确提示“账号已存在”。
- 影响：Low~Medium（隐私 + 撞库前置情报）。
- 修法：统一返回“如果该邮箱可用，我们已发送验证邮件”，不区分存在与否；或加图形验证码 + 频率限制。

**② `${table.name}` 表名拼接（必须确认）**

`src/main/resources/mapper/AggregateMapper.xml:15` 用了 `${table.name}`。`${}` 是**直接字符串拼接**（`#{}` 才是参数化）。如果 `table` 来自用户输入 → SQL 注入；如果来自代码里的常量枚举 → 安全。

- 怎么查：

```powershell
# 找谁调用了 AggregateMapper，参数从哪来
Select-String -Path src\main\java\**\*.java -Pattern "AggregateMapper" -List
```

- 结论标准：调用链上游如果是 `switch`/枚举/白名单常量 → 安全，写进报告“已确认白名单”；如果参数一路来自 `@RequestParam` → 立即改。

**③ 水平越权（IDOR）**

拿学生 B 的 Cookie 去请求学生 A 的任务/成绩 uuid。响应 200 且是 A 的数据 = 高危。

**④ 垂直越权**

学生账号请求 `/api/v1/admin/**`、`/api/v1/teacher/**`。返回 200 = 高危。

**⑤ 退出登录后旧 token 还能不能用**

点退出 → 用旧 `AUTH_TOKEN` 重放一个需要登录的 GET。还能用 = 会话撤销没生效（代码里有 jti 撤销机制，值得验证）。

**⑥ 上传文件的真实校验**

把一个 `.exe` 改名成 `.pdf` 上传。README 说做了 magic number 校验；实测一下是否真的 400。

> 这 6 项做完，你的报告深度会超过 90% 的“跑一遍扫描器就交”的作业。

---

## 11. 进阶 A：用 OpenAPI 契约导入全部接口

手动探索只能覆盖“你点到的”接口。契约里有 148 步，很多接口 UI 不会触发。做法：

1. 先让后端跑起来；
2. 菜单 **导入 → 导入 OpenAPI 定义**（Import → Import an OpenAPI Definition）；
3. URL 填 `http://127.0.0.1:8080/contract/openapi.json`（或选本地文件 `contracts/openapi.json`）；
4. 导入后站点树下会出现一个 OpenAPI 节点，每个 operation 一条请求。

**问题**：这些请求没有 Cookie，全是 401。

**解决办法（够用的版本）**：

1. 在 ZAP 浏览器里正常登录；
2. 历史里找到 `POST /api/v1/auth/login` 的响应，复制 `Set-Cookie` 里的 `AUTH_TOKEN=...`；
3. 工具 → 选项 → **替换器（Replacer）**，新增一条：
   - 类型：`请求头`
   - 匹配字符串：`Cookie`
   - 替换为：`AUTH_TOKEN=你复制的值`
   - 勾选“启用”，URL 匹配 `http://127.0.0.1:8080.*`
4. 再对 `X-CSRF-Token` 加一条（值从 `GET /api/v1/session/csrf` 的响应里取，并且要和同一个 `SESSION` cookie 配对）。

**坑**：JWT 默认 30 分钟过期（`ttl: 1800`），扫到一半就全 401 了。所以 OpenAPI 路线适合“快速覆盖 + 只扫 GET”，别指望它跑长任务。

**更正规的做法**（以后再看）：给 ZAP 配 Context 认证 —— 用“基于脚本的认证”先 `GET /session/csrf` 拿 token → `POST /auth/login` 拿 Cookie → 后续请求自动带上。这需要写一小段 JS，等你熟悉了再碰。

---

## 12. 合规红线（请认真读）

- **只扫你自己的、或明确书面授权的系统。** 本项目在你本机，没问题。
- **不要扫学校教务、图书馆、邮箱、任何 `*.edu.cn`。** 即使“只是看看”，也可能触犯《网络安全法》第 27 条和《刑法》285 条。
- **不要在 ZAP 里开“攻击模式”去逛外网。**
- **不要在报告/截图/提交里放真实用户数据**（手机号、身份证、真实密码）。测试数据一律用假数据。
- **主动扫描只在你备份过数据库的本地环境做。**
- ZAP 的 `.session` 文件和导出的报告都可能含 Cookie/Token，**别提交到 git、别发群里**。

---

## 13. 7 天练法（每天 1~1.5 小时）

| 天 | 做什么 | 产出 |
|---|---|---|
| D1 | 跑通项目 + ZAP 改端口 + 手动探索完整逛一遍 | 站点树里有 5173 和 8080，历史里有登录 200 |
| D2 | 只做被动扫描，精读 5 条警报 | 每条写一句“真问题/噪音/误报” |
| D3 | 挑 3 个 GET 接口做主动扫描 + 生成报告 | 一份 HTML 报告 |
| D4 | 做 8.2 的三个实验 + 10.3 的 ①②⑤ | 一页“确认/存疑”清单 |
| D5 | PortSwigger Web Security Academy 的 Apprentice 前 10 关 | 通关截图 |
| D6 | 对照 OWASP Top 10 给自己项目写一页评估 | 每个 A0x 一句结论 |
| D7 | 修 1~2 个真问题（比如邮箱枚举）并复测 | 修复前后对比 |

**练习靶场**（都在本地跑，合法）：

- **OWASP Juice Shop**：`docker run -p 3000:3000 bkimminich/juice-shop`，最像真实 SPA，和你的技术栈最接近；
- **DVWA**：经典，用来理解 SQLi/XSS/CSRF 原理；
- **PortSwigger Academy**：免费、带讲解，最好的系统课。

---

## 14. 安全基础速查（每条都对应你的代码）

### 14.1 Cookie 与会话

- `HttpOnly`：JS 读不到 → 防 XSS 偷 token。你有（`JwtService.java:116-118`）。
- `Secure`：只在 HTTPS 发 → 防明文嗅探。本地 false，**上线必须 true**。
- `SameSite=Lax`：跨站 POST 不带 Cookie → 防 CSRF 的第一道墙。你有。
- `Path=/api/v1`：Cookie 只发给 API 路径，减小暴露面。你有。

### 14.2 CSRF（跨站请求伪造）

比喻：你登录了银行，浏览器存着“已盖章介绍信”；坏人让你点一个链接，浏览器自动带着介绍信去转账。

防御：**介绍信之外再要一个只有本站页面才知道的暗号**（CSRF Token）。你的实现：

- 前端先 `GET /api/v1/session/csrf` 拿 token；
- 每个非 GET 请求带 `X-CSRF-Token`；
- 后端 `SessionCsrfTokenRepository` 把 token 存在 HTTP Session 里比对。

**为什么 JSON API 也要 CSRF**：因为浏览器会自动带 Cookie，跟 `Content-Type` 无关。

### 14.3 JWT

比喻：一次性腕带，上面写着“我是谁、什么角色、什么时候过期”，服务器验签后信它。

要点：

- 放 **HttpOnly Cookie** 比放 `localStorage` 安全（后者被 XSS 一偷一个准）；
- 要有**过期时间**（你 1800s）和**撤销机制**（你有 `jti`）；
- 签名密钥不能弱、不能进 git；
- 别把敏感信息塞进 payload（Base64 不是加密，谁都能解）。

### 14.4 XSS

比喻：别人在你的公告栏上贴了一张“会自动执行”的纸条，每个来看的人都中招。

- 存储型：脏数据进了数据库，所有访问者中招；
- 反射型：脏数据在 URL 里，骗你点链接；
- DOM 型：前端 JS 把不可信字符串当 HTML 插入。

防线：输出编码 + `v-html` 少用/不用（你没用，很好）+ CSP 兜底。

### 14.5 SQL 注入

比喻：你把“我要查 3 号档案”和“顺便把档案室烧了”写在同一张纸条上，档案员照做了。

防线：**参数化查询**（`#{}`）。`${}` 是字符串拼接，只在表名/列名这类“不能参数化”的地方用，且必须来自白名单。

### 14.6 越权（最常被忽视，也最致命）

- 水平越权（IDOR）：改 uuid 看别人的数据；
- 垂直越权：低角色调高角色接口。

防线：**每个接口都要在服务端校验“这条数据属不属于当前用户”**，不能只靠前端隐藏按钮。你的 `SecurityConfiguration` 管了“路径级”角色，但“数据级归属”要在 Service 里逐个确认 —— 这正是第 10.3 ③④ 要测的。

### 14.7 CORS

它不是防护，是**放宽**。配错（`*` + credentials）等于把接口对全网开放。你的白名单写法是对的。

### 14.8 限流与账号安全

- 登录限流 10 次/分钟/IP：防撞库（你有）；
- 注册/找回密码也要限流（否则枚举 + 短信轰炸）；
- 错误提示要**模糊**：“邮箱或密码错误”，不要说“该邮箱未注册”。

### 14.9 日志与可观测性

- 500 只回 traceId，堆栈进日志（你有）—— 既防泄露又方便排查；
- 安全事件（登录失败、越权尝试、上传拒绝）要单独记，方便事后审计。

---

## 15. 常见问题

**Q1：ZAP 启动后浏览器打不开页面 / 一直转圈？**
代理端口冲突。确认 ZAP 监听端口（8090 或你本机已有的 8081）≠ 后端 8080，且浏览器代理指向 ZAP 那个端口。

**Q2：历史里一条 `api/v1` 都没有？**
你用的是自己平时的浏览器，没走 ZAP 代理。用 ZAP “手动探索”启动的那个浏览器。

**Q3：全是 401？**
登录没成功，或 JWT 过期（30 分钟）。重新登录，或在 Replacer 里更新 `AUTH_TOKEN`。

**Q4：全是 403 CSRF_REJECTED？**
正常。主动扫描不带 CSRF token。要测写接口，先手工在请求编辑器里补上 token 再发。

**Q5：全是 429？**
登录限流。等 1 分钟，或换 IP/换账号，别硬刚。

**Q6：扫描把库搞脏了？**
停后端 → 用 `.local-data\kaoyan.mv.db.bak` 覆盖 → 重启。彻底重来就删 `.local-data` 目录。

**Q7：报告里 200 多条警报，怎么看得完？**
按风险排序，只看 High/Medium；Low/Info 里 90% 是响应头。再看“同一规则命中很多 URL”的，通常是配置问题，一次修完。

**Q8：ZAP 很卡？**
关掉 AJAX 爬虫；主动扫描线程降到 2~3；扫描期间别同时开一堆浏览器标签。

**Q9：中文界面找不到某个菜单？**
ZAP 版本间菜单名会变。用关键词找：代理=Local Proxy、替换器=Replacer、范围=Scope、上下文=Context、断点=Break。

**Q10：我能用 ZAP 扫 5173 吗？扫 8080 吗？**
都要。5173 看前端页面/静态资源/响应头；8080 看 API。两个都圈进范围。

---

## 16. 术语中英对照

| 中文 | English | 一句话 |
|---|---|---|
| 代理 | Proxy | 中间人，ZAP 的本体 |
| 站点树 | Sites Tree | 目标地图 |
| 历史 | History | 流量流水账 |
| 被动扫描 | Passive Scan | 只看不改 |
| 主动扫描 | Active Scan | 塞 payload 试探 |
| 爬虫 | Spider | 跟链接 |
| AJAX 爬虫 | AJAX Spider | 开浏览器跑 JS |
| 上下文 | Context | 一组 URL + 认证配置 |
| 范围 | Scope | 只关心哪些目标 |
| 警报 | Alert | 疑似漏洞 |
| 风险 | Risk | 这类问题一般多严重 |
| 置信度 | Confidence | 扫描器多确定 |
| 扫描策略 | Scan Policy | 用哪些攻击 |
| 断点 | Break | 拦下请求改完再放行 |
| 替换器 | Replacer | 自动改 header/body |
| 模糊测试 | Fuzzer | 批量换 payload |
| 强制浏览 | Forced Browse | 猜隐藏路径 |
| HUD | Heads Up Display | 浏览器里的 ZAP 面板 |

---

## 17. 一页速查卡

```
启动：./scripts/start-local.ps1（后端 8080） + cd frontend; npm run dev（前端 5173）
备份：Copy-Item .local-data\kaoyan.mv.db .local-data\kaoyan.mv.db.bak
ZAP ：代理 127.0.0.1:8090 或 8081（只要不是 8080！）→ 新建会话 → 标准模式
录制：快速开始 → 手动探索 → 127.0.0.1:5173/login → 登录 → 逛全部路由
被动：警报面板，按 风险 排序，先看 High/Medium
主动：历史过滤 api/v1 → 挑 GET → 右键 → 攻击 → 主动扫描
复现：右键 → 手动请求编辑器 → 改一处 → 发送 → 对比
报告：报告 → 生成报告 → Traditional HTML → docs/security/
判定：能复现？有影响？要什么前提？生产也有？
红线：只扫自己项目；不开攻击模式；不扫学校系统；不上传真实数据
```

---

## 附：生产环境安全响应头（等你部署时用）

Vite dev server 不会加这些，Spring 默认只加了一部分。上线时在 Nginx 统一加：

```nginx
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

并确认：

```yaml
server:
  servlet:
    session:
      cookie:
        secure: true   # 生产必须 true
```

---

*本文档基于 2026-11-14 的代码勘察编写。若后续接口/路由有改动，以 `contracts/openapi.json` 与 `frontend/src/router/index.ts` 为准。*
