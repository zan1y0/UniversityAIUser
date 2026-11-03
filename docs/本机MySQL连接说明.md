# 本机 MySQL 连接说明

2026-10-15 已完成本机数据库连接、建库和 Flyway 初始化，并通过 IDEA 的 MySQL 运行配置启动后端。

## 连接参数

- 地址：127.0.0.1:3306
- 用户：root
- 数据库：kaoyan_ai
- MySQL：8.0.46
- 字符集：utf8mb4，排序规则 utf8mb4_0900_ai_ci
- 密码：保存在本机 `.local-data/mysql-local.properties`，本文不记录。

## IDEA 运行

1. 顶部运行配置选择 `KaoyanAi MySQL`。
2. 点击绿色运行按钮。该配置使用 Java 17，主类为 `com.kaoyan.ai.KaoyanAiApplication`，启动参数为 `--spring.profiles.active=mysql`。
3. 工作目录为项目根目录，便于读取本地密码文件。未选择 MySQL 配置、直接运行普通主类时仍默认 local（H2）。
4. 控制台出现 MySQL JDBC 地址、Flyway v1 和 `Tomcat started on port 8080` 即启动成功。

也可以在项目终端运行 `./scripts/start-mysql.ps1`。

## 已验证

- MySQL TCP 登录成功，CURRENT_USER 为 root@localhost。
- 原先没有 kaoyan_ai 数据库，已创建独立数据库，没有删除或覆盖其他数据库。
- Spring Boot 通过 HikariCP 建立 MySQL JDBC 连接。
- Flyway V1 迁移执行成功：37 张业务/技术表，加 flyway_schema_history，共 38 张表。
- 后端先以打包 JAR 验证成功，随后停止验证进程，改由 IDEA 的 MySQL 运行配置启动。
- IDEA 启动后，`GET http://127.0.0.1:8080/api/v1/session/csrf` 返回 HTTP 200。
- Git 检查确认 `.local-data/mysql-local.properties` 被忽略，且未被 Git 跟踪。

## 数据状态

mysql profile 不自动注入 H2 的演示账号、题库和课程资料。当前 app_users 为空，student 演示账号暂不能用于 MySQL 登录；本次没有迁移 H2 文件数据，也没有配置外部模型。

## IDEA 右侧 Database 面板

项目原有 `@localhost` 数据源保存的凭据与本次后端配置分开管理。刷新该数据源时，IDEA 返回 `Access denied for user root@localhost`。后端使用本次提供的密码已连接成功，该报错来自 IDE 的旧数据源凭据。

当前凭据对话框需要用户手动操作：点击 Change Credentials，更新 root 密码，测试连接并保存，再在 Schema 选择中勾选 kaoyan_ai。电脑操作技能禁止自动填写用户认证对话框，因此没有替用户在此对话框中输入或保存密码。右侧数据库面板尚待这一操作完成；这不影响已运行的后端。

不要将本地密码文件提交 GitHub。本说明和运行配置不包含密码。后续迁移到其他电脑时，需要自行配置连接变量或本地密码文件。
