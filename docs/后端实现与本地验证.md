# 大学生考研 AI 导学系统 · 后端

后端以原课程文档的 11 个模块和 `contracts/openapi.json` 为范围，独立构建及本地运行。前端独立项目位于 `C:/Users/jack/Desktop/UniversityAIUserPage`。当前不连接 MySQL、不配置外部模型、不部署 Nginx。

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

本地账号 `student`、`student2`；首次默认密码 `LocalStudy2026!`。这些账号仅由 local 数据初始化器提供，密码在数据库中为 BCrypt 哈希。首次启动可用 `LOCAL_STUDENT_PASSWORD` 改为自己的本地验证密码。设置该变量不会改写已有账号。

## 前端协作

前端启动 Vite 开发服务器后切换“真实接口模式”。开发代理将 `/api/v1` 原路径转发到本后端 8080 端口。前端无需复制到后端，不加入 Maven 构建，也没有新增部署要求。

所有路径、请求方法和字段以 OpenAPI 为准。登录使用 HttpOnly `SESSION` Cookie；登录前获取 CSRF，登录后再获取一次，写请求携带 `X-CSRF-Token`。幂等写请求还须携带 UUID `Idempotency-Key`，有 `requestId` 或 `eventId` 时二者必须一致。公开 ID 为 UUID，业务错误使用正确的 HTTP 状态；204 不返回响应体。

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

## MySQL 脚本（尚未执行）

`database/create-database.mysql.sql` 创建独立 `kaoyan_ai` 数据库。`src/main/resources/db/migration/mysql/V1__initial_schema.sql` 含 37 张业务/技术表。当前本地测试验证 H2 版本，不代表已连接或已验证你的 MySQL。

以后启用 MySQL 时设置 `SPRING_PROFILES_ACTIVE=mysql`、`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USER`、`MYSQL_PASSWORD`。业务账号应具备本数据库内迁移所需权限，不使用源码中的 root/root。mysql 配置不会注入本地种子账号或样例资料；正式账号及课程资料须独立初始化。

持久化按聚合使用独立表，公共目录快照与用户资源通过 owner 隔离；聚合的子项保存在受契约约束的 JSON 文本中。版本列执行乐观锁，写业务通过事务和用户行锁串行化；幂等响应与业务修改在同一事务保存。不是传统每个 DTO 属性一列的 MyBatis 实体结构；现实现使用 Spring JDBC，清理了未使用的 Redis、OSS 和 MyBatis 依赖。

## 验证

```powershell
./scripts/test-backend.ps1
./scripts/test-apifox.ps1
```

Spring 测试通过 MockMvc 调用实际控制器、服务和独立 H2 数据库。模型成功分支使用仅存在于 test 目录的明确测试替身；生产源码没有 Mock 模型回退。它们不能代替真实模型账号联调或 MySQL 验证。

`apifox/backend-local.apifox-cli.json` 含 146 个串行请求步骤和断言，模拟前端的 Cookie、CSRF、幂等键、ID 与版本传递。先启动后端，再运行脚本；首次会将 Apifox CLI 安装到项目 `.tools/apifox`。`apifox/backend-local.postman_collection.json` 可导入你已安装的 Apifox 客户端。使用当前日期生成的集合；跨日期验证前更新日期参数。

Apifox 的 4 次任务轮询依赖脚本设置的请求间隔；资源或模型响应较慢时需要延长间隔/轮询次数。当前集合预期外部模型未配置，配置真实模型后应按成功分支修改预期断言。文件二进制与长连接由专项 Spring 测试覆盖；该集合不会无限等待 SSE。

## 目录和编码规范

`controller` 声明显式接口；`contract` 做严格校验和事务/幂等入口；`domain` 处理 11 模块业务；`persistence` 负责持久化；`jobs` 负责异步任务、通知和计时；`security` 负责会话与 CSRF；`storage` 负责私有文件；`ai` 负责模型适配；`common` 负责错误及 DTO 构造。

Java 17、UTF-8，类/方法使用英文命名，关键业务规则用中文注释。控制器不接受 userId 指定当前用户，不返回数据库用户实体或密钥；不得修改既定契约字段来绕过校验；写请求必须检查归属、版本和状态。日志使用 traceId，不打印账号密码、Cookie、CSRF 或模型密钥。Java 源码采用统一格式，业务变更附相应集成测试。
