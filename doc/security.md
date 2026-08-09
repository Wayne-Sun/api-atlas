# API Atlas — Security Documentation

This file documents the security model, threat mitigations, and residual risks of
the API Atlas scaffold. It is extended incrementally by the security-remediation
plan (`.omo/plans/security-remediation-f1-f10.md`); later waves will add the
credential-leak advisory, key-rotation runbook, and CI secret-scanning guidance.

## Query Execution Security

### Parameter substitution model

All query executors replace `${paramName}` placeholders with caller-supplied
values. The SQL, ES|QL and Mongo executors are **parameterized by design**:

- **SQL** (`DatabaseQueryExecutor.executeSql`): `${param}` → JDBC `?` positional
  placeholders bound via `PreparedStatement`. Injection strings are transmitted
  as data, never interpreted as SQL — locked by
  `executeSql_InjectionParam_BindsLiteralStringAndNoDdl`.
- **ES|QL** (`ElasticsearchQueryExecutor.executeEsql`): `${param}` → `?`
  positional markers, with the values passed through the SQL API `params` body —
  locked by `executeEsql_InjectionParam_FlowsThroughParamsNotInlinedInQuery`.
- **MONGO_FIND / MONGO_AGG** (`MongoQueryExecutor`): `${param}` → typed BSON
  values via a tree-walk substitution. Aggregate pipelines containing write
  stages (`$out` / `$merge`) are rejected with a read-only error — locked by
  `executeAggregate_WriteStage_ThrowsIllegalArgumentException`.

### IBATIS `${}` is dynamic SQL by design (admin-trusted)

The **IBATIS** executor (`DatabaseQueryExecutor.executeIbatis`) wraps
`queryContent` in a real MyBatis XML mapper, so `${param}` is interpreted as
**MyBatis dynamic SQL** — the substitution is inlined into the statement before
parsing, not bound as a literal. A parameter value containing SQL fragments
therefore becomes part of the query.

This is **by design and now admin-trusted**: since the interface query-execution
gate landed (todo 8 of the security-remediation plan), `POST
/api/interfaces/{id}/test` is restricted to `ADMIN`
(`@PreAuthorize("hasRole('ADMIN')")` on `InterfaceController.test`). Only
administrators can author the `queryContent` and supply the values inlined by
`${...}`; any interface whose query content uses `${...}` must be reviewed as
executable SQL.

Additional IBATIS guards: a hard in-memory row cap
(`atlas.executor.ibatis.max-memory-rows`, enforced fetch-time so the full result
set is never materialized) and a statement timeout
(`atlas.executor.query-timeout-seconds`).

## SSRF protection for outbound datasource connections

Outbound connections created for datasources are guarded against Server-Side
Request Forgery by `HostSecurityValidator`
(`backend/src/main/java/com/api/atlas/config/HostSecurityValidator.java`). The
validator is applied on every client-creation path — `DataSourceController`
`POST /api/datasources/test-connection`, and `DataSourceClientManager`
(`enableDataSource` / `getDataSource` / `getMongoClient` / `createMongoClient`).

The check rejects a host when **any** resolved address is:

- loopback (`127.0.0.0/8`, `::1`) or the `localhost` name,
- private / site-local (RFC 1918 `10/8`, `172.16/12`, `192.168/16`, IPv6 ULA
  `fc00::/7`),
- link-local (`169.254.0.0/16`, `fe80::/10` — covers cloud metadata endpoints
  such as `169.254.169.254`),
- unspecified (`0.0.0.0`, `::`), or
- multicast.

Blank hosts and hosts that fail DNS resolution are treated as blocked so
resolution details are never leaked to callers. A blocked host surfaces as the
generic `connected:false` / `error:"Host not allowed"` body on
`test-connection`, and as `IllegalArgumentException("Host not allowed")` (400) on
the client-creation paths.

The whole check can be bypassed with
`atlas.security.allow-private-hosts: true` (default `false`) for operators that
legitimately run this against in-house or local databases.

