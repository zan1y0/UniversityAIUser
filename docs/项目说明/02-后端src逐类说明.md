# 02 后端src逐类说明

[返回文档目录](README.md)

以下Java类均位于`src/main/java/com/kaoyan/ai/`，表格中的类名对应同名`.java`文件。类的实际源码位置、公开方法及注入依赖见[源码索引](05-接口与源码索引.md)。

## 目录

1. [启动类与Spring组件](#启动类与spring组件)
2. [controller：HTTP接口入口](#controllerhttp接口入口)
3. [security：认证与请求安全](#security认证与请求安全)
4. [contract：契约与统一执行](#contract契约与统一执行)
5. [domain：业务规则](#domain业务规则)
6. [persistence：数据库访问](#persistence数据库访问)
7. [jobs：后台处理与事件](#jobs后台处理与事件)
8. [ai与storage](#ai与storage)
9. [common：公共工具与错误](#common公共工具与错误)
10. [内部类型与接口说明](#内部类型与接口说明)
11. [test：逐个测试类](#test逐个测试类)

## 启动类与Spring组件

`KaoyanAiApplication.java`是整个后端的启动入口。`main()`调用`SpringApplication.run()`创建应用上下文、启动内嵌Web服务器；`@SpringBootApplication`从当前包向下扫描组件；`@EnableScheduling`使`jobs`中的`@Scheduled`任务能够执行。

日常阅读时，理解这些注解就能看清对象如何连接：

| 写法 | 当前项目中的含义 |
| --- | --- |
| `@RestController` | 类中的HTTP映射返回JSON或指定响应，而不是服务端页面模板 |
| `@Service` | 由Spring管理的业务服务组件 |
| `@Repository` | 数据访问组件；这里主要封装JdbcTemplate |
| `@Component` | 其他由Spring管理的组件，例如过滤器、任务执行器、模型网关 |
| 构造方法中的依赖参数 | Spring把已有Bean传进来，不需要Controller每次手工new Service |
| `@Value` | 从配置读取值，例如JWT密钥、附件目录、模型地址 |
| `@Transactional` | 在Spring代理调用边界组织数据库事务；异常会影响提交／回滚 |
| `TransactionTemplate` | 代码中显式划分事务，后台任务用它把耗时模型请求和数据库提交分开 |
| `@Scheduled` | 按固定间隔检查任务、考试截止或提醒 |

## controller：HTTP接口入口

这层决定“哪个URL交给哪个方法”。多数方法很薄，只把`operationId`、路径变量、请求体和Servlet请求交给`ApiExecutor.execute()`。所以看到`op045()`只有几行，不代表功能没实现，需要继续查执行器和业务Service。

| 类 | 接口范围 | 职责及实际去向 |
| --- | --- | --- |
| `AuthController` | 新增`/api/v1/auth/register`、`login`、`me`、`logout` | `register()`／`login()`直接调用`AuthService.authenticate()`；`me()`查当前用户；`logout()`撤销JWT并销毁会话。返回与项目一致的JSON封装，退出为204。它不经81个旧操作的ApiExecutor分发。 |
| `SupportController` | op001～op010 | CSRF、旧会话登录退出、用户档案、附件、异步任务查询、SSE。普通方法走ApiExecutor；上传下载直接走FileService；事件订阅直接走EventService。 |
| `Module01Controller` | M01，op011～op018 | 数字人资源、偏好、文字／语音互动、打卡、督学状态与提醒；主要由AvatarService和DomainDispatcher处理。 |
| `Module02Controller` | M02，op019～op024 | 院校专业查询、推荐、历史指标、对比、风险评估；目录查询走CatalogService，评估任务经DomainDispatcher和jobs处理。 |
| `Module03Controller` | M03，op025～op030 | 公共课知识体系、知识点讲解、考点和英政数辅导；查询走CatalogService，生成型操作进入异步分发与模型网关。 |
| `Module04Controller` | M04，op031～op036 | 专业课学科、知识树、参考书章节、真题及考点分析；目录与题目查询走CatalogService，讲解等走异步任务。 |
| `Module05Controller` | M05，op037～op042 | 答疑会话、图片识别、发消息、消息查询和取消生成；QaService管理会话／消息状态，DomainDispatcher组织生成。 |
| `Module06Controller` | M06，op043～op049 | 创建练习、获取试题、提交作答、错题及作答历史、同类题、掌握进度；由PracticeService处理。 |
| `Module07Controller` | M07，op050～op056 | 生成候选计划、查询修改、确认、调整、任务完成与进度；PlanService负责规则，部分生成与调整经任务执行。 |
| `Module08Controller` | M08，op057～op064 | 试卷、开考、恢复、保存答案、交卷、成绩、排名、估分；试卷列表走CatalogService，其余主要走ExamService与任务流程。 |
| `Module09Controller` | M09，op065～op070 | 复试创建、回答追问、结束总结、总结查询、自我介绍批改和礼仪资料；InterviewService管理状态，生成由DomainDispatcher完成，礼仪资料走CatalogService。 |
| `Module10Controller` | M10，op071～op074 | 学习事实记录、总览曲线、薄弱点和周报；由AnalyticsService处理。 |
| `Module11Controller` | M11，op075～op081 | 资讯政策、版本差异、关注、关键日期、通知和已读状态；由NewsService处理。 |

控制器方法上的`@GetMapping`等才是完整路由定义；详细方法、URL和中文接口说明在第5篇逐条列出。控制器中相似的依赖字段不一定每个都被实际使用，判断调用关系要看方法体，不能只看构造方法。

## security：认证与请求安全

| 类 | 做什么 | 关键方法与协作 |
| --- | --- | --- |
| `SessionFilter` | 请求进入控制器之前的统一入口检查。设置traceId、no-store及nosniff；按可信来源设置CORS；处理OPTIONS；保护`/api/v1/`下接口；对写请求校验CSRF及来源。 | `doFilterInternal()`先识别公开路由，再通过`SessionService.user()`确认受保护请求身份；异常直接写入统一JSON错误，不再进入Controller。公开登录／注册也需要CSRF。 |
| `SessionService` | 管理CSRF及兼容旧会话身份，并给其他服务提供统一的当前用户ID。 | `csrf()`创建／读取SESSION中的CSRF值；`user()`优先读取并校验JWT，没带JWT时才检查旧session的userId；`login()`是旧账号密码会话登录；`logout()`使HttpSession失效。 |
| `AuthService` | 新注册／登录的业务处理：参数检查、小写规范化、登录频率控制、BCrypt、账号创建、初始档案创建、登录后身份轮换。 | `authenticate(body, register, request, response)`用布尔标记区分注册或登录，在事务中处理账号与档案／令牌；`current()`查当前用户。依赖UserAccountRepository、RecordRepository、SessionService及JwtService。 |
| `JwtService` | JWT签发、解析、校验、令牌状态查询与撤销，以及HttpOnly Cookie设置。 | `token()`从Bearer头或AUTH_TOKEN Cookie取值；`verify()`固定HS256并检查签发者、接收方、时间和数据库jti；`issue()`写auth_tokens并设置Cookie；`revoke()`撤销并清Cookie；`revokeActive()`只撤销用于轮换。 |

认证涉及的三种东西必须分清：

- `AUTH_TOKEN`：浏览器携带的JWT Cookie，用于新登录身份认证；JavaScript不能直接读取。
- `SESSION`：Servlet会话Cookie。新登录仍使用它承载CSRF状态；旧登录还会在会话里存userId。
- `X-CSRF-Token`：写请求的请求头。证明当前请求具备与会话对应的CSRF凭据，不能替代账号身份认证。

新登录成功后会轮换会话ID、清理旧userId和旧CSRF，并签发JWT。因此项目是“JWT身份＋会话CSRF＋可撤销令牌记录”的组合，不是完全无数据库查询、无服务端会话的认证方案。

`AuthService`的频率窗口按连接来源保存在进程内，换SESSION不会重置，但后端重启会清空，多实例间也不共享。旧`SessionService.login()`保留了自己的会话登录尝试计数。

## contract：契约与统一执行

| 类 | 作用 | 阅读重点 |
| --- | --- | --- |
| `ContractRegistry` | 加载`resources/contract/openapi.json`，按operationId保存定义，展开模型引用，编译JSON Schema，校验请求、查询参数、模型及响应。 | `request()`校验HTTP参数／请求体；`query()`提取并规范查询字段；`response()`检查返回；`model()`检查业务或模型结构；`idempotent()`判断是否需要操作去重。 |
| `ApiExecutor` | 81个旧操作的统一事务执行入口，串联契约、用户身份、用户锁、幂等、业务分发与响应封装。 | `execute()`先校验请求，识别当前用户，对写操作锁账号，再查幂等缓存；op001～op003处理CSRF和旧会话认证，其他操作交DomainDispatcher。成功后校验响应并保存幂等结果。 |

这里的“契约”是接口数据约定，不是数据库事务。比如输入多了一个不允许的字段，会在契约校验阶段失败；输入字段正确但访问别人的资源，则还需安全层和业务／Repository所有者检查。

`ApiExecutor`使用`@Transactional`：业务写入和幂等结果在同一事务中组织。重复的“用户＋操作＋幂等键”会返回原始响应；同一键换了请求内容会返回冲突。当前有效幂等记录的复用逻辑按24小时判断。

## domain：业务规则

这是最应花时间阅读的一层：它回答“接口收到合法请求后，系统究竟如何处理”。

| 类／类型 | 具体职责 | 关键方法、关联数据及调用者 |
| --- | --- | --- |
| `DomainContext` | 请求上下文record，携带已认证user、路径参数paths、查询参数query和请求体body。 | `path()`取路径值，`q()`取查询文本，`b()`取请求体文本，`integer()`取请求体整数；从ApiExecutor或任务命令构造，供Service统一读取。它不是数据库实体。 |
| `DomainDispatcher` | 根据op编号将请求分发给业务Service；判断哪些操作入队；准备任务资源；组织模型调用和完成结果的持久化。 | `execute()`同步分发或入队；内部`enqueue()`准备资源；`requiresModel()`划分模型阶段；`complete()`产生任务结果；`persistModelResult()`在提交阶段落库。依赖多数业务Service、JobService、FileService和ModelGateway。 |
| `ProfileService` | 用户备考档案的读取、更新，检查版本、目标院校及基础分数。 | `get(user)`按用户取profiles；`update(context)`验证后更新档案版本。新注册的初始档案由AuthService创建。 |
| `CatalogService` | 公共目录入口：院校、专业、科目、知识树、书目、题目、试卷及部分资料查询。 | `execute()`处理M02/M03/M04的查询及op057、op070等；`program()`、`subject()`、`knowledge()`、`questionVersion()`提供共享查询；`displayQuestion()`组织可展示题目；`publicTree()`组织公开知识树。数据通常以owner为public保存。 |
| `AvatarService` | 数字人素材、偏好、打卡、督学状态与提醒设置。 | `execute()`处理M01常规操作；读取／更新avatar_preferences、checkins和reminder_preferences等。互动生成op014由分发器另行组织模型任务。 |
| `PracticeService` | 创建练习会话、返回试题、保存作答与批改、管理错题和知识掌握进度。 | `execute()`处理op043～op049；共享评分相关方法供考试逻辑使用。使用practice_sessions、attempts、wrong_items等，依赖CatalogService核对题目版本。 |
| `PlanService` | 按目标日期、每天预算和基础成绩生成候选计划，处理修改、确认、任务完成和调整。 | `generate()`是可解释的规则分配：依据薄弱程度计算权重并分配时间，不等于每次都调用大模型；`execute()`处理查询／确认等；`progress()`统计完成情况；`adjustment()`生成调整结果。访问profiles、plans、study_events等。 |
| `QaService` | 答疑会话与消息的状态控制、历史查询、取消生成。 | `execute()`创建会话／查询消息／取消；`prepareMessage()`核对会话科目、上下文版本及是否有未结束消息，准备生成中的消息；答案生成交DomainDispatcher及ModelGateway。取消时同时更新相关任务，防止晚到结果覆盖取消状态。 |
| `ExamService` | 模考开考、恢复、保存答案、截止与交卷、评分结果、排名、估分。 | `session()`／`paper()`取本人会话或试卷版本；`execute()`处理考试操作；`close()`收卷；`grade()`评分；`saveResult()`保存评分结果；`estimate()`估分。依赖CatalogService、PracticeService及ModelGateway，使用exam_sessions、exam_submissions、exam_results、score_snapshots等。 |
| `InterviewService` | 管理专业、英语、压力复试会话状态与总结占位／查询。 | `execute()`处理对应复试操作；`ensureRunning()`阻止结束后继续回答；`finish()`标记结束；追问和总结生成由分发器调用模型并落interview_turns／interview_reports。 |
| `AnalyticsService` | 记录学习事实、汇总学习时长和作答情况、生成薄弱点与周报。 | `execute()`处理op071～op074；内部合并重叠学习区间，避免同时上报多段时间而重复累加。主要读取study_events、attempts、plans及目录数据。 |
| `NewsService` | 政策资讯列表、版本差异、关注配置、关键日期、通知列表与已读操作。 | `execute()`处理op075～op081；访问notices、notice_revisions、news_follows、milestones、notifications等。自动导入政策的职责属于jobs/PolicyImportService。 |
| `PartialScoringException` | 当评分只完成一部分时携带已得到的结果，供后台任务保存状态及提示。 | 继承ApiException，`partial()`提供部分评分ObjectNode；它是专用业务异常，不是评分服务。 |

关于`execute(String op, DomainContext c)`：多个Service都提供同名入口，这是各自对一组operationId的分支处理，不是实现了一个共同的自定义Java接口。当前源码里没有统一的`BusinessService`接口。

业务表名在这里只用于说明主要关联，不表示所有操作都会写入表格列出的全部表。具体字段约束看契约，具体SQL／JSON变更看方法体。

## persistence：数据库访问

| 类 | 具体作用 | 关键方法与安全约束 |
| --- | --- | --- |
| `UserAccountRepository` | app_users专用账号存取。 | `findByLoginName()`用于登录；`findById()`用于当前用户；`existsById()`用于初始化判断；`create()`创建账号。使用`?`绑定参数；密码只保存BCrypt哈希。内部Account的toString屏蔽哈希。 |
| `RecordRepository` | 各业务聚合表共用的JSON快照访问层，不是无限制SQL执行器。 | `create/get/find/list/update/replace/delete`实现基础存取；`publicGet()`取public数据；`lockOwner()`通过账号行锁协调同用户写入；`fingerprint()`生成幂等请求指纹；`jdbc()`为少数后台扫描暴露JdbcTemplate。 |
| `IdempotencyRepository` | 保存已处理请求的指纹、状态码、原始返回及时间。 | `find/save/delete`按user_id、operation_id、idem_key联合键存取，支持ApiExecutor重放原响应。 |
| `EventOutboxRepository` | 持久事件队列／事件账本，用于SSE游标和补取。 | `reserve()`取得数据库自增ID；`complete()`填充事件；`after(user,cursor)`取本人游标后的记录；`latest()`取本人最近游标。不是第三方消息队列。 |
| `LocalDataInitializer` | 应用启动时按开关初始化明确标识的本地示例账号、院校、课程、题目等。 | 实现Spring Boot的ApplicationRunner；`run()`只在app.seed-local开启时执行，已有种子账号时跳过；演示账号首次创建要求指定密码。它不是生产数据导入流程，也不会在每次请求时运行。 |

`RecordRepository`中动态拼接的是经过固定`TABLES`白名单验证的表名；用户ID、owner、版本、payload等使用参数绑定。查询本人记录会将owner纳入条件，防止只凭资源id访问别人的数据。

乐观锁示例：更新时要求`WHERE id=? AND owner_id=? AND version=?`，成功后版本递增；影响行数不符合预期则返回版本冲突，要求前端重新读取。用户行锁和版本锁用途不同：前者协调事务并发，后者识别陈旧页面提交。

部分列表采用“先查本人／公共记录，再在Java中筛选、分页”的方式。这是当前实现，应与真正的数据库分页查询区分；数据规模扩大时可以单独优化，本文不假设已经做了SQL分页或全文检索。

## jobs：后台处理与事件

| 类 | 具体职责 | 触发方式和协作 |
| --- | --- | --- |
| `JobService` | 创建异步任务及其命令快照，查询本人任务。 | `enqueue()`写jobs、job_commands并返回jobId、resultUrl等；限制同用户排队／运行任务数；`get()`读取本人任务。 |
| `JobWorker` | 轮询queued任务，在线程池执行，提交结果或错误，发布完成／失败事件。 | `poll()`每500ms触发；当前固定线程池2个工作线程。先事务中抢占状态，再计算或调用模型，最后事务中核对取消状态并落结果。使用DomainDispatcher、ContractRegistry和EventService。 |
| `JobRecovery` | 后端启动后处理上次中断的running任务，避免永久显示处理中。 | 监听ApplicationReadyEvent，`recover()`将这些任务及相应资源标记为可重试失败并发布事件；不是不加区分地自动重新调用模型。 |
| `ExamDeadlineService` | 服务端处理考试截止，即使用户关闭网页也按服务端时间收卷。 | `closeExpired()`每5秒扫描考试会话，在事务和用户锁内调用op061交卷入队。 |
| `ReminderService` | 根据生效计划和用户提醒设置，生成到期／超时任务提醒。 | `remind()`每60秒触发，检查任务状态和当日是否已提醒，写通知及事件。它生成站内通知，不等于已接入短信、邮件或手机系统推送。 |
| `PolicyImportService` | 读取明确配置的本机政策JSON，校验来源与版本，更新政策及关键日期，通知匹配用户。 | `refresh()`每60秒触发；未配置app.policy-file则不执行导入。不是自动联网爬取新闻。 |
| `EventService` | 组织事件结构、写事件账本、创建站内通知、管理SSE订阅连接并发送事件。 | `publish()`保存事件；`notify()`保存通知；`subscribe()`建立带游标连接；`deliver()`每秒取新事件并发送，约15秒发送心跳。每用户连接数有上限。 |

后台任务当前是单应用进程内的轮询和线程池，持久状态在数据库；不能据此称为已部署Redis队列、Kafka、RabbitMQ或分布式调度系统。

**当前兼容边界：** EventService的Client和deliver仍以旧HttpSession.userId及sessionId判断连接身份。新AuthService登录会移除userId、使用JWT认证。因此新JWT登录的SSE不能仅凭建立连接就认定持续推送可用，需要进一步适配；前端`waitJob()`的HTTP轮询是另一条独立查询路径。本文如实记录源码，不在文档任务中擅自改动事件逻辑。

## ai与storage

| 类 | 作用 | 关键细节 |
| --- | --- | --- |
| `ai/ModelGateway` | 将业务调用转换为外部OpenAI兼容HTTP请求，并校验输出结构。 | `ready()`检查配置；`structured()`请求结构化文本／图像结果；`withSchema()`把输出Schema加入提示；`recognize()`组织图片识别；`transcribe()`组织音频转写；`version()`返回模型标识。使用Java HttpClient，模型地址、API key、文本／视觉／音频模型和超时来自配置。 |
| `storage/FileService` | 接收私有图片／音频，校验内容和用途，保存文件与files元数据，提供鉴权下载／删除。 | `upload()`处理MultipartFile；`metadata()`按本人及purpose查元信息；`bytes()`供后端读取；`download()`返回二进制；`delete()`删除本人附件。文件实体在配置的私有目录，不是放到static让任何人访问。 |
| `storage/AudioInspector` | 校验音频实际时长，供上传限制使用。 | `duration()`检查音频；WAV有相应解析路径，其他支持格式依赖配置的ffprobe。不是语音转文字组件，转写属于ModelGateway。 |

模型没有配置或返回无效结构时，网关返回明确的依赖不可用错误，不自动生成“假AI回答”。登录成功也不代表院校数据、政策数据和模型服务都已经配置齐全。

## common：公共工具与错误

| 类 | 具体作用 |
| --- | --- |
| `Jsons` | 静态JSON及基础校验工具：`obj/arr`构造JSON，`parse`解析，`id`生成UUID，`now/today`处理时间，`text`取字段，`require`抛参数错误，`version`校验版本，`page`组织分页返回；统一ObjectMapper和Asia/Shanghai业务时区常量。 |
| `ApiException` | 自定义业务异常，携带HTTP status、业务code和message；`missing()`构造未找到，`state()`构造状态冲突。业务层抛它，由HTTP边界转换为响应。 |
| `ApiAdvice` | `@RestControllerAdvice`统一处理控制器／服务抛出的ApiException、无效JSON、超限上传及未知异常。业务异常返回相应状态；未知错误记录traceId和服务端日志，客户端接收一般化错误。 |

过滤器位于Controller之前，因此`SessionFilter`有自己的ApiException响应写入逻辑；不要以为过滤器错误一定经过ApiAdvice。

## 内部类型与接口说明

源码中有些类型写在另一个`.java`文件内部，没有独立文件，也有明确作用：

| 类型 | 所在文件 | 含义 |
| --- | --- | --- |
| `UserAccountRepository.Account` | UserAccountRepository.java | 账号数据record：id、loginName、displayName、passwordHash；只在认证／持久层使用，不应直接作为响应返回。 |
| `IdempotencyRepository.Entry` | IdempotencyRepository.java | 查询出的幂等记录：fingerprint、statusCode、responseBody、createdAt。 |
| `EventOutboxRepository.Event` | EventOutboxRepository.java | 账本事件record：id、type、payload，供游标读取和SSE投递使用。 |
| `AuthService.Window` | AuthService.java | 私有record，记录某来源限流窗口的结束时间和次数。 |
| `EventService.Client` | EventService.java | 私有内部类，保存订阅用户、SseEmitter、旧会话、sessionId、事件游标和心跳时间。 |

当前后端业务源码没有单独定义的自有`interface`文件。`LocalDataInitializer implements ApplicationRunner`实现的是Spring Boot提供的接口；`SessionFilter extends OncePerRequestFilter`继承的是Spring过滤器基类。Java的record是紧凑数据类型，不是接口。

也没有完整的一套`entity/DTO/VO`目录：大部分业务模型使用Jackson的JsonNode/ObjectNode和JSON契约描述。阅读字段时应同步打开OpenAPI Schema，而不是寻找不存在的实体类。

## test：逐个测试类

位置：`src/test/java/com/kaoyan/ai/`。这些类是测试入口，不属于正式请求链。

| 文件 | 验证重点 |
| --- | --- |
| `KaoyanAiApplicationTests.java` | 使用测试H2配置验证Spring应用上下文能正常启动。 |
| `BackendContractTests.java` | 基于MockMvc验证多模块业务流程、严格契约、CSRF、数据所有权、版本、幂等、任务取消、模考、文件等。它会调用HTTP层，但不依赖浏览器。 |
| `BackendModelFlowTests.java` | 在可控测试模型响应条件下验证模型相关业务及结果处理；不能据此当作真实第三方模型服务的效果评测。 |
| `DatabaseDeliverablesTests.java` | 验证仓库参数绑定、账号唯一性、幂等、事件游标、所有权及版本更新，并整理测试库交付数据。 |
| `MysqlPersistenceTests.java` | 只有明确开启mysql.verify并指定验证库时才运行MySQL持久化往返验证；默认测试不会自动向用户业务库写记录。 |
| `LocalDataInitializerTests.java` | 验证种子数据初始化和密码等前置条件，避免以不明确凭据创建账号。 |
| `AuthIntegrationTests.java` | 新JWT认证集成测试：注册与档案、密码哈希、重复账号、登录轮换、退出重放、CSRF、来源、注入输入、限流、签名／过期／签发者／接收方。 |

测试资源`src/test/resources/mockito-extensions/`中的`org.mockito.plugins.MockMaker`、`org.mockito.plugins.MemberAccessor`是Mockito扩展配置文件，分别选择子类Mock方式与反射成员访问方式，用于当前运行环境的测试兼容；不是应用业务接口实现。

推荐用测试理解规则：先找到目标operationId所在Controller，再找对应Service，最后看BackendContractTests或AuthIntegrationTests里的成功／失败用例。
