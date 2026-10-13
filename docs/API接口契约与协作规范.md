# 考研 AI 导学系统 API 接口契约 v1.0.0

本契约严格覆盖课程要求的 11 个模块，通用技术目录只包含认证、本人档案、附件、任务和事件。不存在注册、用户列表、用户管理或管理员业务模块。依据为《大学生考研AI导学系统_总体架构设计_MySQL版》。本文件是待实现的正式设计，现有脚手架不代表已实现这些接口。

## 1. 唯一契约与协作

`contracts/openapi.json` 与 `contracts/openapi.yaml` 内容一致，是前后端和 Apifox 的正式交换契约。版本号 1.0.0。前端独立 npm 构建，后端独立 Maven 构建；不得把前端构建接入 Maven。Nginx `/` 服务前端 dist，`/api/v1/` 原样转发后端，不删改路径前缀。开发前端代理同一 `/api/v1` 前缀到本地8080，生产统一域名 HTTPS。Apifox 本地直连测试用后端环境；浏览器生产通过 Nginx。

字段、方法、路径、返回结构、枚举、状态和错误码以 Schema 为准；不可由任一方临时更名。新增字段也须先修改契约并双方同步；破坏性修改不得覆盖 v1，必须升主版本或取得双方明确迁移约定。Apifox 编辑后先导出审核并更新仓库，禁止只改单一副本。业务未实现接口保持“设计中”，Mock 只用于开发，不能作为真实AI或数据验收。

## 2. 全局固定规则

| 项目 | 固定规则 |
|---|---|
| 根路径 | `/api/v1`；路径固定，UUID 资源 ID |
| 请求 | UTF-8 JSON，camelCase；除上传使用 multipart/form-data |
| ID | UUID 字符串；事件游标是十进制字符串，不传 Java Long JSON 数值 |
| 日期 | 本地日期 YYYY-MM-DD；时间戳 UTC ISO8601 且末尾 Z；v1时区 Asia/Shanghai |
| 缺省与空值 | 请求可选字段省略；仅 Schema 显式 nullable 的字段可传/返回 null；其余拒绝 null |
| 未知字段 | 所有请求 additionalProperties=false，拒绝拼错字段与擅自添加参数 |
| 成功响应 | `{code:"OK",message:string,data:具体类型,traceId:string}`；HTTP状态区分200/201/202 |
| 无内容 | 退出与删除附件204，无JSON响应体 |
| 下载/事件 | 文件原始流和 SSE 不使用JSON成功包装 |
| 分页 | page从1开始；pageSize默认20、1..100；data={items,page,pageSize,total}；无数据items=[]、total=0 |
| 分页排序 | 默认创建时间降序、UUID升序稳定排序；会话消息按创建时间升序；通知按eventId降序，afterEventId为严格大于游标；排序规则不由客户端任意更改 |
| 分数/概率 | JSON数值；分数0..500（单题0..150），保留最多2位小数；概率/正确率/掌握度0..1最多4位小数；未知数据null，不以0代替 |
| 认证 | HttpOnly SESSION Cookie，Secure、SameSite=Lax，30分钟无活动过期；浏览器credentials=include；禁止传userId选择本人数据 |
| CSRF | 先GET /session/csrf；所有写请求，包括登录，携带X-CSRF-Token；登录后重新取token |
| 幂等 | 标注接口必传Idempotency-Key UUID；requestId/eventId字段如存在必须等于该键；24h同键同体重放原响应，异体409；进处理中返回已有任务，不能重复触发供应商调用 |
| 版本 | version/contextVersion/progressVersion整数>=1；明确说明的模考首次答案version=0；不匹配409 VERSION_CONFLICT，不自动覆盖 |
| 权限 | 所有个人资源按session用户校验归属；不存在或非本人返回404；不返回密码、供应商密钥、私有路径 |
| 数据来源 | 内容、院校指标、政策和生成结果均保留来源与版本；所有示例为合成示例，不是官方院校事实 |

幂等响应中的资源和任务必须保持一致，响应重放优先于乐观锁检查。过了24小时不得用旧键创建新业务事实；打卡的用户日期、学习事件eventId、作答requestId、交卷sessionId具有永久业务唯一约束。批量列表可为空。分数字段根据题目和量规额外校验不得超过maxScore，baseline同理。