### Documented residual: DNS rebinding (admin-trusted)

Host validation resolves the hostname at check time; a client factory connecting
to the same hostname performs its **own** DNS resolution later, so a hostname
that passes validation could in principle rebind to a private address between
the check and the connect. Closing this would require resolve-and-pin
(connecting to the validated IP rather than the hostname), which would change
the connect semantics of all three client factories and break hostname-based
features (TLS SNI, virtual hosting, certificate validation).

This is accepted as an **admin-trusted residual**: every path that creates a
client — `test-connection` and all client-creation code — is restricted to
`ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`, todo 7/8 of the
security-remediation plan). Only a compromised administrator account can supply
a rebinding hostname, and that same account already holds the plaintext
datasource credentials. The plan deliberately does **not** add resolve-and-pin.

## Credential leak advisory

On **2026-07-29**, commit `41da0e1` (feat: integrate auth/security
infrastructure, audit interceptor, user management, RBAC, and Redis token
revocation) tracked `backend/src/main/resources/application-local.yml`. That
file carried real secrets: the AES-256 key used for datasource-credential
encryption, the RSA-2048 JWT signing keypair, and a local MySQL password. The
file remained tracked through the public history — commits `41da0e1` through
`4e81b60` (the current public head) all carry the **old** keys. This old-key
leak is a documented residual: the repository is public, the leak is already
in history, and rewriting it cannot un-leak the keys, so it is kept and
documented here.

The **new** keys (the rotated AES key and the rotated RSA keypair) were written
into the still-tracked file by the plan commits for todos 1–2 (originally
`485baf0` and `bd0f457`), but those commits were **never pushed**. On
**2026-08-09**, before any push, the file was removed from the entire unpushed
plan history with `git filter-branch` (`--index-filter git rm --cached` over
`485baf0^..HEAD`), and the now-unreachable secret blobs were purged with
`git reflog expire --all` + `git gc --prune=now --aggressive`. The new keys
therefore **never exist in any git history** — they live only in the untracked
on-disk `application-local.yml`.

**Impact and response.** Anyone with read access to the repository history held
the old AES key (able to decrypt every datasource password encrypted before the
rotation) and the old JWT private key (able to forge administrator tokens until
the rotation). Both were rotated:

- AES key rotated on **2026-08-07** in commit `5a3766f`; all existing
  `data_source.password` rows were re-encrypted with the new key by the
  profile-gated re-encryption runner.
- JWT RSA keypair rotated on **2026-08-08** in commit `036a114`; the new public
  key rejects every token signed with the old private key.
- All existing token sessions were invalidated (todo 2) by `RedisTokenService
  .revokeAll()` and a live `redis-cli` flush of the `token:*` keyspace, so a
  stolen old-key token is rejected by the Redis jti check even before the
  signature check runs.

**Why the history is kept.** The repository is public and the old-key leak is
already in history. Force-pushing a rewritten history cannot un-leak those
keys, breaks every existing clone, and can be reconstructed from any fork. The
remediation is key rotation, not history rewriting — except for the **new**
keys, which were scrubbed from the unpushed plan history (see above) so they
were never exposed. Since commit `3ed8c97` (2026-08-08),
`application-local.yml` is untracked and holds only the new keys; it is covered
by `.gitignore` (`application-local.yml` entry) and never committed again.

**Remaining action for operators.** Anyone who has used this scaffold in the
past should assume their stored datasource credentials are exposed and rotate
them, then follow the runbook below to rotate the application keys.

## Key-rotation runbook

All commands are verified against the current implementation.

### Rotating the AES encryption key

1. Generate a new 32-byte Base64 key:

   ```bash
   openssl rand -base64 32
   ```

   The key must decode to exactly 32 bytes; `EncryptionConfig.validateKey()`
   rejects placeholders, malformed Base64, and any other length with
   `IllegalStateException` at startup.

