本仓库包含同一个软件项目的后端 UniversityAIUser 与前端 UniversityAIUserPage：后端位于根目录，最新前端源码收录在 `frontend/`。登录／注册与 JWT 认证说明见 [登录注册与JWT](docs/登录注册与JWT.md)。

学生、老师、管理员统一使用邮箱登录。发码、老师邀请、首位管理员初始化、数据库权限、迁移与验证见 [统一邮箱认证与三角色 RBAC 交付](docs/统一邮箱认证与三角色RBAC-交付.md)。桌面版 `UniversityAIUserPage` 与仓库版 `frontend/` 均已适配。

当前包目录已按功能归组，阅读顺序见 [包目录与调用顺序](docs/包目录与调用顺序.md)。

当前后端已迁移为 MyBatis 分层结构，目录、数据兼容及 MySQL 验证见 [MyBatis迁移与MySQL验证](docs/MyBatis迁移与MySQL验证.md)。

迁移前的后端源码阅读说明见 [后端项目说明：包与类详解](docs/后端项目说明-包与类详解.md)，按包解释全部正式类、内部类型及测试类，并说明架构、关键方法、调用流程、数据表和启动配置。

# 大学生考研 AI 导学系统

后端以原课程文档的 11 个模块和 `contracts/openapi.json` 为范围，独立构建及本地运行。前端是 Vue 3 + TypeScript + Vite 项目，使用说明见 [frontend/README.md](frontend/README.md)。前后端统一保存在本仓库，分别构建和启动。已配置本机 MySQL；外部模型与 Nginx 尚未配置。

克隆仓库后，在根目录按下文配置并启动后端；另开终端进入 `frontend/`，执行 `npm ci`、`npm run dev` 启动前端。本机数据库、密码和模型密钥需要自行配置，参考 `.env.example`；依赖目录、构建产物和本机私有配置不提交到 Git。数字人三种模式的演示、迁移和验证见[数字人陪伴督学](docs/数字人陪伴督学.md)。

## IDEA 启动

1. Reload Maven Project，项目 SDK 和 Maven Runner JRE 设置为 JDK 17。本机路径为 `C:/Program Files/Java/jdk-17`。
2. 运行 `com.kaoyan.ai.KaoyanAiApplication`。不填写 profile 时默认 `local`。
3. 看到 `Tomcat started on port 8080` 后，使用 `http://127.0.0.1:8080`。
4. `GET /api/v1/session/csrf` 可以检查服务；契约下载地址为 `/contract/openapi.json`。

也可以在项目终端运行：

```powershell
./scripts/start-local.ps1
```

local 配置使用 `.local-data/kaoyan.mv.db`，不是你的 MySQL 数据库。Flyway 自动建表，第一次启动生成明确标记为联调样例的院校、知识点、题目和练习卷。重新启动保留个人业务数据；会话需要重新登录。

新建本地联调库的演示账号使用 `student@example.test` 和 `student2@example.test`；首次启动前在 IDEA 或终端设置 `LOCAL_STUDENT_PASSWORD`。密码以 BCrypt 哈希存储；更改该变量不会改写已有账号。已有用户按历史账号认领流程绑定真实邮箱。

本地 H2 密码通过 `LOCAL_DB_PASSWORD` 配置；未设置时为空，仅用于兼容现有本地 H2 文件库，已有文件必须继续使用原密码。新建文件库可在首次启动前设置非空值。MySQL 后端继续读取 `MYSQL_PASSWORD`；Docker Compose 创建 MySQL 容器时必须另外设置 `MYSQL_ROOT_PASSWORD`，缺失或为空会直接报错。`.env.example` 只是说明，需将变量配置到启动进程；Docker Compose 可读取项目根目录下未提交的 `.env`。这些配置均不会自动修改数据库服务器或现有用户密码。

## 前端协作

前端启动 Vite 开发服务器后切换“真实接口模式”。开发代理将 `/api/v1` 原路径转发到本后端 8080 端口。前端无需复制到后端，不加入 Maven 构建，也没有新增部署要求。

