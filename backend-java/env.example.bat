@echo off
REM ===========================================================================
REM  Template for backend-java\env.bat  --  copy this file and fill in real values
REM
REM    copy env.example.bat env.bat
REM
REM  Why a separate file: Spring Boot does NOT read the project .env file.
REM  Every variable referenced as ${VAR:default} in application.yml must be
REM  exported here before launching the services.
REM
REM  Pure ASCII + CRLF only. cmd parses .bat as GBK before any chcp applies,
REM  so non-ASCII characters would shift bytes and break execution.
REM ===========================================================================
setlocal

REM ---- absolute paths (relative paths resolve against CWD and break) ----
set "TS=%~dp0.."
set "JAVA=java"
set "PY=%TS%\.venv\Scripts\python.exe"

REM ---- MySQL ----
REM An empty password yields "Access denied for user 'root'@'localhost'"
set "DB_URL=jdbc:mysql://localhost:3306/ecommerce?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
set "DB_USERNAME=root"
set "DB_PASSWORD=REPLACE_ME"

REM ---- Elasticsearch 9.x: HTTPS + self-signed CA (blank = disabled) ----
set "ES_URL=https://localhost:9200"
set "ES_USERNAME=elastic"
set "ES_PASSWORD="
set "ES_ENABLED=false"
set "ES_USE_FOR_BROWSE=false"

REM ---- Canal (MySQL binlog -> ES). Leave false unless ES is enabled ----
set "CANAL_ENABLED=false"
set "CANAL_HOST=localhost"
set "CANAL_PORT=11111"

REM ---- JWT: auth-service signs, gateway verifies. MUST be identical ----
REM Generate with: openssl rand -hex 32
set "JWT_SECRET_KEY=REPLACE_WITH_32_BYTES_HEX"
set "JWT_EXPIRATION_HOURS=24"

REM ---- Redis (optional; services degrade via circuit breaker when absent) ----
set "REDIS_HOST=127.0.0.1"
set "REDIS_PORT=6379"
set "REDIS_PASSWORD="
set "REDIS_DB=1"

REM ---- LLM (translation / recommendation / assistant) ----
set "LLM_API_KEY=sk-REPLACE_WITH_YOUR_KEY"
set "LLM_BASE_URL=https://api.deepseek.com"
set "LLM_MODEL_NAME=deepseek-chat"
set "LLM_TIMEOUT=60"

REM ---- Translation engine: false keeps torch/NLLB out of the JVM path ----
set "USE_LOCAL_MT_MODEL=false"

REM ---- service-to-service URLs (all localhost on a single machine) ----
set "AUTH_SERVICE_URL=http://localhost:8081"
set "CATALOG_SERVICE_URL=http://localhost:8082"
set "TRANSLATION_SERVICE_URL=http://localhost:8083"
set "RECOMMENDATION_SERVICE_URL=http://localhost:8084"
set "PLATFORM_SERVICE_URL=http://localhost:8085"
set "EMBEDDING_SERVICE_URL=http://127.0.0.1:10683"

REM ---- filesystem paths that must not be relative ----
set "UPLOAD_DIR=%TS%\backend\uploads"
set "ASSISTANT_FAQ_PATH=%TS%\data\assistant_faq.json"
set "ASSISTANT_INDEX_DIR=%TS%\assistant-index"

REM ---- gateway port: 10600 locally, 10682 in Docker/cloud ----
set "GATEWAY_PORT=10600"

endlocal
