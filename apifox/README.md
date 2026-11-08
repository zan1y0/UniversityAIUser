# Apifox 前后端分离联调

2026-11-05 按仓库当前控制器、认证实现和 `frontend/` 调用更新，契约版本 **1.1.0**，共 **86 个接口**，覆盖原 11 个业务模块。

## 导入接口定义

在 Apifox 的项目设置／导入数据中选择 OpenAPI，导入根目录 `contracts/openapi.json`（也可用同目录 YAML）。已有项目应检查导入差异，按方法和路径更新现有接口，保留原用例。这里修改的是仓库文件，尚未写入你客户端或云端的 Apifox 项目。

配置环境前置 URL 为 `http://127.0.0.1:8080`。接口路径本身已包含 `/api/v1`，前置 URL 不再添加这个前缀。前端 Vite 通过 `/api/v1` 代理到后端；浏览器及 Apifox 分别维护自己的 Cookie，网页已经登录不代表 Apifox 也已登录。整个流程保持同一主机名，不混用 `localhost` 和 `127.0.0.1`。

## 当前认证流程

1. GET `/api/v1/session/csrf`：让 Apifox 自动保存 `SESSION` Cookie，把响应 `data.token` 保存为环境变量 `csrf`。后置操作可选择提取变量，或使用 `pm.environment.set('csrf', pm.response.json().data.token)`。
2. POST `/api/v1/auth/login`：Body → JSON 填 `loginName,password`，Headers 添加 `X-CSRF-Token: {{csrf}}`。没有账号时使用 `/auth/register`，额外填写 `displayName`。
3. 登录／注册成功，后端通过 `Set-Cookie` 签发 HttpOnly `AUTH_TOKEN` JWT；Apifox 自动管理 Cookie，响应 JSON **没有 token 字段**。保持 Cookie 自动发送；当前环境不要继承一个未填写或过期的 Bearer Authorization，否则它优先于 Cookie，会造成 401。
4. 再次 GET `/api/v1/session/csrf` 并更新 `csrf`，因为认证成功后 CSRF 会轮换。
5. GET `/api/v1/auth/me` 确认登录，然后调用业务接口。POST/PUT/PATCH/DELETE 继续携带 `X-CSRF-Token`；有幂等标记的请求还须填写 UUID `Idempotency-Key`。
6. POST `/api/v1/auth/logout` 退出，返回 204 空响应；会撤销 JWT 并销毁 CSRF Session。

也支持 `Authorization: Bearer <已有的有效JWT>`，但写请求仍需 SESSION Cookie 和 CSRF。浏览器使用 Cookie 方案即可，不需要读取 HttpOnly Cookie，也不需要把令牌存入 localStorage。

旧 `/session/login`、`/session/logout` 保留兼容并标记 deprecated；旧退出只销毁 Session，**不会撤销 JWT**。请使用与当前前端相同的 `/auth/*` 流程。

## 本次补齐的接口

| 操作 | 路径 | 说明 |
| --- | --- | --- |
| op082 POST | `/api/v1/auth/register` | 注册并自动登录；201；账号重复为 409 ACCOUNT_EXISTS |
| op083 POST | `/api/v1/auth/login` | JWT Cookie 登录；200；重新登录撤销当前旧 JWT |
| op084 GET | `/api/v1/auth/me` | 获取当前登录用户；200 |
| op085 POST | `/api/v1/auth/logout` | 撤销 JWT 并退出；204 无响应体 |
| op086 POST | `/api/v1/avatar/attachments` | multipart/form-data，仅填写 File 类型 `file`；201 返回 `data.id` |

附件接口按当前实现只保存非空且不超过 10MiB 的私有文件，支持原有 `/files/{id}` 下载／删除。它没有 MIME 魔数检测或内容解析，不会自动传给 AI；当前 `/avatar/interactions` 也没有聊天附件参数。语音互动仍通过 `/files` 上传 `purpose=avatarAudio`，再将返回的 fileId 传给互动接口。该边界已写进契约，不能把附件保存成功等同于 AI 已读取附件。

## 导入及运行流程用例

- `backend-local.postman_collection.json`：可导入 Apifox 的 Postman 格式集合，148 步，使用已有账号，登录／退出已切换 JWT；覆盖 11 模块及错误分支。需要本地种子目录，默认预期未配置外部 AI，会修改该账号的数据，应使用独立本地测试库。
- `auth-jwt.postman_collection.json`：12 步注册、身份恢复、退出失效、重新登录。无须预置账号或模型；每次生成新的测试账号和密码。测试账号及初始档案会保留，仅用于独立测试库。
- 对应的 `*.apifox-cli.json` 用于 CLI。集合会按 Asia/Shanghai 动态计算当天、周一和未来计划日期；无需每次手动改日期。

已有本地样例账号的完整业务回归：

```powershell
./scripts/test-apifox.ps1 -BaseUrl http://127.0.0.1:8080 -LoginName student -Password '<已有账号密码>'
```

独立测试库的注册登录回归：

```powershell
./scripts/test-apifox.ps1 -BaseUrl http://127.0.0.1:18081 -Suite auth
```

首次运行脚本会把 Apifox CLI 安装到 Git 忽略的 `.tools/apifox`。文件二进制和 SSE 仍由后端专项测试覆盖；定义文件包含这些接口，集合不等待无限 SSE。

## 同步与检查

`contracts/openapi.json` 是编辑入口。修改后执行：

```powershell
python -m pip install --target .tools/contract-python PyYAML jsonschema openapi-spec-validator
python scripts/sync-contract.py
python frontend/scripts/generate-contract.py
cd frontend
npx prettier --write src/api/generated.ts src/api/contract-data.json
```

`python scripts/sync-contract.py --check` 校验 OpenAPI、引用、示例、负例、控制器路由覆盖和所有发布副本。后端新增了实际 Spring 路由集合与契约一致的回归检查，认证／附件响应也按新增 Schema 验证。路由一致不代表外部模型及 MySQL 已经过真实验收。

## 本次验证结果（2026-11-05）

- OpenAPI：86项操作与当前控制器路由完全一致，1396个示例、5个负例通过，前后端及静态发布副本一致。
- 后端：40项测试通过，1项 MySQL 专项按原配置跳过；包含 JWT、未知认证字段、附件上传／下载／删除和新增响应 Schema 校验。
- 前端：24项单元测试、类型检查及生产构建通过；22项桌面／手机契约与聊天页面回归通过，另4项真实注册、恢复、退出及重新登录浏览器测试通过。
- Apifox CLI：完整业务集合148个请求、470条断言全部通过；注册登录集合12个请求、35条断言全部通过。

真实 HTTP 与浏览器认证验证使用18081端口的独立H2内存数据库，临时前端使用15173／15174端口，没有向现有MySQL业务库写入测试数据。AI配置为空，验证的是明确的依赖失败分支，不代表真实模型调用已验收。自定义前端端口需在测试后端的 `APP_ALLOWED_ORIGINS` 中配置实际来源，否则写请求会因来源校验返回403；普通本地开发默认支持5173端口。