所有路径、请求方法和字段以 [OpenAPI 契约](contracts/openapi.json) 为准。邮箱认证使用 `/auth` 接口，JWT 经 HttpOnly `AUTH_TOKEN` Cookie 签发；`/session/login` 兼容同一邮箱凭据。注册、登录前获取 CSRF，成功后再获取一次，所有写请求均须携带绑定到会话的 `X-CSRF-Token`。幂等写请求还须携带 UUID `Idempotency-Key`；有 `requestId` 或 `eventId` 时二者必须一致。

知识追踪的训练批次、推理服务协议和清单落地状态见 [知识追踪接入说明](docs/知识追踪接入说明.md)。

## 当前业务行为

| 模块 | 本地行为 |
| --- | --- |
| M01 数字人 | 形象资源、模式/提醒配置、真实打卡、连续天数、督学及任务提醒；文字/音频模型交互走异步任务。 |
| M02 择校 | 筛选、推荐、查询统计、2–4 校对比；没有校准招生样本时概率为 null。 |
| M03 公共课 | 三科知识树和考点查询；讲解、英语/政治/数学辅导接入结构化模型。 |
| M04 专业课 | 科目、知识体系、题目列表、书籍章节讲解接入；题库历史频率分析不声称押题命中。 |
| M05 答疑 | 私有图片上传、OCR 任务、用户确认文字、多轮版本控制、消息历史、取消任务；模型不得在 DTO 中返回最终答案字段。 |
| M06 练习 | 每日/每周/专项会话、客观题评分、错题归档与历史、同类题、经验掌握率；主观题走模型评分。 |
| M07 计划 | 按时间和基础分配日任务，支持日/周/月视图；生成草稿、修改、确认、生效、完成、调整草稿和进度。 |
| M08 模考 | 服务端计时、自动保存、只读恢复、到时自动交卷、客观题评分、主观题模型估分、匿名排名、同量尺成绩快照估分。 |
| M09 复试 | 专业/英语/压力会话、同意约束、文字或音频作答、报告、自我介绍批改及礼仪流程。 |
| M10 数据 | 学习事件幂等写入、重叠区间去重、中国标准日期聚合、正确率、薄弱点和规则周报；专注度来自用户自评。 |
| M11 政策 | 关注院校、通知、版本差异、时间节点、通知已读；可配置本机已核实政策 JSON 文件，更新后按关注范围推送。 |

模型没有配置时，任务明确进入 `failed_retryable` 并返回 `DEPENDENCY_UNAVAILABLE`，不会用固定答案冒充真实 AI。客观题模考和规则计划不需要模型。主观题评分失败时保留客观题成绩，`totalScore=null`，不参与排名。示例卷不是全真真题，示例院校没有虚构招生数字和真实日期。

## 模型和音频接入

在 IDEA 的运行配置中设置环境变量：

```text
AI_LLM_BASE_URL=<兼容服务的完整 /v1 地址>
AI_LLM_API_KEY=<自己的密钥>
AI_LLM_MODEL=<文本模型>
AI_VISION_MODEL=<支持图像的模型>
AI_AUDIO_MODEL=<支持 /audio/transcriptions 的模型>
```

`.env.example` 是配置说明，Spring Boot 不会自动读取该文件。外部服务须支持 JSON 输出及对应视觉/音频能力；返回值再通过契约校验。当前文字模型适配为非流式调用，SSE 提供任务完成、失败、模考完成、提醒和政策更新；未声称已验证外部模型的实时增量输出。

私有文件限制 10 MiB，文件头须与 MIME、用途一致。WAV 由 JDK 检查实际时长并限定 600 秒。MP3、WebM、OGG 可通过环境变量 `APP_FFPROBE_EXECUTABLE` 指定本机 ffprobe 的完整路径；未配置时明确返回依赖不可用。语音转写、合成/播放能力需相应服务或前端浏览器提供；后端不虚构音频文件 ID。

## 本机 MySQL 连接

2026-10-15 已连接本机 MySQL 8.0.46（127.0.0.1:3306），创建 `kaoyan_ai` 数据库并由 Flyway 成功执行 V1 迁移：37 张业务/技术表，加 1 张 Flyway 历史表。没有改动其他数据库。MySQL 模式启动后，`GET /api/v1/session/csrf` 已返回成功。

