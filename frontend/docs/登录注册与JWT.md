# 登录、注册与 JWT

前端项目：`C:\Users\jack\Desktop\UniversityAIUserPage`。打开根地址立即显示居中的淡绿色登录／注册卡片，背景有32个柔和模糊的萤火虫光点。支持手机布局、键盘操作和系统「减少动态效果」设置。

## 本地启动

后端项目根目录运行：

```powershell
./scripts/start-auth-local.ps1
```

前端目录运行 `npm run dev`，访问 `http://127.0.0.1:5173`。先注册新账号即可登录，不需要内置演示账号。前端 API 默认为 `/api/v1`，Vite 代理到后端8080端口。已有5173或8080服务需要先由你关闭或重启以加载新代码。

账号支持3～32位英文字母、数字和下划线，统一转为小写；手机号也可以作为账号。昵称1～32个字符。注册密码至少8个字符，同时包含字母、数字，UTF-8编码不超过72字节。注册成功自动登录。登录状态刷新可恢复；JWT默认30分钟过期，过期需要重新登录。

## 接口

所有返回沿用项目 `code/message/data/traceId` 格式。注册、登录先 GET `/api/v1/session/csrf`，保存 SESSION Cookie，再使用返回的 `X-CSRF-Token` 请求头提交。成功认证后 CSRF 会轮换，下一次写操作前重新获取；前端已自动处理。

| 方法 | 路径 | 参数／行为 |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | `loginName,password,displayName`；201，写入账号和初始备考档案，并签发Cookie |
| POST | `/api/v1/auth/login` | `loginName,password`；200，旧JWT撤销并轮换Cookie |
| GET | `/api/v1/auth/me` | 返回当前用户的id、账号、昵称 |
| POST | `/api/v1/auth/logout` | 204，撤销JWT、清Cookie、销毁CSRF会话 |

接口错误包括：401 账号密码错误／凭证失效；403 CSRF或来源校验失败；409 账号重复；422 参数无效；429 一分钟内同连接来源超过10次认证尝试。限流为单进程内存窗口，多实例部署应换成网关或Redis共享限流。反向代理部署应在可信代理层限流，目前不信任客户端自行填写的 `X-Forwarded-For`。

浏览器JWT保存在 `AUTH_TOKEN` Cookie：HttpOnly、SameSite=Lax、路径 `/api/v1`，不进入localStorage/sessionStorage或响应JSON。生产 profile 启用Secure，需HTTPS。也支持 `Authorization: Bearer <JWT>` 的请求；写操作仍要求CSRF。旧 `/session/login` 会话接口为原81项业务契约保留，新前端使用 `/auth/*`。

## 数据库与密钥

注册事务写入 `app_users`、`profiles`，令牌状态存入 `auth_tokens`。Flyway V2自动创建令牌表，提供MySQL及H2版本，不修改旧V1迁移。不要手工提前执行V2，否则Flyway重复建表会失败。

密码使用 BCrypt（cost=12）加盐哈希；响应不返回哈希。账号、昵称、用户ID、令牌ID、时间都通过JDBC占位符绑定，禁止拼接输入；账号唯一约束负责处理并发重复注册。已有RecordRepository对动态表名使用固定白名单。

JWT使用Spring Security / Nimbus实现HS256签名并固定验证算法、签发者、接收方、签发时间、到期时间；还查询数据库中的jti和到期时间，因此退出后原令牌立即失效。数据库不保存JWT原文或密码原文。

MySQL、dev、prod运行时必须设置 `APP_JWT_SECRET` 为至少32随机字节的Base64。PowerShell可在当前终端生成：

```powershell
$jwtKeyBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtKeyBytes)
$env:APP_JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
./scripts/start-mysql.ps1
```

生成值请保存在你的秘密管理配置或IDE运行环境中；每次重新生成会使旧JWT失效。`.env.example`只是示例，Spring不会自动加载`.env`。local profile若不配置密钥会随机生成进程内密钥，后端重启后重新登录即可，已注册账号仍存于H2文件数据库。

`APP_JWT_TTL_SECONDS`可配置有效期（60～86400秒，默认1800）。`APP_ALLOWED_ORIGINS`可配置逗号分隔的前端来源（默认localhost及127.0.0.1的5173端口）；生产跨域部署需设置为实际可信前端来源。

## 验证

已通过后端37项测试（MySQL专用测试另1项按原配置跳过）、前端19项单元测试、桌面与手机4项真实浏览器认证测试，并成功构建前后端产物。

`AuthIntegrationTests`覆盖注册入库、哈希、档案创建、大小写重复账号、登录、JWT轮换、退出重放、无CSRF、恶意来源、注入输入、弱密码、错误参数、伪造和过期凭证、跨新会话限流。既有业务回归保留。

前端新增 `tests/e2e/auth.spec.ts`，覆盖首屏、表单校验、真实注册、Cookie属性、刷新恢复、退出、保护路由和重新登录；桌面及手机运行。浏览器联调使用独立H2数据库，不向现有MySQL业务库写入测试账号。MySQL迁移提供但未对用户业务库执行验证。

实现参考：[OWASP参数化查询](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)、[OWASP JWT](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html)。
