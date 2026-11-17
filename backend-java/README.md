# Spring Boot microservices

> **继续迁移前先读 [`后端迁移台账.md`](后端迁移台账.md)。** 它记录已覆盖接口、实现文件、行为差异和待办。每次迁移完成后必须同步更新台账和验证记录，避免重复实现或把部分实现误记为完整替换。

Java 17 / Spring Boot 3.4 multi-module Gradle project. The root Gradle build builds the shared library and six separately executable services:

| Module | Port | Responsibility |
| --- | ---: | --- |
| `gateway-service` | 10600 | Single HTTP entry point, route dispatch, JWT validation, identity headers, CORS |
| `auth-service` | 8081 | Login, registration, JWT issuance, role/menu loading |
| `catalog-service` | 8082 | Product browsing, merchant-owned product edit/unpublish, Elasticsearch search/indexing, Canal CDC and MySQL fallback |
| `translation-service` | 8083 | OpenAI-compatible LLM translation, history, daily/monthly statistics and scheduled reports |
| `recommendation-service` | 8084 | Hybrid content/collaborative recommendations, profile and cultural tags, Redis cache and exposure logging; batched AI inference client |
| `platform-service` | 8085 | Glossary, admin, dashboard, click/compliance stats, profiles, product publishing, uploads, RAG assistant |
| `platform-common` | — | Shared response, exception, and MVC operation audit support |

## Primary keys

Every table uses a 64-bit Snowflake id instead of AUTO_INCREMENT — no table has a
database-generated key anymore, so **an insert must supply its id explicitly**.
`SnowflakeIdGenerator` (in `platform-common`) produces `41-bit timestamp | 10-bit
worker | 12-bit sequence`; inject it and call `nextId()` before the insert.

The worker id defaults to `server.port % 1024` (8081→913, 8082→914 ...), which keeps
concurrent services from colliding without any per-service configuration. Override it
with `-Dapp.id.worker-id=N` only when the port heuristic cannot work (e.g. the same
port on several hosts).

Ids are about 19 digits, beyond JavaScript's `Number.MAX_SAFE_INTEGER`, so
`IdGeneratorConfiguration` serialises `Long` as a **string**. Front-end code must treat
ids as opaque strings — never `parseInt` them or compare them numerically.

`migrate_snowflake.py` performed the one-off conversion (id remap, `int→bigint`,
AUTO_INCREMENT removal, foreign keys dropped and rebuilt). It is idempotent-ish and
supports `--dry-run`, but treat it as a migration artifact rather than a routine task.

## Run locally

