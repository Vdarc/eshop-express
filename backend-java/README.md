# Spring Boot 微服务

Java 17 / Spring Boot 3.4 多模块 Gradle 工程。根构建包含一个共享库和六个可独立运行的服务。

| 模块 | 端口 | 职责 |
| --- | ---: | --- |
| `gateway-service` | 10600 | 唯一 HTTP 入口，路由分发、JWT 校验、身份头注入、CORS |
| `auth-service` | 8081 | 登录、注册、JWT 签发、角色与菜单装配 |
| `catalog-service` | 8082 | 商品浏览、商家商品编辑/下架、ES 检索与索引、Canal CDC、MySQL 兜底 |
| `translation-service` | 8083 | OpenAI 兼容协议的 LLM 翻译、历史、日/月统计与定时报表 |
| `recommendation-service` | 8084 | 混合推荐、用户画像与文化标签、Redis 缓存与曝光日志、AI 推理客户端 |
| `platform-service` | 8085 | 术语库、管理端、仪表盘、点击/合规统计、个人中心、商品发布、上传、RAG 客服 |
| `platform-common` | — | 统一响应体、异常处理、MVC 操作审计 |

## 主键设计

所有表使用 64 位雪花 ID，**没有一张表用 AUTO_INCREMENT**，因此插入必须显式提供 id。
`SnowflakeIdGenerator`（在 `platform-common`）生成 `41 位时间戳 | 10 位 worker | 12 位序列`，
注入后调用 `nextId()` 再执行 insert。

worker id 默认取 `server.port % 1024`（8081→913、8082→914……），
这样多服务并发时天然不撞号，无需逐个配置。只有端口推导失效的场景（如多台机器共用同一端口）
才需要用 `-Dapp.id.worker-id=N` 覆盖。

ID 约 19 位，超过 JavaScript 的 `Number.MAX_SAFE_INTEGER`，因此
`IdGeneratorConfiguration` 把 `Long` 序列化为**字符串**。前端必须把 id 当作不透明字符串，
禁止 `parseInt` 或数值比较。

## 本地运行

需要 Java 17+、已初始化的 `ecommerce` MySQL 库，以及环境变量 `DB_URL`、`DB_USERNAME`、
`DB_PASSWORD`、`JWT_SECRET_KEY`。仓库内自带 Gradle wrapper，首次使用会下载 Gradle 8.9。
在本目录下分别启动各服务：

```powershell
./gradlew :gateway-service:bootRun
./gradlew :auth-service:bootRun
./gradlew :catalog-service:bootRun
./gradlew :translation-service:bootRun
./gradlew :recommendation-service:bootRun
./gradlew :platform-service:bootRun
```

或在仓库根目录用 `docker compose -f deploy/docker-compose.java.yml up --build` 构建并启动容器。
Compose 假定 MySQL 在宿主机可用，并要求 `DB_PASSWORD`、`JWT_SECRET_KEY`。

### 用 IntelliJ IDEA 打开

本目录是独立的 Gradle 构建。请作为单独项目打开（`File → Open →` 选中 `settings.gradle`
`→ Open as Project`），IDEA 才能正确解析 Spring Boot 类路径。

Spring Boot **不读**仓库根目录的 `.env`，`application.yml` 里每个 `${VAR:default}`
都必须以环境变量的形式提供。两个容易踩的坑：缺 `DB_PASSWORD` 时服务能正常启动，
但每个请求都返回 500（`Access denied for user 'root'@'localhost' (using password: NO)`）；
`ES_URL` 填错不会报错，而是让 catalog 静默回落 MySQL ngram（「T恤」从 568 条掉到 28 条）。

两个组件不在 Gradle 里：10683 的向量推理服务（`start-embedding.bat`）和
20682 的 Vite 开发服务器（`start-frontend.bat`）。推荐与 RAG 客服依赖前者。
`run-all.bat` 会连同环境变量一起启动全部八个进程。

### 检索与同步配置

catalog 的检索读取 `ES_URL`、`ES_INDEX`、`ES_USERNAME`、`ES_PASSWORD`、`ES_ENABLED`、
`ES_USE_FOR_BROWSE`（默认 false，让无关键词浏览走 MySQL）。部署新索引后，
调用内部接口 `POST /internal/search/reindex` 从 MySQL 建库并灌数据。
商品的发布/编辑/下架在事务提交后同步文档。

可选的 Canal 消费者由 `CANAL_ENABLED`、`CANAL_HOST`、`CANAL_PORT`、`CANAL_DESTINATION`、
`CANAL_USERNAME`、`CANAL_PASSWORD`、`CANAL_CLIENT_ID`、`CANAL_FILTER`、`CANAL_BATCH_SIZE`、
`CANAL_TIMEOUT_MS` 控制，默认关闭。`GET /api/search/status` 会返回 Canal 连接状态与已处理事件数。
使用私有 CA 的 HTTPS Elasticsearch 需要把该 CA 导入 JVM truststore。

网关对公开的商品读取与登录注册放行，其余 `/api/**` 需要合法 Bearer JWT。
Compose 只发布网关端口，服务端口留在内部。每个服务的健康检查在 `/actuator/health`。

## 向量推理服务

语义向量编码由独立的 Python 进程承担，不在 JVM 内加载模型：

```bash
uvicorn backend.ai_service:app --host 0.0.0.0 --port 10683
```

对外提供批量 `POST /embed` 与 `POST /similarity`；Java 侧的推荐与 RAG 客服通过
recommendation 服务的内部 AI 端点以 HTTP 调用。地址不是默认值时设 `EMBEDDING_SERVICE_URL`。

推荐的关键词解析与文化标签选择使用 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL_NAME`；
未配置 key 时仍有词法打分与规则标签兜底。

## 上传目录

上传走 `UPLOAD_DIR`（本地默认 `../backend/uploads`）。Compose 把 `backend/uploads`
挂进 platform 容器，使网关能从同一目录提供 `/uploads/**`。
