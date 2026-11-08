# MyBatis 分层与 MySQL 使用说明

更新日期：2026-11-08。当前目录已进一步按功能归组，阅读顺序见 [包目录与调用顺序](包目录与调用顺序.md)。

## 当前架构

后端使用 Java 17、Spring Boot 3.3.5、MyBatis Spring Boot Starter 3.0.4。3.0 系列与项目的 Spring Boot 3 / Java 17 兼容；参考 [MyBatis 官方兼容说明](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。

```text
src/main/java/com/kaoyan/ai/
├─ api/                       controller、contract、security、advice
├─ service/                   11 模块业务，auth 认证服务，task 后台任务
├─ persistence/
│  ├─ repository/             聚合持久化适配
│  ├─ mapper/                 MyBatis Mapper 接口
│  └─ entity/                 数据库映射类型和表名白名单
├─ infrastructure/            ai 模型、storage 文件、config 初始化
├─ common/                    JSON 工具和业务异常
└─ KaoyanAiApplication.java

src/main/resources/
├─ mapper/                    5 个 Mapper XML，集中维护 SQL
├─ db/migration/              原有 MySQL、H2 的 Flyway V1/V2 迁移
└─ application*.yml
```

普通业务调用：`api.controller → api.contract.ApiExecutor → service → persistence.repository → persistence.mapper → MySQL`。
认证接口：`api.controller.AuthController → service.auth → persistence.repository/mapper → MySQL`。

原 `domain` 业务包现为 `service`；数据访问集中到 `persistence.repository/mapper/entity`；`LocalDataInitializer` 位于 `infrastructure.config`；账户类型为 `persistence.entity.UserAccount`。Repository 保留聚合和 JSON 的适配职责，实际 SQL 均由 Mapper XML 执行。MyBatis 不要求为每一个 Service 额外建立只有转发功能的接口。

| Mapper | 管理数据 |
| --- | --- |
| `UserAccountMapper` | 用户查询、创建和事务行锁 |
| `AggregateMapper` | 原有 34 张聚合表的增删改查、版本检查、调度扫描 |
| `IdempotencyMapper` | 用户、操作和幂等键对应的原始响应 |
| `EventOutboxMapper` | 自增事件 ID、事件负载与用户游标 |
| `AuthTokenMapper` | JWT 的有效记录、过期清理与撤销 |

## 数据兼容与事务

- 保持已有表名、字段、JSON 格式、用户 ID 和密码哈希，不需要导出、清库或重建数据库。
- MyBatis 与 Flyway 使用同一 Spring DataSource；`@Transactional` 和 `TransactionTemplate` 继续控制事务。业务写入、幂等响应、事件和用户行锁仍使用同一连接事务。
- 普通查询/更新绑定 `owner_id`；更新通过 `WHERE version = expected` 保持乐观锁语义。
- 参数值使用 `#{}`。动态表名只接受构造时经过固定白名单验证的 `AggregateTable`，不能传入任意 SQL 标识符。
- MyBatis 一级缓存设为 `STATEMENT`，关闭二级缓存，避免事务内更新后读取旧状态。
- 原有聚合表使用 JSON 保存子项。本次引入 Mapper 和实体映射，没有将历史 JSON 拆成新关系表。
- 生产 Java 代码不再使用 `JdbcTemplate`。测试中的 JDBC 仅用于独立比对、构造测试状态和验证回滚。

## 连接并启动 MySQL

在项目根目录执行：

```powershell
./scripts/start-mysql.ps1
```

脚本使用 Java 17，构建 JAR 后以 `mysql` profile 启动。也可在 IDEA 运行主类 `com.kaoyan.ai.KaoyanAiApplication`，设置参数 `--spring.profiles.active=mysql`，工作目录设为仓库根目录。

`application-mysql.yml` 继续读取被 Git 忽略的 `.local-data/mysql-local.properties` 和 `.local-data/auth-local.properties`。连接变量为 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USER`、`MYSQL_PASSWORD`；JWT 使用 `APP_JWT_SECRET`。本次不修改既有凭据，不把密钥写入源码。

直接运行未指定 profile 的主类仍使用 `local`（H2）；MyBatis 在两种数据库下均生效。MySQL 模式不导入 H2 的样例账号与题库。

## 验证方法

```powershell
# 常规回归，使用独立 H2 测试数据库
./scripts/test-backend.ps1

# 显式验证本机 MySQL 及完整应用认证链路
./scripts/test-mysql.ps1

# 如果使用项目中的 Maven 依赖缓存
./scripts/test-mysql.ps1 -MavenRepository .tools/maven-cache
```

MySQL 专项测试默认跳过，只有指定 `mysql.integration=true` 才运行：

- `MybatisMysqlIntegrationTests`：只加载持久化层，不执行数据库迁移、样例初始化或后台任务；以独立 JDBC 结果的 SHA-256 校验 Mapper 对所有既有聚合表的读取；在回滚事务中验证 CRUD、中文和 emoji、用户隔离、乐观锁、行锁、自增事件主键、幂等响应及 JWT 记录。
- `MybatisMysqlApplicationTests`：加载完整应用与 MySQL profile，执行正常的 Flyway 校验/待执行迁移，验证未登录拦截、注册、BCrypt、JWT 和档案读取；后台工作器使用测试替身，注册及相关写入在测试事务中回滚。

MySQL 回滚不会回收已经分配的自增序号，因此事件 ID 可以出现正常的序号间隔。测试不依赖原有 student 账号，也不调用真实外部 AI。

## 旧文档

此前说明中出现的 `domain/`、`persistence/`、内部 `UserAccountRepository.Account`、`RecordRepository.jdbc()` 和 Spring JDBC 描述属于迁移前结构。当前目录、持久化技术和验证方式以本文及源码为准。

## 本次验证结果

Java 17 下完成全量构建及回归：共 44 项，43 项通过、1 项旧临时库专项测试按条件跳过，失败和错误均为 0。新增 3 项 MySQL 专项测试均已连接本机 `kaoyan_ai`（MySQL 8.0.46）执行通过。Flyway 确认既有结构为 V2，无需迁移。结果索引见 [MyBatis验证结果](MyBatis验证结果.json)，原始报告见 `target/surefire-reports/`。