Requires Java 17+, an existing `ecommerce` MySQL schema, and environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_KEY`. The checked-in Gradle wrapper downloads Gradle 8.9 when first used. Start services in separate terminals from this directory:

```powershell
./gradlew :gateway-service:bootRun
./gradlew :auth-service:bootRun
./gradlew :catalog-service:bootRun
./gradlew :translation-service:bootRun
./gradlew :recommendation-service:bootRun
./gradlew :platform-service:bootRun
```

Or build and start the containers from the repository root with `docker compose -f deploy/docker-compose.java.yml up --build`. The Compose file expects MySQL to be available from the host and requires `DB_PASSWORD` and `JWT_SECRET_KEY`.

### Run from IntelliJ IDEA

This folder is an independent Gradle build. Open it as its own project (`File → Open →` pick `settings.gradle` `→ Open as Project`) so IDEA resolves the Spring Boot classpath. Opening the repository root instead yields a single Python module, and the `main` methods will not even compile.

Six run configurations are checked in under `.run/`; they pin `--server.port`, carry the environment variables below, and add the Elasticsearch truststore for catalog. If IDEA flags a configuration's module as missing, re-pick it in the Module dropdown — the rest can stay as-is.

Spring Boot does not read the repository `.env`, so every `${VAR:default}` in `application.yml` must be supplied as an environment variable. Two failures worth knowing: a missing `DB_PASSWORD` starts the service fine but every request returns 500 with `Access denied for user 'root'@'localhost' (using password: NO)`, and a wrong `ES_URL` makes catalog silently fall back to MySQL ngram (T恤 drops from 568 hits to 28) instead of reporting an error.

Two stack components are outside Gradle: the Python embedding service on 10683 (`start-embedding.bat`) and the Vite dev server on 20682 (`start-frontend.bat`). Recommendation and the RAG assistant depend on the former. `run-all.bat` starts all eight processes with their environment variables already set.

Catalog search reads `ES_URL`, `ES_INDEX`, `ES_USERNAME`, `ES_PASSWORD`, `ES_ENABLED`, and `ES_USE_FOR_BROWSE` (false by default to keep unfiltered browse on MySQL). After deploying a fresh index, invoke the internal catalog endpoint `POST /internal/search/reindex` to create and populate it from MySQL. Product publish/edit/unpublish operations synchronize their documents after commit. The optional Canal consumer is controlled by `CANAL_ENABLED`, `CANAL_HOST`, `CANAL_PORT`, `CANAL_DESTINATION`, `CANAL_USERNAME`, `CANAL_PASSWORD`, `CANAL_CLIENT_ID`, `CANAL_FILTER`, `CANAL_BATCH_SIZE`, and `CANAL_TIMEOUT_MS`; it is disabled by default in Compose during the Python/Java transition. After stopping the Python consumer, enable it with `CANAL_ENABLED=true`; the default Canal address is `host.docker.internal:11111`. `GET /api/search/status` includes Canal connection and processed event counters. For HTTPS Elasticsearch using a private CA, add that CA to the JVM truststore.

The gateway routes public catalog reads and login/register without a token. Other `/api/**` routes require a valid bearer JWT. Gateway ports alone are published by Compose; service ports remain internal. Actuator health is available at `/actuator/health` on each service.

## Python AI inference boundary

The embedding model stays in Python as proposed in `Java重写方案-亮点清单.md`. Run it with `uvicorn backend.ai_service:app --host 0.0.0.0 --port 10683`. It exposes batch `POST /embed` and `POST /similarity`; Java recommendation and RAG services use the recommendation service's internal AI endpoints to call it over HTTP. Set `EMBEDDING_SERVICE_URL` when the service is not on the default address. Recommendation LLM keyword parsing and cultural-tag selection use `LLM_BASE_URL`, `LLM_API_KEY`, and `LLM_MODEL_NAME`; if no key is configured, lexical scoring and rule-based fallback tags remain available.

## Migration status

See [`后端迁移台账.md`](后端迁移台账.md) before changing backend coverage. It is the source of truth for migrated endpoints, implementation gaps, and next steps.

Most user-facing CRUD and commerce flows now have Java endpoints: login/register, product browsing and merchant ownership operations, publish/unpublish, image/avatar upload, profiles, glossary, admin and merchant review, translation/history/statistics, click and dashboard statistics, compliance, and RAG assistant. Product publishing calls translation, persists the legacy MySQL records, clears recommendation cache, and schedules a fresh recommendation on a bounded executor. Recommendation now combines content scoring and the legacy simulated-user rating matrix, applies market tags, records exposures, and caches results in Redis for 30 minutes. The Python embedding model remains the planned AI inference boundary; its matching simulated-user data is packaged in the recommendation module without the source fixture's unused credential fields.

The Java services are not yet a full replacement for Flask. RAG assistant API and retrieval orchestration now run in `platform-service`; the embedding model remains in the Python inference service. Configure `LLM_API_KEY`, `EMBEDDING_SERVICE_URL`, and `ASSISTANT_FAQ_PATH` (Compose mounts the FAQ file and persists Java indexes in a named volume). RAG conversation state is per Java instance. `/api/health/deps` now aggregates Redis circuit state and Elasticsearch cluster health through internal service endpoints. Java search supports IK when installed and otherwise the built-in CJK analyzer, completion plus phrase-prefix suggestions, a longest-token query strategy and highlights, and the Canal TCP consumer. Its token selection is not identical to Python Jieba; live ES/Canal/MySQL/Redis integration verification remains. Glossary Java recall currently covers user terms and synonym/literal matching, without semantic retrieval. Run Python as the production backend until remaining functions are migrated and parity has been verified. Services share the legacy MySQL schema during transition.

Uploads use `UPLOAD_DIR` (local default `../backend/uploads`). The Java Compose file mounts `backend/uploads` into the platform container so the gateway can serve `/uploads/**` from the same directory.
