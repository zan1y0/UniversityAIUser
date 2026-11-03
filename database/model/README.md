# UniversityAIUser 数据库设计交付

本目录根据 2026-10-18 本机 `kaoyan_ai` 实际结构生成：37 张业务/技术表、221 个字段、2 条实际外键。`flyway_schema_history` 由 Flyway 管理，不作为业务表重复建模。

## 文件

- `01_schema.mysql.sql`：MySQL 8.0 建库、建表、索引与外键脚本；不包含 DROP。
- `02_demo_data.mysql.sql`：33 条本地联调样例，含两个演示账号及题库、试卷、知识点等；非真实招生数据。
- `UniversityAIUser.pdm`：PowerDesigner 原生 XML 模型，37 表、221 列、2 引用、6 个物理图。
- `数据库设计说明书.docx`：根据桌面原模板六章结构填写，包含完整字段明细和持久化实现。
- `验证记录.json`：SQL、模型结构与测试结果核验。

## 执行顺序

推荐继续由 Spring Boot Flyway 的原 V1 脚本初始化空数据库，再按需运行样例脚本。若手工执行交付 SQL，则顺序为 01、02；初次运行后若要接入 Flyway，必须核对结构并显式使用 baseline 版本 1。不要在已知结构不一致的库中启用自动 baseline。

01 使用 IF NOT EXISTS，保留已有数据，但不修复已有表的结构差异。02 按主键/账号判重，不覆盖已有记录；两次执行已在独立 MySQL 验证库中实测。当前正式 kaoyan_ai 没有注入这些样例。

演示账号为 student、student2，无固定默认密码。运行 02 前，使用 Spring Security 的 `new BCryptPasswordEncoder(12).encode(自行选择的密码)` 生成两个哈希，并在同一个 MySQL 会话中通过 `SET @student_password_hash = '生成的哈希';` 和 `SET @student2_password_hash = '生成的哈希';` 传入，再执行脚本。不要将真实密码或哈希写回交付 SQL。脚本在插入数据前检查哈希非空、长度及 BCrypt 前缀；缺失时会报错，请停止执行，不要使用忽略错误继续运行的选项。已有账号不会被覆盖。这不是 MySQL 管理员密码，本目录不包含本机连接密码文件。

## PowerDesigner

用 File > Open 打开 PDM，通过 Physical Diagrams 选择总览或主题图；Database > Generate Database 可导出 SQL。导出时取消 Drop 选项并先查看预览。

本机 PowerDesigner 16.7 的 DBMS 资源仅含 MySQL 5.0 兼容描述，因此模型内嵌该资源；实际运行目标为 MySQL 8.0.46，交付 SQL 已在该版本执行。PDM 的全部 XML 对象引用已校验，但 PowerDesigner 自动化服务启动返回 CO_E_SERVER_EXEC_FAILURE，未完成工具内打开和再次导出验证。交付 SQL 是实际 MySQL 结构导出，不宣称已由 PowerDesigner 界面生成。文档关系图也不是工具截图。

## 持久化代码

项目 `src/main/java/com/kaoyan/ai/persistence`：RecordRepository 管理 34 张聚合表；本次新增 UserAccountRepository、IdempotencyRepository、EventOutboxRepository，并接入 SessionService、ApiExecutor、EventService、LocalDataInitializer。接口契约未变，数据库原 V1 迁移未修改。

原 21 项测试、新增 4 项持久化测试及 1 项显式开启的 MySQL 往返测试通过。MySQL 测试仅使用本次创建的独立验证库，测试事务回滚；正常构建默认跳过该 MySQL 测试。

官方依据：[SAP PowerDesigner 文档](https://help.sap.com/docs/SAP_POWERDESIGNER)。