2. Edit the untracked `backend/src/main/resources/application-local.yml`. Set
   the new key as the active key and the current (old) key as the old key:

   ```yaml
   atlas:
     encryption:
       secret-key: <NEW_BASE64_32_BYTE_KEY>
       old-secret-key: <CURRENT_KEY_BEING_REPLACED>
   ```

   The property name for the old key is `atlas.encryption.old-secret-key`
   (constructor `@Value` in `ReEncryptRunner`, default empty; it must be set or
   the runner fails fast before touching any row).

3. Run the re-encryption runner with **both** profiles. `rotate` activates
   `ReEncryptRunner` (`@Profile("rotate")`); `local` is required so the
   untracked `application-local.yml` (DB password, new key, old key) is loaded.
   `rotate` alone would not load that file and the runner could not reach the
   database or would miss the keys:

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=rotate,local mvn spring-boot:run
   ```

   The runner re-encrypts every non-blank `data_source.password` from the old
   key to the new key and logs `Re-encrypted N rows`. It is idempotent (rows
   already encrypted with the new key are skipped) and fail-closed: if any row
   cannot be decrypted with the old key the whole transaction rolls back and an
   `IllegalStateException` is thrown, so no row is ever stranded encrypted with
   the old key after you delete it.

4. Verify the output, then remove the old key from `application-local.yml`
   (`atlas.encryption.old-secret-key`). Any row that still failed would have
   aborted the run, so a successful run means every stored password now
   decrypts with the new key.

### Rotating the JWT RSA keypair

1. Generate a fresh RSA-2048 keypair and extract the public key:

   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt_private.pem
   openssl pkey -in jwt_private.pem -pubout -out jwt_public.pem
   ```

2. Replace the PEM strings in the untracked `application-local.yml`:

   ```yaml
   atlas:
     jwt:
       private-key: |
         -----BEGIN PRIVATE KEY-----
         ...
       public-key: |
         -----BEGIN PUBLIC KEY-----
         ...
   ```

   `RsaKeyConfig.validateKey()` rejects placeholders, PEMs shorter than 200
   characters, and PEMs missing `-----BEGIN` at startup, so a malformed key will
   never boot.

3. Revoke every existing session. The Redis key prefix is `token:` (the
   `KEY_PREFIX` constant in `RedisTokenService`, keys of the form
   `token:{jti}`), so this command removes the whole keyspace:

   ```bash
   redis-cli --scan --pattern 'token:*' | xargs -r redis-cli del
   ```

   This matches the keys-based `RedisTokenService.revokeAll()` implementation
   (`keys("token:*")` + batch `delete`) and can be run from the CLI when you
   want to avoid code changes. Tokens issued before the rotation are rejected
   with 401 (`Signed JWT rejected: Invalid signature`); tokens that were still
   in Redis are additionally gone, so the filter's jti check fails first.

4. Old tokens that were never stored in Redis are rejected purely by the
   signature check against the new public key. No other action is needed; new
   logins mint tokens with the new key.

## Security configuration reference

| Property | Default | Effect |
|----------|---------|--------|
| `atlas.security.redis-fail-closed` | `true` | When Redis is unavailable during token validation, `TokenValidationFilter` clears the security context and returns **503** `{"code":503,"msg":"Token service unavailable"}` instead of letting the request through. Set to `false` to restore the degraded fail-open mode (WARN log + pass-through), useful while operating without a token store. |
| `atlas.security.allow-private-hosts` | `false` | Bypasses the SSRF guard (`HostSecurityValidator`) for outbound datasource connections, allowing loopback, private, link-local, and other blocked hosts. Intended for operators that legitimately connect to in-house or local databases. |
| `atlas.executor.query-timeout-seconds` | `30` | Statement timeout applied to JDBC queries (`JdbcTemplate.setQueryTimeout`) and to the IBATIS executor (`SqlSessionFactory` `defaultStatementTimeout`). Long-running queries surface a typed error instead of hanging. |
| `atlas.executor.ibatis.max-memory-rows` | `100000` | Hard cap on rows the IBATIS executor materializes. Enforced fetch-time: the `ResultHandler` aborts mid-iteration once the cap is reached and the limit `IllegalArgumentException` is unwrapped from MyBatis's `PersistenceException`, so the full result set is never loaded into memory. |

