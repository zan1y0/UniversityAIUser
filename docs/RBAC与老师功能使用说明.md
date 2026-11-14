# RBAC、Spring Security 与老师功能

更新日期：2026-11-12。

## 已实现范围

- `STUDENT`、`TEACHER` 两种角色；登录时选择当前身份，后端验证该账号是否已被授予该角色。
- 注册只创建学生账号，注册参数不接受角色；不会因前端选择老师而授予老师权限。
- Spring Security 6.3.4：`SecurityFilterChain`、`AuthenticationManager`、`DaoAuthenticationProvider`、基于 MyBatis 的 `UserDetailsService`、JWT 认证、请求授权、方法授权及 CSRF。
- 权限从数据库读取，每次请求检查当前角色归属；JWT 携带 `activeRole`，不会把账号所有角色的权限合并使用。
- 老师可创建班级，向班级或单个学生布置任务，查看作答并给分和评语。
- 学生可输入班级编号加入，也可主动授权老师。普通任务可以标记完成，作业任务提交文本后可查看批改结果。
- 不开放学生私人 AI 对话、附件、题目作答内容；原有学生接口继续按本人归属隔离。

权限过滤链及方法授权的官方说明：[Spring Security 架构](https://docs.spring.io/spring-security/reference/servlet/architecture.html)、[方法安全](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)。

## 前端位置

实际前端为 `C:/Users/jack/Desktop/UniversityAIUserPage`。已增量修改登录、角色路由、菜单和老师/学生教学页面，保留原有悬浮数字人与学生接口。仓库 `frontend/` 同时实现相同 RBAC 功能；两份前端原有接口版本不同，因此没有整目录互相覆盖。

- 老师登录后进入 `/teacher`：创建班级、查看学生学习概况、发布任务、批改作业。
- 学生登录后仍进入 `/dashboard`，新增 `/teaching`“老师与任务”入口。
- 教学功能只在真实接口模式开放；演示模式不模拟老师权限或伪造老师数据。
- 前端菜单/路由只负责交互；直接调用接口仍由后端检查权限、指导关系、任务归属和版本。

## 数据库与 MyBatis

V3 为增量迁移，不修改 V1/V2，不重建已有业务表。启动 MySQL profile 时由 Flyway 执行：

| 新表 | 用途 |
| --- | --- |
| `sys_roles` | 两种角色 |
| `sys_permissions` | 10 项权限 |
| `sys_user_roles` | 用户角色关联 |
| `sys_role_permissions` | 角色权限关联 |
| `teacher_students` | 学生授权的指导关系 |
| `teaching_tasks` | 教学任务及状态、版本 |

V10 新增 `teacher_accounts` 保存老师登录名和 BCrypt 密码哈希，并迁移原有老师凭据。学生凭据继续保存在 `app_users`。新老师账号拥有独立的用户 ID，即使登录名和某个学生相同，也按登录身份查询对应的凭据。老师在 `app_users` 中的基础身份用于会话、资料和业务外键。

新增角色与教学数据交互全部通过 `RbacMapper`、`TeachingMapper` 接口及 XML；没有新增 JDBC 业务读写。现有 `auth_tokens` 的撤销逻辑、学生 `owner_id` 和版本校验继续使用。

V10 同时新增 `teaching_classes`、`teaching_class_members` 和 `teaching_submissions`。每个班级编号全库唯一，由 6～16 位字母或数字组成，忽略大小写。班级发布按发布时的成员分配。普通任务可标记完成，作业任务走 `pending → submitted → graded`，评分为 0～100 整数。取消限待完成或待提交状态，所有状态更新校验版本。截止后仍允许提交，页面显示逾期。

## 开通第一个老师账号

不提供固定默认老师密码。系统负责人在 IDEA 后端运行配置中临时设置以下环境变量，然后按原 MySQL profile 启动：

```text
BOOTSTRAP_TEACHER_LOGIN=自定老师账号（3～32位小写字母、数字、下划线）
BOOTSTRAP_TEACHER_PASSWORD=自定密码（至少8个字符，包含字母和数字，UTF-8不超过72字节）
BOOTSTRAP_TEACHER_NAME=老师昵称（1～32个字符）
```

或在 PowerShell 中设置同名环境变量，再执行 `./scripts/start-mysql.ps1`。`.env.example` 只列变量名，Spring Boot 不会自动加载 `.env`。

`TeacherAccountInitializer → TeacherProvisioningService` 在事务中创建老师凭据、个人资料及老师角色。重复初始化已有老师账号时校验该老师的当前密码，保留原密码和资料。

首次成功后移除这三个初始化变量即可；账号及角色已保存于 MySQL。不要把真实密码写入前端、源码、SQL 文件或提交 Git。此初始化入口属于服务器管理操作，没有开放角色分配 HTTP 接口。

## 使用流程

1. 系统负责人开通老师账号。
2. 老师在登录页选择“老师”，输入该账号密码。
3. 老师在「班级管理」创建班级，把编号发给学生。学生登录后进入「老师与任务」并输入编号加入。
4. 老师选择单个学生或班级发布任务。勾选「要求提交文本作业并评分」可发布作业。
5. 学生提交作业，老师在待批改列表录入分数和评语，学生在原任务中查看结果。
6. 退出班级后，该班任务与成绩从双方列表隐藏。单独授权的指导关系可以在「我的指导老师」中管理。

## 兼容与安全边界

- 默认不提供 `role` 的旧 JWT 登录请求按 `STUDENT` 处理；旧 SESSION 登录只允许具有学生角色的账号，不能绕过老师身份校验。
- JWT/旧 SESSION 身份在每次请求转换为 Spring Security 的认证上下文；不把认证状态保存成另一份长期 SecurityContext 会话。
- CSRF 由 Spring Security 执行，复用既有 `SESSION` 内的 `csrf` 属性及 `X-CSRF-Token` 请求头。登录成功仍旋转 SESSION ID、清除旧 CSRF；前端重新获取令牌。
- 原有登录尝试限流保留。JWT Cookie 仍为 HttpOnly；浏览器不保存可读的 JWT。
- 教学接口在契约 1.3.0 中增加班级、提交和评分操作。桌面前端按原有契约增量同步教学操作。

## 验证

```powershell
# 常规回归
./scripts/test-backend.ps1

# 真实 MySQL：原有读写及认证验证 + RBAC 全流程
./scripts/test-mysql.ps1 -MavenRepository .tools/maven-cache
```

`RbacIntegrationTests` 与 `RbacMysqlIntegrationTests` 检查角色伪造、越权访问、指导授权、任务流转、CSRF、角色撤销以及老师账号初始化。测试使用 MyBatis 构建业务数据，事务结束后断言测试账号已回滚。

浏览器测试文件为 `tests/e2e/rbac.spec.ts`，覆盖桌面与手机上的老师登录/发布任务、学生授权/完成任务/撤销授权及路由隔离。浏览器测试使用明确的接口替身，数据库和后端行为另由真实 MySQL 集成测试验证。
