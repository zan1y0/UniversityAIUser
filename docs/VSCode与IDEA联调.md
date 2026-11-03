# VS Code 前端 + IDEA 后端

前端项目：`C:\Users\jack\Desktop\UniversityAIUserPage`。后端项目：`D:\STUDY\UniversityAIUser`。

## 已配置的连接

浏览器访问 `http://127.0.0.1:5173/login`；Vite将 `/api/v1` 转发到 `http://127.0.0.1:8080`。业务数据库使用项目已有的MySQL连接配置 `.local-data/mysql-local.properties`。

JWT开发密钥独立存放在 `.local-data/auth-local.properties`，只用于本机开发，目录已被Git忽略。MySQL profile 自动读取这个文件，IDEA无需依赖其他PowerShell终端的环境变量。首次设置可运行 `./scripts/setup-local-auth.ps1`；文件存在时不会覆盖原密钥。操作系统环境中的 `APP_JWT_SECRET` 如果存在则优先。

## 每次启动

1. 确保MySQL服务已运行。
2. IDEA打开后端项目，Maven窗口重新加载依赖。右上角选择 **KaoyanAi MySQL**，点击运行或调试。该配置固定使用Java 17、MySQL profile、8080端口及项目根工作目录。不要使用未配置profile的临时主类运行配置。
3. IDEA控制台出现 `Tomcat started on port 8080`、`Started KaoyanAiApplication` 后，保持运行。
4. VS Code打开桌面的前端目录，在终端执行 `npm run dev`；已有开发服务时无需再启动第二个。默认5173端口。
5. 打开 `http://127.0.0.1:5173/login`，注册或登录。VS Code的浏览器调试地址也应为5173，8080是API服务。

首次连接已存在的MySQL库时，Flyway会应用V2迁移创建 `auth_tokens` 表；不手工删除用户或重建数据库。若旧库由SQL手工建表且没有Flyway历史，先按项目原有数据库接入说明完成基线，不擅自关闭迁移校验。

## 检查连接

打开 `http://127.0.0.1:8080/api/v1/session/csrf` 应返回200及JSON安全凭证；打开 `http://127.0.0.1:5173/api/v1/session/csrf` 也应返回200，说明前端代理已连通。这个GET请求仅建立CSRF会话，不创建用户。

未登录时 `/api/v1/auth/me` 返回401属于正常鉴权；注册成功返回201，登录成功返回200。登录成功后可在浏览器开发者工具Network中看到 `/api/v1/auth/me` 返回200。

若8080连接失败，先看IDEA启动日志；数据库连接失败通常是MySQL未运行或连接配置不正确。`APP_JWT_SECRET`报错需检查密钥文件及IDEA工作目录。修改配置后重启对应服务。

本机调试应始终使用同一个域名，例如全部使用 `127.0.0.1`，避免localhost和127.0.0.1切换造成Cookie会话分离。生产环境仍应通过环境变量或秘密管理系统提供独立密钥，不使用本机开发密钥文件。