## Admin-only write policy

All datasource and interface **mutations** and the interface **query-test**
endpoint are restricted to `ADMIN`:

- `DataSourceController`: `create`, `update`, `delete`, `updateStatus`, and
  `POST /datasources/test-connection` carry
  `@PreAuthorize("hasRole('ADMIN')")`; `list` and `getById` remain available to
  any authenticated user.
- `InterfaceController`: `create`, `update`, `delete`, `POST /interfaces/{id}/test`,
  and `updateStatus` carry `@PreAuthorize("hasRole('ADMIN')")`; `list` and
  `getById` remain authenticated-only.

A non-admin caller receives HTTP 403 with the R envelope body
(`{"code":403,"message":"Access denied"}`) via `GlobalExceptionHandler`. The
frontend mirrors this: the router guards mutation routes
(`/datasource/create`, `/datasource/edit/:id`, `/interface/create`,
`/interface/edit/:id`, `/interface/test/:id`) and the data tables/forms hide
mutation controls behind `authStore.isAdmin`.

This gate is what makes the IBATIS dynamic-SQL model and the SSRF residual
admin-trusted: only an administrator can author interface query content, run a
query test, create a datasource, or test a connection.

## SSRF host allowlist behavior and DNS-rebinding residual

See the "SSRF protection for outbound datasource connections" section above
for the full host-validation rules, the generic blocked-host responses, and
the `atlas.security.allow-private-hosts` escape hatch. The documented
**DNS-rebinding residual** (a hostname can pass validation and later rebind to
a private address before the factory connects) is accepted as **admin-trusted**
for the same reason as the write policy above: every path that creates a
client is `ADMIN`-only, and a compromised admin already holds the plaintext
credentials. The plan deliberately does not add resolve-and-pin.

## CI secret-scanning recommendation

Run secret scanning in CI to catch future commits like `41da0e1`. Two
recommended tools, both documented here but **not installed** in this
repository (no new dependencies were added by the security plan):

```bash
# Gitleaks: scans the working tree and the git history for secrets
gitleaks detect --source . --redact

# TruffleHog: scans the git history, verifying only credentials that are
# still live (GitHub tokens, AWS keys, etc.)
trufflehog git file://. --only-verified
```

Add one of these as a CI job (or a pre-push hook) so any future tracked file
containing a key, a PEM block, or a credential-like string fails the build.
The untracked `application-local.yml` is the only file that should ever carry
real keys.

## Documented residuals (future work)

These are known weaknesses that this plan deliberately does **not** fix.

1. **Elasticsearch `apiKey` is stored plaintext at rest.** The
   `data_source.api_key` column (VARCHAR(500), `COMMENT 'ES API key'`) holds the
   ES API key in plaintext. Todo 9 fixed the **response** leak: no API response
   returns `password` or `apiKey` anymore, and `getById` nulls both fields. The
   at-rest column is **not** encrypted; encrypting it (and decrypting it only
   inside the ES client factory) is future work.

2. **Login error responses are unified, but a timing side-channel remains.**
   Todo 6 made all three failure branches (unknown user, disabled user, wrong
   password) return the identical `401 {"code":401,"message":"Invalid username
   or password"}` body, preventing account-status enumeration by response
   content. However the unknown/disabled-user path returns before BCrypt runs,
   while the wrong-password path executes a BCrypt comparison, so a remote
   attacker could still distinguish the cases by response time. Future work: run
   a dummy BCrypt compare on the unknown/disabled paths so every login failure
   takes the same time.