## 3. 错误响应

`{code:业务错误码,message:可读说明,data:null,traceId:string,details:[{field,reason}]}`。details 无字段错误时为[]。服务端不得把错误伪装成HTTP200。400为格式/未知字段，422为内容范围及跨字段规则；前端用业务code处理而非解析message。429返回Retry-After秒数。

- HTTP 400：INVALID_REQUEST。
- HTTP 401：UNAUTHENTICATED, INVALID_CREDENTIALS。
- HTTP 403：FORBIDDEN, CSRF_REJECTED。
- HTTP 404：RESOURCE_NOT_FOUND。
- HTTP 409：VERSION_CONFLICT, INVALID_STATE, IDEMPOTENCY_MISMATCH, PLAN_NOT_DRAFT, EXAM_CLOSED。
- HTTP 422：VALIDATION_FAILED, INSUFFICIENT_INPUT。
- HTTP 429：RATE_LIMITED。
- HTTP 500：INTERNAL_ERROR。
- HTTP 503：DEPENDENCY_UNAVAILABLE。
- HTTP 504：PROVIDER_TIMEOUT。

## 4. 异步与流式状态

202固定返回 jobId、status=queued、resultUrl、resourceId（暂无领域资源时null），消息接口另有messageId/contextVersion；交卷接口是独立Submission响应。resultUrl仅同源 `/api/v1/...` 相对路径。轮询 GET /jobs/{id}，status 固定 queued/running/completed/failed_retryable/failed/cancelled；completed的progress=100、result非空、error=null。其他状态result=null；failed状态error非空。kind决定JobResult的唯一具体Schema，禁止任意JSON结果。

通用异步任务resultUrl默认 `/api/v1/jobs/{jobId}`；答疑为消息资源，择校为评估资源，计划为计划资源，模考为成绩资源，复试结束为报告资源。资源必须创建成功后才返回其路径，resourceId须对应该资源；通用任务无领域查询接口时保持null。

SSE只允许 ai.delta、ai.completed、job.failed、task.reminder、exam.scored、policy.updated 六类。data内完整JSON按EventPayload定义：eventId、type、resourceId、version、occurredAt、data。SSE id等于eventId，event等于type。每15秒`: heartbeat`注释。客户端按eventId去重，增量sequence严格递增；completed之后必须重新GET最终资源。Last-Event-ID只回放持久化完成事件/通知，增量不持久回放；掉线后重新查询任务、消息和通知。事件流或通知不能作为权限绕过。

## 5. 状态与关键业务边界

- 计划：draft → active → superseded；候选任务才可手动编辑；同用户目标只允许一个active；自动调整生成新draft，不改已完成事实。GET本人profile的activePlanId用于恢复当前计划。
- 模考：in_progress → submitted；服务端控制deadlineAt；截止后拒绝写答案；自动交卷和手动交卷共用提交唯一约束。GET会话恢复接口是现有自动保存功能的必要只读补充，不是新增模块。保存成功递增答案和会话版本；前端提交前GET最新会话版本。评分未完整完成totalScore=null；完成后才能参与同卷版本排名。
- 题目：练习/模考展示Question无正确答案；批改后PracticeAttempt才提供correctAnswer与解析；主观题估分明确量规。拍照答疑只给思路，必须确认OCR，不提供最终答案字段。
- 择校：缺数据或校准不足probability=null、status=insufficient_data，必须返回原因/缺项/来源。不能返回伪造概率。
- 学习统计：事件时间区间合法、归属正确且去重；0样本率为null；不重叠累计时长。周报weekStart为周一，GET不启动生成任务。
- 政策日期：只返回核验出处；未知日期null。通知与关注归属于当前会话用户。
- 接口超时：普通查询15秒、上传60秒，生成接口在10秒内接受任务后202；AI工作任务120秒软超时，重试最多3次，不重复写业务结果。SSE长期连接由Nginx关闭代理缓冲并设置读超时>=60秒。

## 6. 接口目录

