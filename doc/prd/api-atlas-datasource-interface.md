# API Atlas — Data Source & Interface Management Module PRD

## 1. Overview

API Atlas is a low-code API gateway that enables developers to expose SQL queries, MyBatis mappings, and Elasticsearch queries as RESTful HTTP endpoints without writing backend controller code. The Data Source & Interface Management module provides the core authoring workflow: administrators register database/Elasticsearch connections (data sources), define query templates against them (interfaces), and publish those interfaces as live API endpoints consumable by client applications.

**Target users:** Backend developers, data engineers, and platform operators who need to rapidly expose data-layer operations as REST APIs without deploying dedicated services.

**Module scope:** Data source lifecycle (CRUD, connectivity testing, credential encryption) and interface lifecycle (CRUD, parameter extraction, test execution, status state machine).

---

## 2. Data Source Management

### 2.1 Supported Types

| Type | Driver | Connection Pool | URL Pattern |
|------|--------|----------------|-------------|
| MySQL | `com.mysql.cj.jdbc.Driver` | HikariCP | `jdbc:mysql://{host}:{port}/{database}` |
| PostgreSQL | `org.postgresql.Driver` | HikariCP | `jdbc:postgresql://{host}:{port}/{database}` |
| Elasticsearch | `co.elastic.clients.transport.RestClientTransport` | Apache HttpClient 5 | `http://{host}:{port}` |
| MongoDB | `com.mongodb.client.MongoClient` | Lazy driver connection (`MongoClients.create`, no pool) | `mongodb://{host}:{port}` or full `mongodb://` / `mongodb+srv://` string passthrough |

The type registry follows an extensible pattern: adding a new data source type requires implementing a `DatasourceClientFactory` bean and registering it in the type-to-factory map. No changes to existing controllers or services are needed.

### 2.2 Features

- **CRUD operations:** Create, read, update, and delete data source records. Updates do not modify stored credentials — the password/API key field is sent as `password=__MASKED__` on read and only overwritten when a non-masked value is provided.
- **Test connection:** Validates connectivity using the provided parameters without persisting. Returns `{ connected: boolean, responseTime: number, error?: string }`.
- **Enable/Disable lifecycle:** Data sources have an `ENABLED` / `DISABLED` status. Disabled data sources cannot be selected when creating or editing interfaces, and existing interfaces referencing a disabled data source return a 400 error at test/execution time.
- **AES-256-GCM credential encryption:** Passwords and API keys are encrypted at rest using AES-256-GCM with a randomly generated IV per record. The encryption key is configured via `api-atlas.encryption.key` in `application.yml` (64 hex characters = 32 bytes).
- **Connection pool management:** Each ENABLED data source maintains a dedicated HikariCP pool (or Elasticsearch `RestClient`) with `maximumPoolSize=5`, `minimumIdle=1`, and `connectionTimeout=5000ms`. Pools are lazily initialized on first use and evicted when a data source is disabled or deleted.

### 2.3 API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/datasources` | List data sources (supports `type`, `status`, `pageNum`, `pageSize` filters) |
| `GET` | `/api/datasources/{id}` | Get data source details (password/API key masked) |
| `POST` | `/api/datasources` | Create a new data source |
| `PUT` | `/api/datasources/{id}` | Update an existing data source |
| `DELETE` | `/api/datasources/{id}` | Delete a data source and evict its connection pool |
| `PATCH` | `/api/datasources/{id}/status` | Toggle between `ENABLED` and `DISABLED` |
| `POST` | `/api/datasources/test-connection` | Test connectivity with provided parameters (no persistence) |

---

## 3. Interface Management

### 3.1 Query Types