在 IDEA 顶部选择 **KaoyanAi MySQL** 运行配置，再点击运行；或执行 `./scripts/start-mysql.ps1`。配置使用 Java 17、`--spring.profiles.active=mysql` 和项目根工作目录。普通主类运行仍默认 local（H2），便于原有本地验证。

`application-mysql.yml` 读取 `.local-data/mysql-local.properties` 中的本机连接信息。该文件已被 Git 忽略，不能提交；源码、IDEA 运行配置与文档均不包含真实密码。环境变量 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USER`、`MYSQL_PASSWORD` 仍可覆盖本地值。

mysql 配置不会注入 H2 本地种子账号或样例资料，目前新库的 `app_users` 为空，`student` 演示账号不能直接用于 MySQL 登录。账号和课程初始化属于后续业务配置，不自动迁移 H2 数据。

持久化按聚合使用独立表，公共目录快照与用户资源通过 owner 隔离；聚合的子项保存在受契约约束的 JSON 文本中。版本列执行乐观锁，写业务通过事务和用户行锁串行化；幂等响应与业务修改在同一事务保存。保留既有聚合 JSON 存储格式，持久化改由 MyBatis Mapper 接口和 XML 执行；无需重建数据库或迁移已有数据。Mapper 实体位于 `entity`，SQL 位于 `src/main/resources/mapper/`。本机 Redis 已作为可选缓存接入，说明见下文；当前未接入 OSS。

## 本机 Redis 连接

2026-11-17 已接入本机 Windows 服务 `Redis`（安装目录 `D:\Redis`，127.0.0.1:6379）。连接参数位于 `application.yml` 的 `spring.data.redis`，密码等本机值放在被 Git 忽略的 `.local-data/redis-local.properties`；也可用 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DB`、`REDIS_KEY_PREFIX` 覆盖。启动日志出现 `Redis 连接成功` 表示连接、PING 和读写校验都通过；Redis 未启动时只记录告警，应用继续按数据库启动。Redis 只承担缓存，不是业务数据源，也不保存密码、Cookie、JWT 或学生隐私原文。详见 [本机 Redis 连接说明](docs/本机Redis连接说明.md)。

## 验证

```powershell
./scripts/test-backend.ps1
./scripts/test-mysql.ps1  # 显式连接既有 MySQL，测试写入回滚
./scripts/test-apifox.ps1
```

Spring 测试通过 MockMvc 调用实际控制器、服务和独立 H2 数据库。模型成功分支使用仅存在于 test 目录的明确测试替身；生产源码没有 Mock 模型回退。它们不能代替真实模型账号联调或 MySQL 验证。

`apifox/backend-local.apifox-cli.json` 含148个串行请求步骤和断言，按当前前端使用 JWT Cookie 登录／退出，传递 CSRF、幂等键、ID 与版本，日期按 Asia/Shanghai 运行时生成。先启动独立测试后端，再运行脚本；首次会将 Apifox CLI 安装到项目 `.tools/apifox`。`apifox/backend-local.postman_collection.json` 可导入客户端。另提供12步 `auth-jwt` 集合，`./scripts/test-apifox.ps1 -Suite auth` 无需预置账号，但会创建并保留测试账号，应仅用于独立测试库。

Apifox 的 4 次任务轮询依赖脚本设置的请求间隔；资源或模型响应较慢时需要延长间隔/轮询次数。当前集合预期外部模型未配置，配置真实模型后应按成功分支修改预期断言。文件二进制与长连接由专项 Spring 测试覆盖；该集合不会无限等待 SSE。

## 目录和编码规范

`api` 管理接口、请求安全、契约和全局异常；`service` 处理 11 模块业务，其中 `auth` 负责账户认证、`task` 负责异步任务和定时处理；`persistence` 集中管理 Repository、MyBatis Mapper 和实体；`infrastructure` 管理模型、文件和初始化配置；`common` 提供公共工具与业务异常。

Java 17、UTF-8，类/方法使用英文命名，关键业务规则用中文注释。控制器不接受 userId 指定当前用户，不返回数据库用户实体或密钥；不得修改既定契约字段来绕过校验；写请求必须检查归属、版本和状态。日志使用 traceId，不打印账号密码、Cookie、CSRF 或模型密钥。Java 源码采用统一格式，业务变更附相应集成测试。