完整字段、必填条件、枚举、边界、请求及响应示例见 OpenAPI/Apifox；每个请求/响应均有独立可验证Schema，禁止只写data:Object。

| 模块 | 方法 | 路径 | 功能 |
|---|---|---|---|
| 00 通用技术接口 | GET | `/api/v1/session/csrf` | 获取 CSRF 令牌 |
| 00 通用技术接口 | POST | `/api/v1/session/login` | 登录 |
| 00 通用技术接口 | POST | `/api/v1/session/logout` | 退出登录 |
| 00 通用技术接口 | GET | `/api/v1/profile` | 获取本人备考档案 |
| 00 通用技术接口 | PUT | `/api/v1/profile` | 替换本人备考档案 |
| 00 通用技术接口 | POST | `/api/v1/files` | 上传私有图片或音频 |
| 00 通用技术接口 | GET | `/api/v1/files/{id}` | 下载本人私有附件 |
| 00 通用技术接口 | DELETE | `/api/v1/files/{id}` | 删除本人附件 |
| 00 通用技术接口 | GET | `/api/v1/jobs/{id}` | 查询本人异步任务及类型化结果 |
| 00 通用技术接口 | GET | `/api/v1/events` | 订阅本人 SSE 事件 |
| M01 AI数字人陪伴督学 | GET | `/api/v1/avatar/assets` | 获取数字人形象与动作素材 |
| M01 AI数字人陪伴督学 | GET | `/api/v1/avatar/preferences` | 获取数字人偏好 |
| M01 AI数字人陪伴督学 | PUT | `/api/v1/avatar/preferences` | 更新形象模式声音与提醒时间 |
| M01 AI数字人陪伴督学 | POST | `/api/v1/avatar/interactions` | 文字或语音互动 |
| M01 AI数字人陪伴督学 | POST | `/api/v1/avatar/checkins` | 每日打卡 |
| M01 AI数字人陪伴督学 | GET | `/api/v1/avatar/supervision` | 获取督学状态与鼓励 |
| M01 AI数字人陪伴督学 | GET | `/api/v1/avatar/reminders` | 获取督学提醒设置 |
| M01 AI数字人陪伴督学 | PUT | `/api/v1/avatar/reminders` | 更新督学提醒设置 |
| M02 智能择校与专业分析 | GET | `/api/v1/schools/programs` | 分页查询院校专业 |
| M02 智能择校与专业分析 | POST | `/api/v1/schools/recommendations` | 推荐院校专业 |
| M02 智能择校与专业分析 | GET | `/api/v1/schools/programs/{id}/facts` | 查询院校历史指标 |
| M02 智能择校与专业分析 | POST | `/api/v1/schools/comparisons` | 对比二至四个院校专业 |
| M02 智能择校与专业分析 | POST | `/api/v1/schools/evaluations` | 评估录取概率与风险 |
| M02 智能择校与专业分析 | GET | `/api/v1/schools/evaluations/{id}` | 获取本人择校评估 |
| M03 公共课智能辅导 | GET | `/api/v1/public-courses/knowledge` | 公共课知识体系 |
| M03 公共课智能辅导 | POST | `/api/v1/public-courses/explanations` | 分层讲解知识点 |
| M03 公共课智能辅导 | GET | `/api/v1/public-courses/exam-points` | 高频易错及背诵考点 |
| M03 公共课智能辅导 | POST | `/api/v1/public-courses/english/tasks` | 英语词汇长难句作文辅导 |
| M03 公共课智能辅导 | POST | `/api/v1/public-courses/politics/tasks` | 政治大纲预测背诵时政辅导 |
| M03 公共课智能辅导 | POST | `/api/v1/public-courses/math/tasks` | 数学推导题型与解题指导 |
| M04 专业课学习支持 | GET | `/api/v1/professional-courses/subjects` | 获取专业课学科配置 |
| M04 专业课学习支持 | GET | `/api/v1/professional-courses/knowledge` | 专业课知识体系 |
| M04 专业课学习支持 | POST | `/api/v1/professional-courses/book-explanations` | 参考书章节讲解 |
| M04 专业课学习支持 | GET | `/api/v1/professional-courses/past-questions` | 专业课历年真题 |
| M04 专业课学习支持 | POST | `/api/v1/professional-courses/explanations` | 专业课真题分层讲解 |
| M04 专业课学习支持 | POST | `/api/v1/professional-courses/predictions` | 专业课考点预测 |
| M05 AI答疑与解题 | POST | `/api/v1/qa/conversations` | 创建多轮答疑会话 |
| M05 AI答疑与解题 | POST | `/api/v1/qa/image-recognitions` | 识别题目图片 |
| M05 AI答疑与解题 | POST | `/api/v1/qa/conversations/{id}/messages` | 发送文字题或已确认图片题 |
| M05 AI答疑与解题 | GET | `/api/v1/qa/conversations/{id}/messages` | 分页获取会话消息 |
| M05 AI答疑与解题 | GET | `/api/v1/qa/messages/{id}` | 获取最终答疑消息 |
| M05 AI答疑与解题 | POST | `/api/v1/qa/messages/{id}/cancel` | 取消生成 |
| M06 刷题错题本专项训练 | POST | `/api/v1/practice/sessions` | 创建日练周测专项训练 |
| M06 刷题错题本专项训练 | GET | `/api/v1/practice/sessions/{id}` | 获取本人练习试题 |
| M06 刷题错题本专项训练 | POST | `/api/v1/practice/sessions/{id}/attempts` | 提交单题作答与批改 |
| M06 刷题错题本专项训练 | GET | `/api/v1/practice/wrong-items` | 分页查询本人错题 |
| M06 刷题错题本专项训练 | GET | `/api/v1/practice/wrong-items/{id}/history` | 分页查询错题作答历史 |
| M06 刷题错题本专项训练 | GET | `/api/v1/practice/similar-questions` | 获取同类专项试题 |
| M06 刷题错题本专项训练 | GET | `/api/v1/practice/knowledge-progress` | 知识点掌握进度 |
| M07 个性化学习规划 | POST | `/api/v1/plans` | 生成候选学习计划 |
| M07 个性化学习规划 | GET | `/api/v1/plans/{id}` | 查询日周月学习计划 |
| M07 个性化学习规划 | PATCH | `/api/v1/plans/{id}/tasks/{taskId}` | 调整候选计划任务 |
| M07 个性化学习规划 | POST | `/api/v1/plans/{id}/confirm` | 确认候选计划 |
| M07 个性化学习规划 | POST | `/api/v1/plans/{id}/adjustments` | 根据进度调整计划 |
| M07 个性化学习规划 | POST | `/api/v1/plans/{id}/tasks/{taskId}/complete` | 标记计划任务完成 |
| M07 个性化学习规划 | GET | `/api/v1/plans/{id}/progress` | 查询计划完成进度 |
| M08 模考与估分系统 | GET | `/api/v1/mock-exams/papers` | 分页查询模考试卷 |
| M08 模考与估分系统 | POST | `/api/v1/mock-exams/sessions` | 开始模考 |
| M08 模考与估分系统 | GET | `/api/v1/mock-exams/sessions/{id}` | 恢复本人模考会话与已存答案 |
| M08 模考与估分系统 | PUT | `/api/v1/mock-exams/sessions/{id}/answers/{qid}` | 自动保存单题答案 |
| M08 模考与估分系统 | POST | `/api/v1/mock-exams/sessions/{id}/submit` | 交卷并异步评分 |
| M08 模考与估分系统 | GET | `/api/v1/mock-exams/sessions/{id}/result` | 查询模考成绩与评分状态 |
| M08 模考与估分系统 | GET | `/api/v1/mock-exams/papers/{id}/ranking` | 查询同试卷版本匿名排名 |
| M08 模考与估分系统 | POST | `/api/v1/mock-exams/estimates` | 考前估分与冲刺策略 |
| M09 复试AI训练 | POST | `/api/v1/interviews` | 创建专业英语压力复试 |
| M09 复试AI训练 | POST | `/api/v1/interviews/{id}/turns` | 提交复试回答并生成追问 |
| M09 复试AI训练 | POST | `/api/v1/interviews/{id}/finish` | 结束复试并生成总结 |
| M09 复试AI训练 | GET | `/api/v1/interviews/{id}/result` | 查询复试总结 |
| M09 复试AI训练 | POST | `/api/v1/interviews/self-introduction-reviews` | 自我介绍批改 |
| M09 复试AI训练 | GET | `/api/v1/interviews/guidance` | 查询复试礼仪与流程 |
| M10 学习数据仪表盘 | POST | `/api/v1/analytics/study-events` | 记录学习事实 |
| M10 学习数据仪表盘 | GET | `/api/v1/analytics/dashboard` | 学习数据总览与曲线 |
| M10 学习数据仪表盘 | GET | `/api/v1/analytics/weakness-radar` | 薄弱知识点雷达 |
| M10 学习数据仪表盘 | GET | `/api/v1/analytics/weekly-reports` | 本人学习周报 |
| M11 资讯与政策提醒 | GET | `/api/v1/news/notices` | 分页查询政策和院校通知 |
| M11 资讯与政策提醒 | GET | `/api/v1/news/notices/{id}/changes` | 查看政策版本差异 |
| M11 资讯与政策提醒 | GET | `/api/v1/news/follows` | 获取本人关注范围 |
| M11 资讯与政策提醒 | PUT | `/api/v1/news/follows` | 更新院校政策关注范围 |
| M11 资讯与政策提醒 | GET | `/api/v1/news/milestones` | 获取报名确认准考证初试日期 |
| M11 资讯与政策提醒 | GET | `/api/v1/news/notifications` | 分页获取本人通知与补取游标 |
| M11 资讯与政策提醒 | POST | `/api/v1/news/notifications/{id}/read` | 标记通知已读 |

