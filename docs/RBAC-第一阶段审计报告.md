# UniversityAIUser RBAC 第一阶段审计报告

审计日期：2026-11-12

## 1. 结论与范围

项目已具备学生和老师的核心 RBAC。用户—角色和角色—权限保存在数据库，普通受保护请求读取当前角色对应的权限，教学服务检查操作权限及任务归属。权限的实际分配已经部分动态化，登录身份解析、部分 HTTP 授权和前端路由仍绑定学生、老师两种角色。现有系统缺少 `ADMIN`、账号停用、角色权限管理、会话批量失效和授权审计。

目标角色固定为 `STUDENT`、`TEACHER`、`ADMIN`，不设置 `SUPER_ADMIN`。目标中的“动态”指账号获授哪些角色、每个角色拥有何种业务权限，由数据库分配并在运行时生效。业务代码声明执行某项操作需要的权限键，服务和 SQL 继续限定可访问的数据范围。

本次对当前工作区（含已有未提交内容）做静态审计，遍历 15 个 Controller、认证及业务服务、MyBatis Mapper、H2/MySQL 迁移、仓库 Vue 前端和文档指向的桌面前端源码。用脚本比对了实际 Controller 方法与 OpenAPI。测试结果取自已有报告，未连接部署数据库、未重跑测试或进行在线漏洞验证。只新增本报告。

| 状态 | 审计摘要 |
| --- | --- |
| 已实现 | 两角色认证与数据库授权、教学接口的操作权限、个人数据归属、教学任务范围、前端登录恢复和身份分流。 |
| 部分实现 | 接口权限粒度、账号与会话生命周期、动态变更后的前端刷新、两份前端契约一致性和回归覆盖。 |
| 尚未实现 | 管理员登录与后台、应用内角色权限管理、账号停用和授权变更审计。 |
| 存在安全风险 | SSE 建连后缺少订阅权限复查，旧 Session 登录限流可随会话重置，初始化配置残留时可再次授予老师角色。详见第 5 节。 |

## 2. 当前架构与数据库

### 后端和登录链路

| 层 | 当前实现 | 状态 |
| --- | --- | --- |
| 应用架构 | Spring Boot、Spring Security、MyBatis、Flyway。普通业务由 Controller 经 `ApiExecutor` 校验契约并分派给领域服务，教学和部分督学接口直接调用服务。 | 已实现 |
| 账号身份 | `UserAccount` 含 ID、登录名、显示名、密码哈希。统一的业务外键指向 `app_users.id`。老师凭据在 `teacher_accounts`，新老师在 `app_users` 有独立 ID 和占位登录记录。同名学生和老师可以分别存在。账号实体和映射无启用状态。 | 部分实现 |
| JWT 登录 | `/auth/login` 通过 `AuthenticationManager` 和 BCrypt 验证凭据。登录角色决定查询学生或老师凭据，随后校验数据库角色归属。公开注册分配学生角色，旧请求省略角色时使用学生身份。JWT 携带用户 ID、`activeRole` 和 `jti`，请求时验签、校验过期与数据库令牌记录。 | 已实现两种身份 |
| 旧 Session | `/session/login` 仍可单独使用，仅允许学生。成功时旋转 Session ID、清除旧 CSRF。请求过滤器优先处理 JWT，其次读取旧 Session。两条入口的认证与限流逻辑独立。 | 部分实现 |
| 授权上下文 | `SessionFilter` 每次受保护请求检查用户拥有当前角色，再把 `sys_role_permissions` 中的权限写入 Spring Security 上下文。旧 JWT 无 `activeRole` 时兼容学生身份。权限集合来自当前角色。 | 已实现 |
| 会话撤销 | JWT 退出删除当前 `jti`，旧 Session 退出使当前 Session 失效。撤销某个角色后，普通请求的角色复查会拒绝使用该角色的旧凭据。缺少账户级批量撤销和统一的权限版本。 | 部分实现 |

