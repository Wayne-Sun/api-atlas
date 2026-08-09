# API Atlas

API 接口管理与查询平台 —— 统一管理多数据源、动态查询接口、可视化测试。

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4.1, JDK 21, Maven |
| ORM | MyBatis 4.0.1, PageHelper 4.1.1 |
| Database | MySQL (primary), H2 (test), PostgreSQL (optional), Doris (MySQL protocol, jdbc) |
| Search | Elasticsearch 8.16 (ES\|QL + Query DSL) |
| NoSQL | MongoDB (mongodb-driver-sync 5.8.0, MONGO_FIND + MONGO_AGG) |
| Cache / Auth | Redis (token session store, degradable) |
| Security | Spring Security + OAuth2 Resource Server, RSA256 JWT |
| Frontend | Vue 3.5, Vite 8, TypeScript 6 |
| UI | Naive UI 2.44 (tree-shaken, ~309 kB) |
| State | Pinia 2 |
| Testing | Vitest 4 + jsdom (frontend), JUnit 5 + Mockito + H2 (backend) |

## Features

- **Multi-Datasource Management** — JDBC (MySQL, PostgreSQL, Doris) + Elasticsearch + MongoDB client lifecycle, extensible factory registry
- **Dynamic Interface CRUD** — Create API interfaces with 6 query engine types
- **Query Engines**
  - **SQL** — JdbcTemplate with `PreparedStatement` parameter binding (MySQL/PostgreSQL/Doris; Doris uses `LIMIT offset, count` dialect)
  - **IBATIS** — MyBatis XMLBuilder dynamic SQL, in-memory pagination with safety limits (MySQL/PostgreSQL/Doris)
  - **ES\|QL** — Elasticsearch query language with parameter substitution
  - **Query DSL** — Full Elasticsearch JSON query with `index` field stripping
  - **MONGO_FIND** — MongoDB `find` queries (filter/projection/sort) with typed `${param}` substitution and `$skip`/`$limit` pagination
  - **MONGO_AGG** — MongoDB aggregation pipelines with appended `$skip`/`$limit` pagination and `$count` totals (`$out`/`$merge` write stages rejected)
- **Query Testing** — In-browser query execution with parameter input and result display
- **Auth & RBAC** — RSA256 JWT, token revocation via Redis, admin-only user management
- **Audit Logging** — MyBatis interceptor auto-fills `createdBy`/`updatedAt` etc.
- **Data Encryption** — AES/GCM/NoPadding for datasource credentials
- **Paginated APIs** — PageHelper-powered list endpoints with NDataTable frontend
- **Tree-shaken UI** — Naive UI `create()` API, production chunk ~309 kB

## Quick Start

### Prerequisites

```bash
# JDK 21
java -version

# MySQL 8 (Docker container expected)
docker ps --filter name=api-atlas-mysql --format '{{.Names}}' || docker start api-atlas-mysql

# Redis (optional — token revocation degrades gracefully if absent)
redis-cli ping || echo "Redis not running — token revocation degraded"

# Node >= 18
node -v
```

### Backend

```bash
cd backend

# Local profile — uses application-local.yml (gitignored, override passwords/keys)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# Or inline password
SPRING_DATASOURCE_PASSWORD=your_password mvn spring-boot:run
```

The server starts at `http://localhost:8080`. On first startup it automatically:
1. Initializes the database schema (`schema.sql`)
2. Creates a default admin user (password configured via `atlas.admin.default-password` — no password is logged)

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Dev server starts at `http://localhost:5173`, proxies `/api` to `localhost:8080`.

## Project Structure

```
api-atlas/
├── backend/                          # Spring Boot 4.1, Maven, Java 21
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/api/atlas/
│       │   ├── ApiAtlasApplication.java
│       │   ├── config/               # @Configuration, security, encryption, factories
│       │   ├── controller/           # REST endpoints (auth, datasource, interface, user)
│       │   ├── mapper/               # MyBatis interfaces
│       │   ├── model/                # Entities + DTOs (separate for request/response)
│       │   ├── run/config/           # Startup validators (Redis connectivity)
│       │   └── service/              # Business logic + executors
│       │       └── executor/         # DatabaseQueryExecutor, ElasticsearchQueryExecutor
│       └── src/main/resources/
│           ├── application.yml       # Shared config (tracked)
│           ├── application-local.yml # Local overrides (gitignored)
│           ├── schema.sql            # DB init schema
│           └── mapper/               # MyBatis XML mappers
├── frontend/                         # Vue 3, Vite 8, TypeScript 6
│   └── src/
│       ├── layouts/BaseLayout.vue    # Sidebar + header + router-view
│       ├── views/                    # Page components
│       │   ├── datasource/           # List + Editor
│       │   ├── interface/            # List + Editor + TestView
│       │   ├── login/                # Login page (public)
│       │   ├── user/                 # User management (admin-only)
│       │   └── NotFound.vue          # 404 catch-all
│       ├── stores/                   # Pinia stores (auth, datasource, interface, user)
│       ├── router/index.ts           # Hash-mode router with auth guard
│       └── utils/
│           ├── request.ts            # Axios instance + interceptors
│           └── naive-ui.ts           # Tree-shaken Naive UI plugin
├── doc/prd/
│   └── api-atlas-datasource-interface.md
└── .omo/                             # OpenCode plans & evidence
```