## 7. 现有脚手架迁移对照

| 现有接口/约定 | 正式契约 | 实现要求 |
|---|---|---|
| POST /api/user/login {phone,password} | POST /api/v1/session/login {loginName,password} | 修改登录DTO；SESSION/CSRF；不新增注册 |
| GET /api/user/{id}，直接返回User | GET /api/v1/profile | 当前用户从Session获取；安全DTO不返回password |
| POST /api/ai-chat/chat | POST /api/v1/qa/conversations + messages | 多轮上下文、异步任务、启发式结构响应 |
| GET /api/dashboard/overview?userId | GET /api/v1/analytics/dashboard | from/to/timezone；去掉userId；真实聚合 |
| GET /api/exam/question?userId | practice/sessions 或 mock-exams/sessions | 明确练习与模考，不再混用接口 |
| GET /api/news/list?category | GET /api/v1/news/notices | 按programId/region/year及分页查询 |
| GET /api/school/recommend | POST /api/v1/schools/recommendations | 完整输入与来源，不在查询参数拼生成业务 |
| GET /api/study-plan/generate?userId | POST /api/v1/plans | 生成改POST；档案版本与候选确认 |
| code=200数字、Result无traceId | code="OK"字符串、统一包装与错误结构 | 前后端严格同步，删除宽松兼容后验收 |
| 数据库Long/前端demo字符串ID | 公共API UUID字符串 | 后端可保留内部Long，但加稳定公开UUID及索引/映射；不把Long直接当UUID |
| 前端XSRF-TOKEN/X-XSRF-TOKEN | GET csrf响应+X-CSRF-Token | 更新统一client；禁止读取HttpOnly凭据 |

接口文档冻结不等于后端或前端已经完成对齐。当前后端部分接口为null脚手架，前端部分数据为demo；正式联调验收前必须实现上述迁移以及11模块真实接口。不要让Mock数据混进真实模式。

## 8. 联调验收及文档校验

自动验证 OpenAPI3.1有效性、全部Schema引用、所有参数/请求/响应示例、11模块覆盖、operationId唯一性；负例检查非法UUID、未知字段、无效枚举、错误答案分支、越界概率。接口实现验收另需验证会话隔离、CSRF、幂等重放、并发version冲突、截止交卷、答案不可提前泄露、AI失败与取消；文档验证不能替代实现测试。

Apifox中按tag建立11业务目录和1技术目录，接口保持设计中；先设置后端环境http://127.0.0.1:8080或Nginx环境http://localhost，再按csrf→login→profile顺序调试。同团队文档以本契约版本为准；没有用户管理接口。