| Query Type | Backend Handler | Parameter Binding | Use Case |
|------------|----------------|-------------------|----------|
| `SQL` | `JdbcTemplate` | `PreparedStatement` with positional `?` placeholders (auto-converted from `${param}`) | Simple read-only queries against relational databases |
| `IBATIS` | MyBatis `SqlSession` | Dynamic XML fragment with `${param}` → MyBatis `#{}` / `${}` conversion | Complex queries requiring conditional logic, joins, or subqueries |
| `ESQL` | Elasticsearch `EsClient` | Positional `?` placeholders in ES\|QL query strings | Aggregation and search queries against Elasticsearch |
| `QUERY_DSL` | Elasticsearch `EsClient` | JSON tree-walk replacement of `"{{paramName}}"` placeholders in the DSL body | Full Elasticsearch Query DSL with complex nested structures |
| `MONGO_FIND` | MongoDB driver `MongoCollection.find()` | Typed JSON tree-walk replacement of `${param}` (numbers/booleans/null → typed nodes, mixed text interpolated as string) | Read-only MongoDB find queries (filter/projection/sort) with `$skip`/`$limit` pagination and `countDocuments` total |
| `MONGO_AGG` | MongoDB driver `MongoCollection.aggregate()` | Typed JSON tree-walk replacement of `${param}` | Aggregation pipelines with appended `$skip`/`$limit` pagination and `$count` total; write stages `$out`/`$merge` rejected |

**SQL** queries accept `${paramName}` placeholders that are converted to JDBC `?` positional parameters before execution, ensuring PreparedStatement-level injection protection. **IBATIS** fragments are wrapped in a MyBatis `<select>` template and executed through `SqlSession.selectList()`. **ES|QL** uses positional `?` parameters via the Elasticsearch ES|QL client. **Query DSL** performs a JSON tree-walk replacement — every `"{{paramName}}"` string value in the parsed JSON tree is replaced with the supplied parameter value before sending to Elasticsearch. **MONGO_FIND** and **MONGO_AGG** run against the MongoDB driver: a JSON tree-walk replaces `${param}` with typed nodes (numbers, booleans, null), then `find()`/`aggregate()` executes read-only against the target collection, with pagination and total counts applied by the executor.

### 3.2 Status State Machine

```
                  ┌─────────────┐
                  │ PENDING_TEST │── (test passes) ──┐
                  └─────────────┘                    │
                        │                            ▼
                        │ (edit)              ┌──────────┐
                        └───────────────────→ │  ONLINE   │
                                              └──────────┘
                     ┌──────────┐                  │
                     │ OFFLINE  │←── (offline) ─────┘
                     └──────────┘
                        │    ↑
                        │    │ (online)
                        └────┘
```

**Transitions:**
- `PENDING_TEST` → `ONLINE`: Manual "上线" action (requires at least one successful test execution in this session)
- `ONLINE` → `OFFLINE`: Manual "下线" action
- `OFFLINE` → `ONLINE`: Direct re-online (bypasses test requirement)
- `PENDING_TEST` / `OFFLINE` → (editable): Users can edit the interface configuration
- `ONLINE`: Read-only in editor (must offline first)

**Backend enforcement:** The `PATCH /api/interfaces/{id}/status` endpoint validates transition legality server-side and rejects invalid transitions with `400 Bad Request`.

### 3.3 Features

- **CRUD operations:** Create, read, update, and delete interfaces. Creating an interface sets its initial status to `PENDING_TEST`.
- **Auto `${param}` extraction:** When the query content is typed, the system scans for `${paramName}` patterns and automatically populates the parameters table with name, type (defaults to `String`), and editable remark.
- **Test execution:** Executes the query against the configured data source with user-supplied parameter values. Returns result rows and measured response time in milliseconds. No data is mutated (all queries wrapped in read-only transaction or `SELECT`-only validation).
- **Status transitions:** Interface status follows the state machine defined in 3.2.
- **Pagination support:** Interfaces with `isPaginated=true` accept `pageNum` and `pageSize` parameters. The backend applies `LIMIT/OFFSET` (SQL) or `from/size` (Elasticsearch) automatically.

