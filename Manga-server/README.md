# Manga Server

漫剧平台后端服务，基于 Java 21、Spring Boot 3、Spring Security、MyBatis-Plus 和 MySQL 8.4。

## 本地环境

- JDK 21
- Maven 3.8+
- Docker 与 Docker Compose（用于启动 MySQL）

确认 Maven 使用的是 JDK 21：

```powershell
$env:JAVA_HOME = 'D:\jdk\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

## 启动 MySQL

复制环境变量示例，并填写数据库密码、JWT 密钥和初始管理员密码：

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
docker compose ps
```

默认配置：

- 地址：`localhost:63376`
- 数据库：`Manga`
- 用户名：`root`
- 密码：`.env` 中的 `MANGA_DB_PASSWORD`
- 数据目录：`docker/mysql/data`

`MANGA_JWT_SECRET` 至少需要 32 字节。`MANGA_INITIAL_ADMIN_PASSWORD` 只在配置的管理员账号尚不存在时使用，密码会以 BCrypt 摘要写入数据库。

停止中间件：

```powershell
docker compose down
```

## 本地启动应用

参考 `ai-fusion-video` 的配置分层，项目将可提交的本地开发配置放在 `src/main/resources/application-local.yml`，将密码、密钥等私密值放在项目根目录下被 Git 忽略的 `.env`。`application.yml` 默认启用 `local` Profile，并将 `.env` 按 properties 格式导入，因此从 IntelliJ 或命令行启动都不需要额外的 dotenv 插件：

```powershell
Copy-Item .env.example .env
# 编辑 .env，填写当前开发环境的密码和密钥
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。

本项目当前已生成本机专用的 `.env`，连接 DBeaver 使用的 `Manga` 数据库。该文件只用于当前开发环境，不应提交或复制到公开位置。

操作系统环境变量的优先级高于 `.env`。其他数据库连接参数可通过 `MANGA_DB_HOST`、`MANGA_DB_PORT`、`MANGA_DB_NAME`、`MANGA_DB_USERNAME` 或完整的 `MANGA_DB_URL` 覆盖；部署到其他环境时可通过 `SPRING_PROFILES_ACTIVE` 切换 Profile。

前端本地 API 地址保存在 `Mange-web/.env.local`，当前指向 `http://localhost:8080/api/v1`，Vite 启动时会自动读取该文件。

## Docker 启动应用

`Dockerfile` 只负责构建和启动后端应用：

```powershell
docker build -t manga-server:local .
docker run --rm `
  --name manga-server `
  --network manga-mysql-network `
  -p 8080:8080 `
  -e MANGA_DB_HOST=mysql-server `
  -e MANGA_DB_PORT=3306 `
  -e MANGA_DB_NAME=Manga `
  -e MANGA_DB_USERNAME=root `
  -e MANGA_DB_PASSWORD='你在 .env 中填写的密码' `
  -e MANGA_JWT_SECRET='至少 32 字节的随机密钥' `
  -e MANGA_INITIAL_ADMIN_PASSWORD='初始管理员强密码' `
  manga-server:local
```

## 数据库初始化

全部业务表定义在 `src/main/resources/manga.sql`。应用启动时会幂等执行该文件，因此连接 DBeaver 中的 `Manga` 库后会自动建表；也可以直接在 DBeaver 中打开并执行该文件。

所有表和字段均带中文注释，每张表统一包含 `created_at`、`updated_at`、`created_by`、`updated_by`。项目仅保留 `manga.sql` 作为数据库初始化脚本，后续结构调整直接同步维护该初始化脚本。

首次启动还会初始化 `GUEST`、`USER`、`ADMIN` 三个角色，并创建：

- 用户名：`MANGA_INITIAL_ADMIN_USERNAME`，默认 `admin`
- 密码：`MANGA_INITIAL_ADMIN_PASSWORD`，没有代码默认值，必须由环境变量提供
- 角色：`USER`、`ADMIN`

后续启动不会重置已存在管理员的密码。

业务数据访问统一使用 MyBatis-Plus。Mapper Java 接口只声明方法，自定义联表、批量写入和状态更新 SQL 统一保存在 `src/main/resources/mapper`；用户列表通过一次聚合查询装配角色，避免逐用户查询造成 N+1。

当前 `Manga` 数据库已初始化管理员账号 `admin`。初始密码按本次开发约定设置为 `admin`，正式部署前应在提供密码修改能力后立即更换为强密码。

## 鉴权接口

平台使用无状态 JWT Bearer Token，暂不开放注册：

- `POST /api/v1/auth/login`：数据库账号密码登录
- `POST /api/v1/auth/guest`：获取短期游客令牌，游客不会写入数据库
- `POST /api/v1/auth/wechat/qr`：创建微信公众号临时登录二维码
- `GET /api/v1/auth/wechat/qr/{loginToken}`：轮询扫码登录结果并一次性兑换 JWT
- `GET|POST /api/v1/auth/wechat/callback`：微信公众号服务器验证与事件回调
- `GET /api/v1/users/me`：查询当前身份
- `GET /api/v1/users`：管理员查询用户列表
- `PUT /api/v1/users/{userId}/roles`：管理员替换用户角色
- `GET /api/v1/roles`：管理员查询角色目录

账号登录示例：

```powershell
$loginBody = @{ username = 'admin'; password = $env:MANGA_INITIAL_ADMIN_PASSWORD } | ConvertTo-Json
$login = Invoke-RestMethod http://localhost:8080/api/v1/auth/login `
  -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