## Commands

```bash
# Backend
cd backend && mvn compile                          # Compile
cd backend && mvn test -Dspring.profiles.active=test  # Run tests
cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run  # Start

# Frontend
cd frontend && npm run dev          # Dev server
cd frontend && npm run build        # Production build
cd frontend && npm run type-check   # TypeScript check
cd frontend && npm run test:run     # Run tests (CI)
cd frontend && npm test             # Run tests (watch)
```

## Configuration

### `application.yml` (tracked)

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/api_atlas` | MySQL connection |
| `spring.data.redis.*` | `localhost:6379` | Redis connection |
| `atlas.encryption.secret-key` | `CHANGE_ME_*` | AES-256 key (Base64, 32 bytes) |
| `atlas.jwt.private-key` | `CHANGE_ME_*` | RSA private key (PEM) |
| `atlas.jwt.public-key` | `CHANGE_ME_*` | RSA public key (PEM) |
| `atlas.security.redis-fail-closed` | `true` | Fail closed (503) when Redis is unavailable during token validation; set `false` for fail-open |
| `atlas.security.allow-private-hosts` | `false` | Allow loopback/private/link-local hosts in datasource connections (SSRF guard bypass) |
| `atlas.executor.query-timeout-seconds` | `30` | JDBC + IBATIS statement timeout (seconds) |
| `atlas.executor.ibatis.max-memory-rows` | `100000` | IBATIS in-memory pagination limit |
| `atlas.mongodb.*` | `5000 / 5000 / 60000` | MongoDB client timeouts (connect / server-selection / socket, ms) |

### `application-local.yml` (gitignored)

Override all secrets here:

```yaml
spring:
  datasource:
    password: your_mysql_password
atlas:
  encryption:
    secret-key: your_base64_32_byte_key
  jwt:
    private-key: |
      -----BEGIN PRIVATE KEY-----
      ...
      -----END PRIVATE KEY-----
    public-key: |
      -----BEGIN PUBLIC KEY-----
      ...
      -----END PUBLIC KEY-----
```

## Default Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | Random UUID (generated, not logged) | `ADMIN` |

Configure a custom password via `atlas.admin.default-password` in `application-local.yml`.

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | No | Login (BCrypt + JWT) |
| POST | `/api/auth/logout` | Yes | Logout (revoke token) |
| GET | `/api/auth/me` | Yes | Current user info |
| GET/POST/PUT/DELETE | `/api/datasources` | Yes | Datasource CRUD |
| GET/POST/PUT/DELETE | `/api/interfaces` | Yes | Interface CRUD |
| GET/POST | `/api/interfaces/{id}/test` | Yes | Execute query test |
| PUT | `/api/interfaces/{id}/status` | Yes | Update interface status |
| GET/POST/PUT/DELETE | `/api/users` | Admin | User management |

## Testing

```bash
# Backend (52 tests, H2 in-memory)
cd backend && mvn test -Dspring.profiles.active=test

# Frontend (25 tests, Vitest + jsdom)
cd frontend && npm run test:run
```

## Architecture Notes

- **Datasource types** use `String` identifiers with a `ConcurrentHashMap`-based factory registry — add new types by implementing `DataSourceFactory<T>` without modifying existing code
- **Token revocation** uses Redis (`token:{jti}` → TokenSession). If Redis is unavailable, authentication still works but revocation is degraded
- **State machine**: Interface statuses follow `PENDING_TEST → ONLINE ⇄ OFFLINE`
- **Audit fields** are auto-populated by a MyBatis interceptor, never set manually
- **All AES encryption** goes through `EncryptionUtil` — never duplicate cipher code
- **Naive UI** uses the `create()` API for tree-shaking — only explicitly listed components are bundled