### 3.4 API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/interfaces` | List interfaces (supports `name`, `status`, `dataSourceId`, `pageNum`, `pageSize` filters) |
| `GET` | `/api/interfaces/{id}` | Get interface details including parameter definitions |
| `POST` | `/api/interfaces` | Create a new interface (status = `PENDING_TEST`) |
| `PUT` | `/api/interfaces/{id}` | Update an existing interface |
| `DELETE` | `/api/interfaces/{id}` | Delete an interface |
| `POST` | `/api/interfaces/{id}/test` | Execute the interface query with provided parameter values; returns rows and response time |
| `PATCH` | `/api/interfaces/{id}/status` | Transition interface status (`PENDING_TEST → ONLINE`, `ONLINE → OFFLINE`, `OFFLINE → ONLINE`) |

---

## 4. Data Model

### 4.1 `data_source`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | `PK, AUTO_INCREMENT` | Unique identifier |
| `name` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | Display name |
| `type` | `VARCHAR(20)` | `NOT NULL` | `MySQL`, `PostgreSQL`, or `Elasticsearch` |
| `host` | `VARCHAR(255)` | `NOT NULL` | Hostname or IP address |
| `port` | `INT` | `NOT NULL` | Port number |
| `database_name` | `VARCHAR(100)` | nullable | Database name (relational only) |
| `username` | `VARCHAR(100)` | nullable | Database username (relational only) |
| `password` | `VARCHAR(512)` | nullable | AES-256-GCM encrypted password |
| `api_key` | `VARCHAR(512)` | nullable | AES-256-GCM encrypted API key (Elasticsearch only) |
| `status` | `VARCHAR(20)` | `NOT NULL, DEFAULT 'ENABLED'` | `ENABLED` or `DISABLED` |
| `created_at` | `DATETIME` | `NOT NULL` | Creation timestamp |
| `updated_at` | `DATETIME` | `NOT NULL` | Last update timestamp |

### 4.2 `api_interface`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | `PK, AUTO_INCREMENT` | Unique identifier |
| `english_name` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | English name (used for URL slug generation) |
| `chinese_name` | `VARCHAR(100)` | `NOT NULL` | Chinese display name |
| `url_slug` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | Auto-generated from english_name |
| `method` | `VARCHAR(10)` | `NOT NULL, DEFAULT 'POST'` | HTTP method (`POST` or `GET`) |
| `data_source_id` | `BIGINT` | `FK → data_source.id, NOT NULL` | Referenced data source |
| `query_type` | `VARCHAR(20)` | `NOT NULL` | `SQL`, `IBATIS`, `ESQL`, `QUERY_DSL`, `MONGO_FIND`, or `MONGO_AGG` |
| `query_content` | `TEXT` | `NOT NULL` | Query template with `${param}` placeholders |
| `is_paginated` | `TINYINT(1)` | `NOT NULL, DEFAULT 0` | Whether pagination is enabled |
| `page_size` | `INT` | `DEFAULT 10` | Default page size (when paginated) |
| `status` | `VARCHAR(20)` | `NOT NULL` | `PENDING_TEST`, `ONLINE`, or `OFFLINE` |
| `created_at` | `DATETIME` | `NOT NULL` | Creation timestamp |
| `updated_at` | `DATETIME` | `NOT NULL` | Last update timestamp |

### 4.3 `interface_param`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | `PK, AUTO_INCREMENT` | Unique identifier |
| `interface_id` | `BIGINT` | `FK → api_interface.id, NOT NULL` | Parent interface |
| `param_name` | `VARCHAR(100)` | `NOT NULL` | Parameter name (matches `${param}` in query) |
| `java_type` | `VARCHAR(50)` | `NOT NULL, DEFAULT 'String'` | Java type for parameter binding |
| `remark` | `VARCHAR(255)` | nullable | User-facing description |
| `sort_order` | `INT` | `NOT NULL, DEFAULT 0` | Display order |

---

## 5. Response Format

All API responses follow a unified JSON structure:

| Field | Type | Description |
|-------|------|-------------|
| `code` | `int` | Business status code: `200` = success, `400` = validation error, `404` = not found, `500` = server error |
| `message` | `string` | Human-readable status message |
| `data` | `object / array` | Response payload (varies by endpoint) |
| `total` | `int` | Total record count (present only on paginated list endpoints) |
| `pageNum` | `int` | Current page number (present only on paginated list endpoints) |
| `pageSize` | `int` | Page size (present only on paginated list endpoints) |

**Example (success):**
```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "englishName": "get_users", ... },
  "total": 42,
  "pageNum": 1,
  "pageSize": 10
}
```

**Example (test endpoint):**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "rows": [ { "id": 1, "name": "Alice" }, { "id": 2, "name": "Bob" } ],
    "responseTime": 23
  }
}
```

**Example (error):**
```json
{
  "code": 400,
  "message": "Data source is disabled",
  "data": null
}
```

---

## 6. Frontend Design

### 6.1 Layout

The UI follows a Soybean Admin-inspired layout pattern:
- **Sidebar:** Fixed left navigation (240px) with route links for Data Source Management and Interface Management
- **Header:** Top bar showing current route name and user context
- **Content:** Main area to the right of the sidebar, scrollable, with consistent 16px padding

### 6.2 Technology Stack

| Layer | Technology |
|-------|-----------|
| Framework | Vue 3.5 (Composition API, `<script setup lang="ts">`) |
| UI Library | Naive UI 2.44 |
| Build tool | Vite 8 |
| Language | TypeScript 6 |
| State management | Pinia |
| HTTP client | Axios (with NMessage error interceptor) |
| Router | Vue Router 4 (hash-based) |
| Primary color | `#3B82F6` (tailwind blue-500) |

### 6.3 Routes

| Path | Component | Description |
|------|-----------|-------------|
| `/datasource` | `DatasourceList` | Data source list page |
| `/datasource/create` | `DatasourceEditor` | Create data source |
| `/datasource/edit/:id` | `DatasourceEditor` | Edit data source |
| `/interface` | `InterfaceList` | Interface list page |
| `/interface/create` | `InterfaceEditor` | Create interface |
| `/interface/edit/:id` | `InterfaceEditor` | Edit interface |
| `/interface/test/:id` | `InterfaceTest` | Test interface execution |
| `/` | (redirect) | Redirects to `/datasource` |

### 6.4 Page Specifications

**Interface List (`/interface`):**
- Data table with columns: Chinese name, English name, Data source, Method, Status (tag), Created at, Actions
- Status-aware action buttons: Edit (PENDING_TEST/OFFLINE), Test (PENDING_TEST/ONLINE), Online/Offline toggle
- Search bar with name input + status dropdown
- Pagination with page size control
- "新增接口" button in card header

**Interface Editor (`/interface/create`, `/interface/edit/:id`):**
- Three-section vertical form: General Config, Query Config, Parameter Config
- General: English name, Chinese name, HTTP method, Pagination switch + page size
- Query: Datasource selector (filtered by query type — ES vs DB vs MongoDB), Query type selector (driven by selected datasource type; MongoDB maps to Find/Aggregation), Query content textarea
- Parameter: Auto-populated table from `${param}` extraction with editable type and remark columns
- Save + Cancel buttons

**Datasource Editor (`/datasource/create`, `/datasource/edit/:id`):**
- Type selector includes `MongoDB`; selecting it flips the default MySQL port (3306) to 27017 (a user-customized port is preserved)

**Interface Test (`/interface/test/:id`):**
- Two-panel horizontal layout
- Left panel: Dynamic parameter input form (one field per extracted param), pagination inputs if enabled, Test + Reset buttons
- Right panel: Result JSON display with response time tag, Back to Edit + Submit Online buttons

---

## 7. Non-Functional Requirements

### 7.1 Thread Safety for Client Lifecycle