Invoke-RestMethod http://localhost:8080/api/v1/users/me -Headers $headers
```

## 微信公众号扫码登录

扫码登录使用带场景值的临时公众号二维码。用户扫码并关注公众号后，微信将 `subscribe` 或 `SCAN` 事件推送到 Manga，后端绑定 OpenID、创建普通用户并确认登录会话，前端随后通过轮询取得 JWT。

需要在私密配置或环境变量中提供：

- `wx.gzh.app-id` / 公众号 AppID
- `wx.gzh.secret` / 公众号 AppSecret
- `MANGA_WECHAT_GZH_APP_ID` / 微信公众号 AppID
- `MANGA_WECHAT_GZH_SECRET` / 微信公众号 AppSecret
- `MANGA_WECHAT_GZH_TOKEN` / 自定义回调 Token，必须与微信后台保持一致

微信后台消息推送 URL 配置为公开 HTTPS 地址：

```text
https://你的后端域名/api/v1/auth/wechat/callback
```

当前实现使用明文消息模式。后端出口 IP 必须加入公众号 API IP 白名单，公众号还必须拥有“生成带参数的二维码”接口权限；开发期间可使用微信公众平台测试号，正式环境建议使用具备权限的已认证公众号。

## Redis 配置

Redis 连接项定义在 `application-local.yml` 的 `spring.data.redis` 下，本地私密值由 Git 忽略的 `.env` 提供：

- `MANGA_REDIS_HOST` / Redis 主机
- `MANGA_REDIS_PORT` / Redis 端口
- `MANGA_REDIS_PASSWORD` / Redis 密码；未启用认证时可留空
- `MANGA_REDIS_DATABASE` / Redis 数据库编号，默认 `0`
- `MANGA_REDIS_CONNECT_TIMEOUT` / 建连超时，默认 `5s`
- `MANGA_REDIS_TIMEOUT` / 命令超时，默认 `3s`
- `MANGA_REDIS_KEY_PREFIX` / Manga 业务 Key 前缀，默认 `manga`
- `MANGA_REDIS_EXTERNAL_LOGIN_EXPIRED_RETENTION` / 扫码会话过期后的状态保留时间，默认 `10m`
- `MANGA_EXTERNAL_LOGIN_SESSION_STORE` / 扫码会话存储策略，默认 `redis`；测试环境可设为 `jdbc`

当前 Redis 用于以下短期、高频或跨实例共享数据：

- 微信公众号 `access_token`：按微信返回的有效期缓存，并提前一分钟失效以便安全刷新。
- 微信扫码登录会话：通过 Redis TTL 自动清理，轮询不再反复查询 MySQL；确认、消费和过期使用 Lua 脚本保证原子状态迁移。

Manga 访问令牌继续使用无状态 JWT。当前没有退出登录、强制下线或令牌撤销需求，因此不把每个 JWT 重复保存到 Redis，避免所有鉴权请求额外访问一次 Redis；增加撤销需求时再接入 `jti` 黑名单或会话注册表。

## 阿里云 OSS 存储

图片上传支持本地磁盘和阿里云 OSS 两种策略。存储策略只从 `.env` 或操作系统环境变量读取，不写入数据库；默认使用 OSS，填写以下配置即可：

```properties
MANGA_OSS_ENABLED=true
MANGA_OSS_ENDPOINT=
MANGA_OSS_ACCESS_KEY_ID=
MANGA_OSS_ACCESS_KEY_SECRET=
MANGA_OSS_BUCKET_NAME=manga-ai
MANGA_OSS_OBJECT_PREFIX=mannga
MANGA_OSS_PUBLIC_DOMAIN=
```

- `MANGA_OSS_ENDPOINT` 填写 Bucket 所在地域的外网 Endpoint，例如控制台“概览”页面显示的地域节点，不要包含 Bucket 名称。
- AccessKey 建议使用 RAM 用户并仅授予 `manga-ai/mannga/*` 所需的对象上传权限，不要使用阿里云主账号 AccessKey。
- `MANGA_OSS_PUBLIC_DOMAIN` 可留空；留空时返回 `https://manga-ai.<Endpoint>/mannga/...`。配置 CDN 或自定义域名后填写该域名即可。
- 当前项目需要浏览器长期访问封面图和画风参考图，因此 Bucket 对象需要具备公开读取能力，或通过可公开访问的 CDN/自定义域名提供读取能力。

默认对象键格式为 `mannga/images/<UUID>.<扩展名>`。切回本地存储只需将 `MANGA_OSS_ENABLED` 改为 `false`。

## AI 服务配置

登录控制台后可在侧栏“AI 配置”中保存多套服务商连接，支持 OpenAI、OpenAI Compatible、Anthropic、Gemini、DeepSeek、DashScope、Ollama 和自定义协议，并可选择一套启用的默认配置。

API Key 使用 AES-GCM 加密后写入 `manga_ai_provider_config.api_key_ciphertext`，列表和更新响应不会返回明文或密文，仅返回是否已配置以及脱敏摘要。生产环境应在 `.env` 中提供独立的高强度随机密钥：

```properties
MANGA_AI_SECRET_ENCRYPTION_KEY=请替换为独立的高强度随机值
```

未单独配置时，本地环境会回退使用 `MANGA_JWT_SECRET` 以便启动；正式部署不建议复用。加密密钥变更后旧 API Key 无法解密，因此轮换前需要先迁移密文或在界面中重新保存。

配置接口均要求正式用户 JWT，游客不能访问：

- `GET /api/v1/ai-provider-configs`
- `POST /api/v1/ai-provider-configs`
- `PUT /api/v1/ai-provider-configs/{configId}`
- `DELETE /api/v1/ai-provider-configs/{configId}`
- `PUT /api/v1/ai-provider-configs/{configId}/default`

## 日志与 traceId

后端统一使用 SLF4J 输出日志。每个 HTTP 请求都会复用合法的 `X-Trace-Id` 请求头或生成新的 traceId，并通过同名响应头返回；日志格式会从 MDC 输出该值。前端如需主动传递 traceId，CORS 已允许并暴露该请求头。

日志默认写入控制台和 `logs/manga-server.log`，并按文件大小滚动。以下环境变量可以覆盖默认值：

- `MANGA_LOG_FILE` / 日志文件路径
- `MANGA_LOG_MAX_FILE_SIZE` / 单个日志文件上限，默认 `20MB`
- `MANGA_LOG_MAX_HISTORY` / 历史文件保留数量，默认 `30`
- `MANGA_LOG_TOTAL_SIZE_CAP` / 日志文件总容量上限，默认 `1GB`

`@Async` 默认使用自定义 `applicationTaskExecutor`。线程池通过 `MdcTaskDecorator` 传播并恢复 MDC，避免异步任务丢失 traceId 或在线程复用时串链。线程池容量可通过 `MANGA_ASYNC_*` 环境变量调整。

## 示例接口

- `GET /api/v1/public/health`：公开应用状态
- `GET /actuator/health`：公开运行状态
- `POST /api/v1/auth/guest`：游客登录
- `GET /api/v1/users/me`：当前认证身份

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/public/health
Invoke-RestMethod http://localhost:8080/api/v1/auth/guest -Method Post
```

## 目录说明

```text
com.manga
├── common          # 通用响应、异常等横切能力
├── config          # Spring 与安全配置
├── controller      # HTTP 接口层
├── dto             # 请求/响应对象
├── entity          # 用户和角色领域数据
├── mapper          # MyBatis-Plus Mapper 契约
├── repository      # 数据访问聚合与 Redis/JDBC 存储策略
└── service         # 认证及用户角色业务
```

自定义 Mapper SQL 位于 `src/main/resources/mapper`。

当前阶段复用 `manga.sql` 管理表结构；表结构进入频繁迭代后建议接入 Flyway，按版本保存增量迁移。
