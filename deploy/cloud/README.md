# 云服务器启动包（演示最小集）

这套脚本按"MySQL 上云、其余全关"的假设编写， demonstrations 不需要你的 Windows 本机开机。

## 文件

| 文件 | 作用 |
| :--- | :--- |
| `cloud.env.example` | 环境变量模板（**含假密钥，必须改**） |
| `start-all.sh` | 按依赖顺序启动，逐端口等待，日志落盘 |
| `stop-all.sh` | 按 PID 文件停止 |
| `../mysql/ecommerce_full_20261116.sql` | 完整数据库备份（结构 + 数据） |
| `../mysql/云服务器导入清单.md` | MySQL 导入步骤 |

## 一、传到云服务器

在你**本机**执行（`user@host` 换成实际 SSH）：

```bash
scp -r deploy <user>@<host>:/opt/ecommerce/TraslationSystem/
```

需要传的目录（缺一不可）：

```
deploy/
├── cloud/          ← 本目录（脚本）
└── mysql/          ← dump 与导入清单

backend-java/       ← 六个 jar 包（见下）
backend/            ← Python embedding 服务源码（仅 EMBEDDING_ENABLED=true 时需要）
data/               ← assistant_faq.json（仅 RAG 客服需要）
frontend/dist/      ← 前端静态产物
```

## 二、云服务器准备

```bash
# 1. Java 17（Spring Boot 3 需要 17）
sudo apt install -y openjdk-17-jre-headless iproute2

# 2. MySQL 8 + 导入数据 → 见 ../mysql/云服务器导入清单.md
sudo mysql < /opt/ecommerce/TraslationSystem/deploy/mysql/ecommerce_full_20261116.sql

# 3. 建应用账号（dump 不含授权）
sudo mysql -e "CREATE USER 'ecommerce_app'@'localhost' IDENTIFIED BY '强密码';
GRANT SELECT,INSERT,UPDATE,DELETE ON ecommerce.* TO 'ecommerce_app'@'localhost';
FLUSH PRIVILEGES;"

# 4. 目录权限（上传目录要可写）
mkdir -p /opt/ecommerce/TraslationSystem/backend-java/uploads
chmod +x /opt/ecommerce/TraslationSystem/deploy/cloud/*.sh
```

## 三、jar 包是否要重新构建

如果云服务器是 **Linux x86_64**，本机 `build/libs/` 下已构建的 jar 可直接用（纯 Java，无平台相关代码）。**不要传 `*-plain.jar`**，那些没有依赖。

若需重新构建：

```bash
cd /opt/ecommerce/TraslationSystem/backend-java
./gradlew --no-daemon :auth-service:bootJar :catalog-service:bootJar \
  :translation-service:bootJar :recommendation-service:bootJar \
  :platform-service:bootJar :gateway-service:bootJar
```

## 四、配置并启动

```bash
cd /opt/ecommerce/TraslationSystem/deploy/cloud
cp cloud.env.example cloud.env
vim cloud.env            # 改 DB_PASSWORD / JWT_SECRET_KEY / LLM_API_KEY 三处

set -a && source cloud.env && set +a
./start-all.sh
```

启动脚本会依次等待每个端口，输出类似：

```
=== [0] Python embedding ===   (仅开启时)
  [START] embedding -> :10683
  [OK]   embedding listening on :10683
=== Java services ===
  [START] auth-service -> :8081
  [OK]   auth-service listening on :8081
  ...
=== Done ===
  Gateway : 10682
```

## 五、验证

```bash
# 网关存活
curl http://localhost:10682/api/products?per_page=1

# 深度校验（全部应为 200）
for p in /api/products /api/search/status /api/search/suggest?q=%E8%BF%9E; do
  curl -s -o /dev/null -w "%{http_code} $p\n" "http://localhost:10682$p"
done

# 看日志
tail -f /opt/ecommerce/TraslationSystem/deploy/cloud/logs/gateway-service.log
```

> **不要裸调下游端口**。所有 `/api/**` 必须走网关 `10682`：网关的 `GatewayJwtFilter` 会从 JWT 解析并**注入 `X-User-Id` / `X-User-Name` / `X-Role-Keys`**，下游服务直接依赖这些头。裸调 `8085` 会因为缺 `X-User-Id` 抛 `MissingRequestHeaderException` → 500（这是调试时实际踩过的坑，不是理论风险）。
>
> 需要身份的接口（如 `/api/assistant/chat`）要带登录后的 Bearer token：
> ```bash
> curl -H "Authorization: Bearer <token>" \
>      -H "Content-Type: application/json" \
>      -d '{"message":"术语库怎么使用"}' \
>      http://localhost:10682/api/assistant/chat
> ```

浏览器打开 `http://<云服务器公网IP>/`。**网关不托管静态文件**（代码里没有任何静态资源处理器），前端必须用 Nginx 单独托管：

```nginx
server {
    listen 80;
    server_name _;
    root /opt/ecommerce/TraslationSystem/frontend/dist;
    index index.html;

    # Vue Router 的 history 模式需要回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 所有 API 转发到网关；网关会自动注入 X-User-Id 等身份头
    location /api/ {
        proxy_pass http://127.0.0.1:10682;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

因为前端 `VITE_API_BASE` 为空（相对路径），**API 和页面同域**，这套配置正好对上。若不用 Nginx，则需要重新构建前端并设 `VITE_API_BASE=http://<公网IP>:10682`，同时开放 10682 安全组。

## 六、三个必改项（不改起不来或没功能）

| 变量 | 不改的后果 |
| :--- | :--- |
| `LLM_API_KEY` | 翻译接口 500，客服降级为空 |
| `DB_PASSWORD` | 六个服务启动即 `Access denied` |
| `JWT_SECRET_KEY` | auth 签发与网关校验不一致，所有登录态 401 |

## 七、功能降级清单（这是"演示最小集"的代价）

| 功能 | 状态 | 原因 |
| :--- | :--- | :--- |
| 搜索 | ✅ 可用 | 回落 MySQL ngram，dump 含 `ft_product_search` 索引 |
| 翻译 | ✅ 可用 | 走 DeepSeek API |
| 推荐 | ✅ 部分可用 | 无 embedding → 退化为内容召回 + 默认兜底 |
| RAG 客服 | ❌ 不可用 | 依赖 SBERT embedding，需 `EMBEDDING_ENABLED=true` 并传模型 |
| 增量同步 | ❌ 不需要 | ES 关闭，Canal 无下游 |

**若要把客服救回来**：把本机 `huggingface_cache/models--sentence-transformers--paraphrase-multilingual-MiniLM-L12-v2` 传到云端 `$AI_MODEL_ROOT` 下，装 `sentence-transformers` + `torch`，然后在 `cloud.env` 设 `EMBEDDING_ENABLED=true`。**不需要传 NLLB 那 2.8G**——`USE_LOCAL_MT_MODEL=false` 时它不会被加载。

## 八、安全提醒

- 云服务器安全组只开 **10682**，MySQL 3306 不要对公网开放
- `cloud.env` 含明文 API Key，**不要提交到 Git**（已在 `.gitignore` 建议中加入）
- 演示结束用 `./stop-all.sh` 停掉，别留着 LLM API Key 在公网机器上跑