Data source clients (HikariCP `DataSource`, Elasticsearch `RestClient`) are cached in a `ConcurrentHashMap<Long, Object>` and protected by `synchronized` blocks during creation and eviction. Each data source operation obtains a reference from the cache before executing — if a concurrent disable/delete evicts the client mid-operation, the operation completes against the already-acquired reference and subsequent operations trigger a fresh initialization.

### 7.2 AES-256-GCM Encryption

All stored credentials (passwords, API keys) are encrypted using AES-256-GCM with the following properties:
- Key: 32 bytes (256 bits), configured via `api-atlas.encryption.key` as a 64-character hex string
- IV: 12 bytes, randomly generated per encryption operation, stored alongside the ciphertext
- Additional Authenticated Data (AAD): The data source `id` as a UTF-8 string, bound to the ciphertext to prevent record-swapping attacks
- Storage format: `Base64(iv + ciphertext)` in the database column

### 7.3 Connection Leak Prevention

- All JDBC operations use try-with-resources to guarantee `Connection`, `Statement`, and `ResultSet` closure
- HikariCP `maximumPoolSize=5` with `leakDetectionThreshold=10000ms` (warns on connections held > 10s)
- Elasticsearch `RestClient` uses a shared connection pool with `connectionMaxTotal=10` per client
- Scheduled `@Scheduled(fixedRate=300000)` task evicts clients for data sources that have been disabled for > 5 minutes

### 7.4 IBATIS Namespace Collision Avoidance

Each IBATIS fragment is assigned a UUID-based namespace (`ibatis_${uuid}`) before registration in the MyBatis `Configuration`. This prevents collisions when multiple interfaces share identical parameter names or fragment structures. The namespace mapping is ephemeral — it exists only for the duration of the single query execution and is discarded immediately after.

### 7.5 SQL Injection Prevention

- SQL-type queries use `PreparedStatement` with `?` positional parameters — `${param}` placeholders are converted to `?` and bound via `PreparedStatement.setObject()`. The SQL skeleton (after placeholder removal) is validated against a deny list of dangerous keywords (`DROP`, `ALTER`, `TRUNCATE`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`) before execution.
- IBATIS fragments are executed read-only through `SqlSession.selectList()` — no commit-capable session is ever opened.
- ES|QL parameters use the Elasticsearch official `?` parameter binding API (not string interpolation).
- Query DSL replacement operates on the parsed JSON tree, never on the raw string — no injection via crafted parameter values that break JSON structure.

---

## 8. Dependency Changes

### 8.1 Backend (`pom.xml`)

| Dependency | GroupId | ArtifactId | Version | Scope |
|-----------|---------|-----------|---------|-------|
| MyBatis Spring Boot | `org.mybatis.spring.boot` | `mybatis-spring-boot-starter` | 3.0.4 | compile |
| PageHelper | `com.github.pagehelper` | `pagehelper-spring-boot-starter` | 2.1.0 | compile |
| MySQL Connector | `com.mysql` | `mysql-connector-j` | 9.2.0 | runtime |
| PostgreSQL Driver | `org.postgresql` | `postgresql` | 42.7.5 | runtime |
| Elasticsearch Client | `co.elastic.clients` | `elasticsearch-java` | 8.17.0 | compile |
| Apache HttpClient 5 | `org.apache.httpcomponents.client5` | `httpclient5` | 5.4.2 | compile |
| Jakarta JSON | `jakarta.json` | `jakarta.json-api` | 2.1.3 | compile |
| MongoDB Driver | `org.mongodb` | `mongodb-driver-sync` | 5.8.0 (managed by Spring Boot BOM) | compile |

### 8.2 Frontend (`package.json`)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `naive-ui` | `^2.44.1` | UI component library (data tables, forms, cards, tags) |
| `pinia` | `^3.0.0` | State management for stores |
| `vue-router` | `^4.5.0` | Hash-based routing |
| `axios` | `^1.7.0` | HTTP client for backend API calls |
| `@vicons/ionicons5` | (peer of naive-ui) | Icon set for UI components |
