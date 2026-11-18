# 本机 Redis 连接说明

2026-11-17 已将本机 Redis 接入后端，作为可选缓存使用，并完成启动连通性验证。

## 连接参数

- 地址：127.0.0.1:6379（只监听本机回环地址）
- Windows 服务名：`Redis`，安装目录 `D:\Redis`
- 版本：8.10.3（standalone）
- 认证：已开启 `requirepass`，密码保存在被 Git 忽略的 `.local-data/redis-local.properties`，本文不记录
- 配置要点（`D:\Redis\redis.conf`）：`bind 127.0.0.1 -::1`、`protected-mode yes`、`daemonize no`、`appendonly no`

## 项目中的配置

1. `pom.xml` 引入 `spring-boot-starter-data-redis`。
2. `application.yml` 的 `spring.data.redis` 读取 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 和 `REDIS_DATABASE`（兼容 `REDIS_DB`）。`REDIS_CONNECT_TIMEOUT` 和 `REDIS_TIMEOUT` 默认均为 800 ms。
3. `application-local.yml` 通过 `spring.config.import` 读取 `.local-data/redis-local.properties`，该文件已被 `.gitignore` 忽略。
4. `infrastructure.cache.RedisCache` 提供前缀化键、写入带过期时间、读取、删除和 PING。
5. 将 `APP_REDIS_CACHE_ENABLED` 设为 `true`，或在 `.local-data/cache-local.properties` 中写入 `app.cache.redis-enabled=true`，启用目录缓存预热和启动连通性检查。检查会写入、读取和删除探测键，成功时打印 `Redis 连接成功`。
6. 连通性检查使用 `kaoyan-ai:` 前缀，可通过 `REDIS_KEY_PREFIX` 修改。公共目录缓存使用 `kaoyan:v1:` 前缀。

只做缓存：不把 Redis 当业务数据源，也不在其中保存密码、Cookie、JWT 或学生隐私原文。Redis 数据丢失只影响缓存命中，不影响数据库中的业务数据。

## 已验证

- TCP 连接 127.0.0.1:6379 成功，`AUTH` 返回 `+OK`，`PING` 返回 `+PONG`。
- `RedisCacheTests` 在本机 Redis 运行时执行真实写入、读取、删除；Redis 未启动时该用例跳过，不阻塞其他测试。
- 应用启动日志出现 `Redis 连接成功：127.0.0.1:6379，PING=PONG，读写校验通过`，说明连接、认证和读写都正常。

## 手动检查

```powershell
# 服务状态
Get-Service Redis

# 需要密码；也可用任意 Redis 客户端连接 127.0.0.1:6379
D:\Redis\redis-cli.exe -a <密码> ping
```

## 注意

- 本机 Redis 只用密码和回环地址保护，不要改成监听 `0.0.0.0` 或暴露到公网。
- Docker Compose 中的 `redis` 服务当前没有密码；改用该容器时把 `REDIS_PASSWORD` 留空，否则认证会失败。
- 密码只放在本机 `.local-data/redis-local.properties`，不要提交到 GitHub，也不要写进运行配置和文档。