代码：[pom.xml](../pom.xml) · [UserAccount.java](../src/main/java/com/kaoyan/ai/persistence/entity/UserAccount.java) · [UserAccountMapper.xml](../src/main/resources/mapper/UserAccountMapper.xml) · [AuthService.java](../src/main/java/com/kaoyan/ai/service/auth/AuthService.java#L48) · [JwtService.java](../src/main/java/com/kaoyan/ai/service/auth/JwtService.java#L67) · [SessionService.java](../src/main/java/com/kaoyan/ai/service/auth/SessionService.java#L51) · [SessionFilter.java](../src/main/java/com/kaoyan/ai/api/security/SessionFilter.java#L18) · [ApiExecutor.java](../src/main/java/com/kaoyan/ai/api/contract/ApiExecutor.java)

### 数据库与 Mapper

| 数据 | 当前约束和行为 | 状态 |
| --- | --- | --- |
| `app_users`、`teacher_accounts` | V1 建用户表，用户名唯一。V10 建老师凭据表，以用户 ID 为主键和外键，老师登录名在老师表内唯一。缺少统一账号状态及账号级授权版本。 | 部分实现 |
| `sys_roles`、`sys_permissions`、`sys_user_roles`、`sys_role_permissions` | V3 建四表，代码主键、两张关联表联合主键及外键。V3 回填原有用户为学生，种下 10 项权限，V10 新增 4 项。现有角色只有学生和老师。 | 已实现基础模型 |
| 角色与权限的写入 | `RbacMapper` 支持读用户角色、读角色权限、授予和撤销用户角色。学生注册和老师开通会写角色。角色—权限写入来自迁移，尚无应用管理接口或事务化变更审计。 | 部分实现 |
| `auth_tokens` | V2 建立，保存令牌 ID、用户 ID、过期时间。Mapper 支持校验有效性、按令牌撤销和清理过期记录。 | 已实现单令牌管理 |
| 教学数据 | V3 建指导关系和任务，V10 增加班级、成员、作业提交，V11 增加批次发布和附件。教学关系用外键、归属列及服务约束表达。 | 已实现基础数据范围 |

`local` 使用 H2 迁移，`dev`、`mysql`、`prod` 使用 MySQL 迁移。两套 RBAC 迁移均含 V3、V10、V11。`database/model/03_rbac.mysql.sql` 是建模交付物，应用启动以对应 profile 的 Flyway 迁移为准。实际部署的 Flyway 版本、角色权限数据及外部数据库修改需要在部署环境单独核对。

依据：[V1](../src/main/resources/db/migration/mysql/V1__initial_schema.sql) · [V2](../src/main/resources/db/migration/mysql/V2__jwt_sessions.sql) · [V3](../src/main/resources/db/migration/mysql/V3__rbac_teaching.sql) · [V10](../src/main/resources/db/migration/mysql/V10__teacher_accounts_classes_and_homework.sql) · [V11](../src/main/resources/db/migration/mysql/V11__teaching_publications_and_attachments.sql) · [RbacMapper.xml](../src/main/resources/mapper/RbacMapper.xml#L5) · [AuthTokenMapper.xml](../src/main/resources/mapper/AuthTokenMapper.xml) · [MySQL 配置](../src/main/resources/application-mysql.yml) · [H2 配置](../src/main/resources/application-local.yml)

## 3. Controller 与数据访问审计

后端 OpenAPI 1.4.0 包含 102 个路径、113 个 HTTP 操作。按方法和路径与 15 个 Controller 逐项比对，113 项一致。后端内置、后端静态副本、根目录和仓库前端的 OpenAPI 操作集合一致。以下数量按当前过滤链第一条匹配规则计算。

| 数量 | 入口 | 当前授权 |
| ---: | --- | --- |
| 4 | 获取 CSRF、旧 Session 登录、JWT 登录、注册 | 无需先登录。写请求仍由 CSRF 配置校验。 |
| 5 | `/auth/me`、两种退出接口、个人资料读写 | 已登录。身份和资料取当前用户。 |
| 12 | `/teacher/**`，含学生概况、班级、发布、取消、批改、作业附件 | `hasRole('TEACHER')` 加教学服务具体权限和资源范围校验。 |
| 11 | `/teaching/**`，含指导关系、加入或退出班级、任务提交、作业附件 | `hasRole('STUDENT')` 加教学服务具体权限和资源范围校验。 |
| 3 | 统计 GET 接口 | `student:analytics:read`，查询范围为当前用户。 |
| 78 | 其他学生入口，含 AI、计划、练习、模考、督学、文件、事件流，以及统计事件写入 | 共用 `student:study:use`。各业务模块按自身的数据规则查询与写入。 |

数据范围核查结果：

- **已实现。** 个人计划、AI 对话和消息、文件和任务等以认证身份为数据所有者。通用 Mapper 在详情、列表、更新和删除中约束 `owner_id`，更新同时检查版本。动态表名来自服务端白名单。文件下载和删除先读取带归属的文件记录。
- **已实现。** 老师查看学习概况要经过指导关系检查，可由学生主动授权或有效的同老师班级成员关系建立。返回的是统计、计划任务计数和已评分模考分数等摘要。班级名单与发布检查班级归属，批改、取消、提交和下载检查任务主体及当前关系。
- **已实现。** 教学列表区分独立任务和班级任务。列表 SQL 只对无班级归属的任务接受直接授权分支。班级任务要求属于任务所在班级，列表在组装提交正文、评分和附件元数据前还会核对双方角色和关系。下载和写入也检查任务所属班级成员身份。建议补测学生退班但仍单独授权同一老师的组合场景。
- **部分实现。** 排名是刻意跨用户的查询，后台提醒、考试截止、任务恢复和任务执行也有跨用户扫描。后续权限清单应记录排行榜对外字段和后台任务调用边界，并用响应断言覆盖。

代码：[SecurityConfiguration.java](../src/main/java/com/kaoyan/ai/api/security/SecurityConfiguration.java#L41) · [TeachingService.java](../src/main/java/com/kaoyan/ai/service/teaching/TeachingService.java#L132) · [TeachingMapper.xml](../src/main/resources/mapper/TeachingMapper.xml#L55) · [AggregateMapper.xml](../src/main/resources/mapper/AggregateMapper.xml#L15) · [AggregateTable.java](../src/main/java/com/kaoyan/ai/persistence/entity/AggregateTable.java#L44) · [PlanService.java](../src/main/java/com/kaoyan/ai/service/PlanService.java#L28) · [QaService.java](../src/main/java/com/kaoyan/ai/service/QaService.java#L38) · [FileService.java](../src/main/java/com/kaoyan/ai/infrastructure/storage/FileService.java#L54) · [ExamService.java](../src/main/java/com/kaoyan/ai/service/ExamService.java#L119)

## 4. 前端审计

| 检查项 | 发现 | 状态 |
| --- | --- | --- |
| 登录与身份恢复 | 登录提交选定身份，`backend.ts` 解析 `activeRole`、`roles`、`permissions`。真实模式下首次进入受保护页面时通过 `/auth/me` 恢复。两份前端的登录类型和响应解析均只允许学生、老师。 | 已实现两种身份 |
| Pinia | `stores/app.ts` 在内存中保存用户和 `sessionChecked`。模式切换会清空用户和 CSRF。身份恢复后主要使用既有权限快照，权限变更时缺少统一刷新。 | 部分实现 |
| Vue Router | `beforeEach` 负责登录恢复及访问控制，`allowedRolePath` 按角色和路径分流。`/teacher` 和 `/teaching` 共用教学视图，学生有仪表盘与学习模块。没有管理员入口。 | 部分实现 |
| 菜单与按钮 | 顶层菜单主要按身份过滤。建班、发布、评分、提交等部分教学操作读取权限码。无统一的路由、菜单、操作权限元数据。 | 部分实现 |
| 401 与 403 | 请求层返回含 HTTP 状态的错误，401 可触发登录失效处理。403 会清理 CSRF 缓存，缺少统一的无权限页面与权限快照刷新。 | 部分实现 |
| 对前端判断的依赖 | 已核查的老师和学生教学 HTTP 请求仍要经过后端路由、方法和数据范围校验。浏览器显示控制提供操作提示，服务器负责授权。 | 已实现服务端约束 |
| 桌面前端契约 | 桌面版 OpenAPI 标记同为 1.4.0，但只有 108 项。比仓库少 4 项学习模式与督学操作，以及 `POST /api/v1/avatar/attachments`。管理员接口落地时需要明确发布和生成契约的来源。 | 部分实现 |

依据：[app.ts](../frontend/src/stores/app.ts) · [router/index.ts](../frontend/src/router/index.ts#L133) · [domain/auth.ts](../frontend/src/domain/auth.ts) · [backend.ts](../frontend/src/api/backend.ts#L130) · [App.vue](../frontend/src/App.vue#L17) · [TeachingView.vue](../frontend/src/views/TeachingView.vue#L32) · [TeachingTaskCard.vue](../frontend/src/components/TeachingTaskCard.vue#L127) · [client.ts](../frontend/src/api/client.ts#L101)

## 5. 安全风险与验证方法

以下为源码可追踪的行为或风险条件。实际可达性与影响范围需要在测试环境验证。

| 编号 | 优先级 | 发现与影响 | 建议验证 |
| --- | --- | --- | --- |
| R1 | 高 | **SSE 权限撤销缺口。** `GET /events` 建连时受 `student:study:use` 保护。`deliver()` 推送时验证令牌或 Session 并复查角色归属，却未复查订阅权限。角色仍有效时，移除订阅权限可能仅阻止新连接，旧连接仍收到该用户事件。 | 建立连接并收到首条事件，撤销订阅权限并保留角色，发布下一条事件。断言旧连接关闭或停止交付，新连接被拒绝。JWT、Session 分别测。 |
| R2 | 高 | **授权规则过宽，新增接口容易继承不合适的权限。** 78 项共用学习权限，`/auth/**` 采用已登录通配，API 前缀之外默认放行，基础配置开启文档。API 匹配、认证过滤与 CSRF 路径还分别配置，后两者直接使用 `getRequestURI()`。当前 113 项的分类已盘点。将来新增入口、启用非根 context path 或使用 GET/HEAD 方法变体时，需核验规则一致性。 | 保护未显式登记的新路径和新方法，核对 HEAD、非根 context path、CORS 预检、Swagger 和静态契约的生产策略。 |
| R3 | 中 | **旧 Session 登录可重置失败计数。** 旧入口的次数计数存放在当前 Session。客户端可获取新 Session 和 CSRF 令牌，再重新尝试。JWT 入口使用按来源地址的独立计数，两条入口的限流未统一。旧入口限于学生。 | 对同一账号跨多份 Session 连续尝试，验证统一限流可覆盖旧、新登录入口。 |
| R4 | 中 | **账号角色撤销存在再授予路径。** 老师初始化器的环境变量仍存在且凭据匹配时，启动会再次调用开通服务，补授缺失的 `TEACHER`。现有账号也缺少停用状态，JWT 只有单令牌删除。 | 保留初始化配置，撤销老师角色后重启，核查角色是否被补回。后续增加禁用、重启和重新启用后旧凭据仍失效的测试。 |
| R5 | 中 | **权限分配与使用仍有硬编码角色约束。** `AuthService`、`PermissionService` 和 `UserDetailsService` 共同限制可选身份，教师与学生路径依赖 `hasRole`。即使数据库中配置了角色或权限，也可能被身份白名单、凭据查询分支或路由挡住。前端权限快照也可能过期。 | 在保持角色不变的情况下，撤销、恢复一个业务权限并用同一会话发请求。检查服务端决定随数据库变化，前端刷新后同步。 |

代码：[EventService.java](../src/main/java/com/kaoyan/ai/service/task/EventService.java#L147) · [TeachingMapper.xml](../src/main/resources/mapper/TeachingMapper.xml#L118) · [TeachingService.java](../src/main/java/com/kaoyan/ai/service/teaching/TeachingService.java#L332) · [SecurityConfiguration.java](../src/main/java/com/kaoyan/ai/api/security/SecurityConfiguration.java#L41) · [SessionService.java](../src/main/java/com/kaoyan/ai/service/auth/SessionService.java#L51) · [TeacherProvisioningService.java](../src/main/java/com/kaoyan/ai/service/auth/TeacherProvisioningService.java#L23) · [PermissionService.java](../src/main/java/com/kaoyan/ai/service/auth/PermissionService.java#L10)

## 6. 下一阶段建议

### 优先处理资源范围和权限撤销

1. 将教学任务列表和具体操作的范围规则写进验收矩阵。带班级的任务检查该班成员关系，独立任务检查对应指导关系。把退班与直接授权同时存在的场景纳入集成测试。
2. 提取统一的账号与权限解析，供 JWT、旧 Session、普通请求和 SSE 使用。为账号增加启用状态，管理操作提交后使旧凭据失效。旧 Session 可使用账号授权版本做复查，JWT 可使用现有令牌表支持按账号撤销。明确权限映射变更在下一次请求或事件交付前生效的验收规则。
3. 盘点 113 个操作所需的权限与数据范围。先明确公开入口和共享认证入口，再按业务能力配置权限键。路由使用细粒度权限和默认拒绝，服务层保留高风险操作与资源归属检查。部署配置显式决定文档暴露范围。

### 落地三角色和动态管理

1. 在 H2 和 MySQL 添加增量迁移，复用现有四张授权表及业务用户 ID。加入 `ADMIN`、管理员业务权限、账号状态及审计记录。首次管理员由受控初始化流程开通。迁移要保留老师独立 ID、两套凭据可兼容的同名行为和已有班级、作业关系。
2. 统一登录身份的解析与授权流程。凭据查找处理现有账号存储结构，角色激活依据数据库中的实际授予记录。公开注册继续固定分配学生角色。建议保留现有单会话激活一个角色的语义。
3. 提供用户角色分配与撤销、角色权限编辑、用户启用和停用、审计查询。管理接口逐项校验管理权限。对授权范围做服务端约束，避免受限管理员借编辑权限或角色给自己提权。用事务及并发控制保留至少一个有效且具有必要管理权限的 `ADMIN`。变更与审计记录在同一事务提交，记录操作者、目标、变更前后值、原因、时间和请求标识。
4. 管理员默认获得账号与授权管理所需的能力。老师查看学生概况的规则继续由指导关系与班级范围决定，学生私人 AI 对话、文件和练习数据继续按其归属管理。

### 完成前端与验收

1. 两份前端同步接入管理员登录和管理页。路由、菜单、按钮使用同一套权限元数据。Pinia 统一维护身份与权限快照，在 403 时按业务错误码决定提示或刷新。同步更新 OpenAPI 和生成的客户端。
2. 在现有 H2、MySQL 集成测试上覆盖三角色登录、直接调用接口、同角色横向越权、动态授权与撤销、旧会话和 SSE、退班后历史作业、并发撤销最后一个管理员、审计事务一致性。用实际发布的前端连接真实后端完成关键流程验收。

参考：[Spring Security 请求授权](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) · [Spring Security 方法授权](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)

## 7. 现有测试证据

- H2 与 MySQL 的已有 RBAC 报告各记录 7 项通过。测试源码覆盖角色伪造、老师账号初始化、角色撤销后旧凭据被拒绝、CSRF、任务与班级流程、跨账号访问和附件隔离。
- 仓库前端已有角色路由与登录请求单测，以及带接口替身的浏览器用例。前端用例可验证导航和操作界面，真实后端的权限判定由后端集成测试验证。
- 尚缺 SSE 已建连后的权限撤销、退班与直接授权同时存在、撤销具体角色权限的回归用例，以及管理员、账户停用、管理授权和审计测试。

证据：[后端 RBAC 测试源码](../src/test/java/com/kaoyan/ai/RbacIntegrationTests.java) · [MySQL 测试入口](../src/test/java/com/kaoyan/ai/RbacMysqlIntegrationTests.java) · [前端单测](../frontend/tests/unit/rbac.test.ts) · [前端浏览器测试](../frontend/tests/e2e/rbac.spec.ts)
