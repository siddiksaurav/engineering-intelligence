# F01 Repository skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A repository where the backend starts, the database is migrated, the web shell shows "backend up", and one command builds and tests the backend.

**Architecture:** One Spring Boot application in `backend/` (JPA with Hibernate, Flyway, Actuator health, a command-line job runner) against PostgreSQL 18 from Docker Compose. Tests use their own PostgreSQL through Testcontainers. A Next.js shell in `web/` calls the health endpoint from the server side.

**Tech Stack:** Temurin JDK 25.0.5+8, Spring Boot 4.1.2, Maven 3.9.16 (wrapper), PostgreSQL 18, Flyway 12.4.0, Hibernate 7.4, Jackson 3.1 (`tools.jackson`), Testcontainers 2.0.5, JUnit 6, Next.js 16.4.6, React 19.3.1, Node 24.14.1, Vitest 5.

**Spec:** `docs/features/F01-repository-skeleton/design.md` (approved 2026-11-17). Read it before any task. Section numbers below refer to it.

## Global Constraints

- Every backend command runs with `export JAVA_HOME=~/.jdks/temurin-25.0.5` and, from `backend/`, with `./mvnw` (never the system `mvn`, except to create the wrapper in Task 1).
- Every web command runs in a shell with `source ~/.nvm/nvm.sh && nvm use` (reads `.nvmrc`).
- Base package `com.dsinnovators.ei`. Maven group `com.dsinnovators`, artifact `ei`. Jar `backend/target/ei.jar`.
- Library versions come from the Spring Boot 4.1.2 parent. No version is written in the `pom.xml` for a library that Boot manages.
- JSON tree type is Jackson 3: `tools.jackson.databind.JsonNode`.
- Hibernate never changes the schema: `spring.jpa.hibernate.ddl-auto=validate`. Every table change is a Flyway migration.
- `raw_record` has no update and no delete path anywhere in the code.
- No threshold, weight or status list is a constant in code.
- Standard PostgreSQL only. No extensions.
- Secrets and local values live in `.env` at the repository root. `.env` is never committed, printed or typed.
- Test first: the failing test is written and seen to fail before its code. One commit per task. Each commit message ends with the project's `Co-Authored-By` line.
- Names in code and tables follow the master plan glossary.
- When the code must differ from this plan, the difference and its reason go into `docs/features/F01-repository-skeleton/notes.md` at once. When the difference changes what F01 does, stop and ask the user.

## Review Focus

Inputs the design implies but does not list in its section 8. Each one has a test in the task named.

1. `--job` with an empty value (`--job=`) or given twice: the known job names are printed and the exit code is 1; no job runs. (Task 6)
2. `RawRecordStore.store` with a Java `null` or blank record kind, external id or URL, or a `null` payload: `IllegalArgumentException`, nothing written. A payload that is a JSON array, string or `null` literal is stored like any other. (Task 4)
3. The same text spelled with and without a Unicode escape (`"\u00e9"` and `"é"`) is the same content and gives the same hash. (Task 3)
4. `getInt` on `2.5` or on a number too large for an `int`, and `getTextList` on a list that holds a non-text value: `SettingTypeException`. (Task 5)
5. `Quarter.parse(null)` and the constructor with a number outside 1 to 4: `IllegalArgumentException`, not a `NullPointerException`. (Task 1)

## File Structure

```
.env.example                      names and safe local defaults
.nvmrc                            24.14.1
docker-compose.yml                PostgreSQL 18 for development
CLAUDE.md                         Commands section filled (Task 8)
backend/
  pom.xml, mvnw, .mvn/
  src/main/resources/application.yml
  src/main/resources/db/migration/V1__base_tables.sql
  src/main/java/com/dsinnovators/ei/
    EiApplication.java            main; switches to job mode
    common/Quarter.java
    common/CanonicalJson.java     parse, canonical form, SHA-256
    job/Job.java, JobArguments.java, CommandLineJobArguments.java,
        JobRegistry.java, JobRunner.java
    setting/Setting.java, SettingRepository.java, SettingService.java,
            DatabaseSettingService.java, SettingNotFoundException.java,
            SettingTypeException.java
    source/DataSource.java, DataSourceRepository.java, RawRecord.java,
           RawRecordRepository.java, RawRecordStore.java, StoreResult.java,
           JpaRawRecordStore.java
    identity|evidence|judgment|analytics|scoring|review|api/package-info.java
  src/test/java/com/dsinnovators/ei/
    DatabaseTest.java             base class for tests that need the database
    SchemaTest.java, HealthEndpointTest.java
    common/QuarterTest.java, CanonicalJsonTest.java
    job/JobRunnerTest.java, EiApplicationJobModeTest.java
    setting/SettingServiceTest.java
    source/RawRecordStoreTest.java
web/
  package.json, next.config.ts, tsconfig.json, vitest.config.ts
  scripts/dev.mjs                 starts `next dev` on WEB_PORT
  lib/health.ts, lib/health.test.ts
  app/layout.tsx, app/page.tsx
docs/features/F01-repository-skeleton/notes.md
```

---

### Task 1: JDK 25, the Maven project and `Quarter`

**Files:**
- Create: `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/src/main/java/com/dsinnovators/ei/EiApplication.java`
- Create: `backend/src/main/java/com/dsinnovators/ei/common/Quarter.java`
- Create: `package-info.java` in `identity`, `evidence`, `judgment`, `analytics`, `scoring`, `review`, `api`
- Test: `backend/src/test/java/com/dsinnovators/ei/common/QuarterTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Quarter` exactly as in design section 7. A build that later tasks add dependencies to.

- [ ] **Step 1: Install Temurin 25.0.5+8 to `~/.jdks/temurin-25.0.5`**

Download into the session scratchpad from `https://api.adoptium.net/v3/binary/version/jdk-25.0.5%2B8/linux/x64/jdk/hotspot/normal/eclipse`. Take the expected SHA-256 from `binary.package.checksum` in `https://api.adoptium.net/v3/assets/release_name/eclipse/jdk-25.0.5%2B8?architecture=x64&image_type=jdk&os=linux` and check it with `sha256sum -c`. Unpack with `tar -xzf … --strip-components=1 -C ~/.jdks/temurin-25.0.5`. No sudo. Do not touch `~/.jdks/ms-21.0.10` or any system default.

Run: `~/.jdks/temurin-25.0.5/bin/java -version`
Expected: `openjdk version "25.0.5"` and `Temurin-25.0.5+8`.

- [ ] **Step 2: Create `backend/pom.xml` and the Maven wrapper**

Parent `org.springframework.boot:spring-boot-starter-parent:4.1.2`. `<java.version>25</java.version>`. `<finalName>ei</finalName>`. Dependencies in this task: `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`, and `spring-boot-starter-test` (test scope). Plugins: `spring-boot-maven-plugin`; `maven-enforcer-plugin` with `requireJavaVersion` `[25,26)` and the message `Java 25 is required. Set JAVA_HOME=~/.jdks/temurin-25.0.5`.

Wrapper: in `backend/`, `mvn -N wrapper:wrapper -Dmaven=3.9.16` (the one use of the system Maven).

Run: `cd backend && ./mvnw -v`
Expected: `Apache Maven 3.9.16` and `Java version: 25.0.5`.

- [ ] **Step 3: Write the failing test `QuarterTest`**

Plain JUnit, no Spring. Assertions:

```java
assertEquals(new Quarter(2026, 3), Quarter.parse("2026-Q3"));
assertEquals("2026-Q3", new Quarter(2026, 3).toString());
assertEquals(LocalDate.of(2026, 7, 1), Quarter.parse("2026-Q3").firstDay());
assertEquals(LocalDate.of(2026, 9, 30), Quarter.parse("2026-Q3").lastDay());
assertEquals(LocalDate.of(2026, 12, 31), Quarter.parse("2026-Q4").lastDay());
assertTrue(Quarter.parse("2026-Q3").contains(LocalDate.of(2026, 7, 1)));
assertTrue(Quarter.parse("2026-Q3").contains(LocalDate.of(2026, 9, 30)));
assertFalse(Quarter.parse("2026-Q3").contains(LocalDate.of(2026, 6, 30)));
assertFalse(Quarter.parse("2026-Q3").contains(LocalDate.of(2026, 10, 1)));
assertEquals(Quarter.parse("2026-Q4"), Quarter.of(LocalDate.of(2026, 10, 1)));
assertEquals(Quarter.parse("2025-Q4"), Quarter.parse("2026-Q1").previous());
assertEquals(Quarter.parse("2027-Q1"), Quarter.parse("2026-Q4").next());
assertTrue(Quarter.parse("2026-Q3").compareTo(Quarter.parse("2026-Q4")) < 0);
assertTrue(Quarter.parse("2026-Q4").compareTo(Quarter.parse("2027-Q1")) < 0);
// each of these throws IllegalArgumentException:
// parse(null), parse(""), parse("2026Q3"), parse("2026-q3"), parse("2026-Q5"),
// parse("2026-Q0"), parse(" 2026-Q3"), new Quarter(2026, 0), new Quarter(2026, 5)
```

- [ ] **Step 4: Run it and see it fail**

Run: `cd backend && ./mvnw test -Dtest=QuarterTest`
Expected: compilation failure, `Quarter` does not exist.

- [ ] **Step 5: Implement `Quarter`, `EiApplication` and the seven `package-info.java` files**

`Quarter`: `public record Quarter(int year, int number) implements Comparable<Quarter>` with the methods of design section 7. `EiApplication`: a plain `@SpringBootApplication` with `main`. Each `package-info.java` has one Javadoc sentence, taken from the "Does" column of the master plan section 3 table for that package.

- [ ] **Step 6: Run the test and the build**

Run: `cd backend && ./mvnw verify`
Expected: `BUILD SUCCESS`; `QuarterTest` passes; `backend/target/ei.jar` exists.

Run: `cd backend && JAVA_HOME=~/.jdks/ms-21.0.10 ./mvnw -q validate`
Expected: build failure with `Java 25 is required`.

- [ ] **Step 7: Commit**

```bash
git add backend
git commit -m "feat(F01): Maven project on Java 25 with Quarter"
```

---

### Task 2: Development database, the base tables and the health endpoint

**Files:**
- Create: `docker-compose.yml`, `.env.example`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__base_tables.sql`
- Modify: `backend/pom.xml` (dependencies)
- Test: `backend/src/test/java/com/dsinnovators/ei/DatabaseTest.java`, `SchemaTest.java`, `HealthEndpointTest.java`

**Interfaces:**
- Consumes: the build of Task 1.
- Produces:
  - `public abstract class DatabaseTest` in package `com.dsinnovators.ei` (test sources). A subclass is a `@SpringBootTest` against a `postgres:18` container with all migrations applied. One container per test run. Before each test the tables `raw_record`, `setting`, `person_identity`, `person`, `project` are emptied (`truncate … restart identity cascade`); `data_source` keeps its two rows. It offers `protected JdbcTemplate jdbc`.
  - The six tables of design section 6.
  - `GET /actuator/health`.

- [ ] **Step 1: Add the dependencies**

Main: `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime). Test: `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter`, and the Boot 4 test starter that gives an HTTP test client for MVC (`spring-boot-starter-webmvc-test`). All without versions. If an artifact id does not resolve, look it up in the Spring Boot 4.1.2 dependency list, not from memory.

- [ ] **Step 2: Write `DatabaseTest` and the failing `SchemaTest` and `HealthEndpointTest`**

`DatabaseTest`: a static `org.testcontainers.postgresql.PostgreSQLContainer` on image `postgres:18`, started once in a static initializer and bound with `@ServiceConnection`; the `@BeforeEach` cleaning described above.

`SchemaTest extends DatabaseTest`, one test per line:
- the tables `data_source`, `raw_record`, `setting`, `person`, `person_identity`, `project` exist (`information_schema.tables`);
- `select key, name, base_url from data_source order by key` returns exactly `("github","GitHub","https://github.com")` and `("openproject","OpenProject","https://community.openproject.org")`;
- two `raw_record` rows with the same data source, kind, external id and version: the second insert throws `DataIntegrityViolationException`;
- a `raw_record` row with a `data_source_id` that does not exist is rejected;
- a `setting` row with `valid_from_quarter = '2026Q3'` is rejected; `'2026-Q5'` is rejected; `'2026-Q3'` is accepted;
- two `setting` rows with the same key and quarter: the second is rejected;
- a `person` with `level = 'L4'` is rejected; with `level` null is accepted and `created_at` is filled;
- two `person_identity` rows with the same data source and external id: the second is rejected;
- `select version from flyway_schema_history` returns exactly one row, `1`.

`HealthEndpointTest extends DatabaseTest` (web environment on, any test client Boot 4.1.2 gives):
- `GET /actuator/health` answers 200 and the JSON field `status` is `UP`;
- `GET /actuator/env` answers 404.

- [ ] **Step 3: Run them and see them fail**

Run: `cd backend && ./mvnw test -Dtest='SchemaTest,HealthEndpointTest'`
Expected: failures; the tables do not exist.

- [ ] **Step 4: Write `V1__base_tables.sql`**

```sql
create table data_source (
    id       bigint generated always as identity primary key,
    key      text not null unique,
    name     text not null,
    base_url text not null
);
insert into data_source (key, name, base_url) values
    ('github', 'GitHub', 'https://github.com'),
    ('openproject', 'OpenProject', 'https://community.openproject.org');

create table raw_record (
    id             bigint generated always as identity primary key,
    data_source_id bigint not null references data_source (id),
    record_kind    text not null,
    external_id    text not null,
    version        int not null,
    url            text not null,
    content_hash   text not null,
    payload        jsonb not null,
    fetched_at     timestamptz not null,
    constraint raw_record_version_uq unique (data_source_id, record_kind, external_id, version)
);

create table setting (
    id                 bigint generated always as identity primary key,
    key                text not null,
    valid_from_quarter text not null check (valid_from_quarter ~ '^[0-9]{4}-Q[1-4]$'),
    value              jsonb not null,
    description        text not null,
    constraint setting_key_quarter_uq unique (key, valid_from_quarter)
);

create table person (
    id           bigint generated always as identity primary key,
    display_name text not null,
    level        text check (level in ('L1', 'L2', 'L3')),
    created_at   timestamptz not null default now()
);

create table person_identity (
    id             bigint generated always as identity primary key,
    person_id      bigint not null references person (id),
    data_source_id bigint not null references data_source (id),
    external_id    text not null,
    username       text,
    constraint person_identity_external_uq unique (data_source_id, external_id)
);

create table project (
    id             bigint generated always as identity primary key,
    data_source_id bigint not null references data_source (id),
    external_id    text not null,
    identifier     text not null,
    name           text not null,
    url            text not null,
    constraint project_external_uq unique (data_source_id, external_id)
);
```

- [ ] **Step 5: Write `application.yml`, `.env.example` and `docker-compose.yml`**

`application.yml` sets:
- `spring.application.name: ei`
- `spring.config.import: optional:file:.env[.properties],optional:file:../.env[.properties]` (found from the repository root and from `backend/`)
- `spring.datasource.url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}/${POSTGRES_DB:ei}`, `username: ${POSTGRES_USER:ei}`, `password: ${POSTGRES_PASSWORD:}`
- `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`
- `server.port: ${BACKEND_PORT:8080}`
- `management.endpoints.web.exposure.include: health`

`.env.example`: exactly the seven lines of design section 5.8.

`docker-compose.yml`: one service `postgres`, image `postgres:18`, environment `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` from `.env`, port `127.0.0.1:${POSTGRES_PORT}:5432`, a named volume `ei-postgres-data` mounted at `/var/lib/postgresql` (the data path of the 18 image; not `/var/lib/postgresql/data`), health check `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB`.

- [ ] **Step 6: Run the tests**

Run: `cd backend && ./mvnw verify`
Expected: `BUILD SUCCESS`; all tests of Tasks 1 and 2 pass with the Compose database not running.

- [ ] **Step 7: Check Compose by hand**

Run: `cp -n .env.example .env && docker compose up -d && docker compose ps`
Expected: service `postgres` is `healthy`, bound to `127.0.0.1:5433`.

- [ ] **Step 8: Commit**

```bash
git add docker-compose.yml .env.example backend
git commit -m "feat(F01): base tables, development database, health endpoint"
```

---

### Task 3: Canonical JSON and the content hash

**Files:**
- Create: `backend/src/main/java/com/dsinnovators/ei/common/CanonicalJson.java`
- Test: `backend/src/test/java/com/dsinnovators/ei/common/CanonicalJsonTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:

```java
public final class CanonicalJson {
    public static JsonNode parse(String json);          // keeps numbers exact: BigDecimal for decimals, BigInteger for large integers
    public static String canonicalize(JsonNode node);   // keys sorted by code point at every depth, no white space, arrays in given order
    public static String sha256Hex(JsonNode node);      // SHA-256 of canonicalize(node) as UTF-8, 64 lower-case hex characters
}
```

Connectors in F02 and F03 turn a source response into a `JsonNode` with `CanonicalJson.parse`.

- [ ] **Step 1: Write the failing test**

```java
assertEquals("{\"a\":2,\"b\":1}", canonicalize(parse("{ \"b\": 1,\n \"a\": 2 }")));
assertEquals("{\"a\":{\"x\":1,\"y\":[3,1,2]},\"z\":null}",
             canonicalize(parse("{\"z\":null,\"a\":{\"y\":[3,1,2],\"x\":1}}")));
assertEquals(sha256Hex(parse("{\"a\":1,\"b\":2}")), sha256Hex(parse("{\"b\":2,\"a\":1}")));
assertNotEquals(sha256Hex(parse("[1,2]")), sha256Hex(parse("[2,1]")));
assertNotEquals(sha256Hex(parse("{\"n\":1}")), sha256Hex(parse("{\"n\":1.0}")));
assertEquals("{\"n\":1.10}", canonicalize(parse("{\"n\":1.10}")));
assertEquals("{\"n\":12345678901234567890123}", canonicalize(parse("{\"n\":12345678901234567890123}")));
assertEquals(sha256Hex(parse("{\"s\":\"\\u00e9\"}")), sha256Hex(parse("{\"s\":\"é\"}")));
assertEquals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", sha256Hex(parse("{}")));
assertEquals("\"text\"", canonicalize(parse("\"text\"")));
assertEquals("null", canonicalize(parse("null")));
// canonicalize(null) and sha256Hex(null) throw IllegalArgumentException
```

- [ ] **Step 2: Run it and see it fail**

Run: `cd backend && ./mvnw test -Dtest=CanonicalJsonTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `CanonicalJson`**

Jackson 3 (`tools.jackson.databind.json.JsonMapper`) with big-decimal and big-integer reading on, and key sorting on output. Add `spring-boot-starter-jackson` only if Jackson 3 is not already on the class path.

- [ ] **Step 4: Run the test**

Run: `cd backend && ./mvnw test -Dtest=CanonicalJsonTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat(F01): canonical JSON and content hash"
```

---

### Task 4: `RawRecordStore`

**Files:**
- Create: `backend/src/main/java/com/dsinnovators/ei/source/DataSource.java`, `DataSourceRepository.java`, `RawRecord.java`, `RawRecordRepository.java`, `RawRecordStore.java`, `StoreResult.java`, `JpaRawRecordStore.java`
- Test: `backend/src/test/java/com/dsinnovators/ei/source/RawRecordStoreTest.java`

**Interfaces:**
- Consumes: `DatabaseTest` (Task 2); `CanonicalJson.sha256Hex(JsonNode)` and `CanonicalJson.parse(String)` (Task 3); tables `data_source`, `raw_record`.
- Produces: `RawRecordStore`, `StoreResult`, `StoreResult.Outcome` exactly as in design section 7. `RawRecord` with getters `getId()`, `getDataSource()`, `getRecordKind()`, `getExternalId()`, `getVersion()`, `getUrl()`, `getContentHash()`, `getPayload()` (a `JsonNode`), `getFetchedAt()` (an `Instant`), and no setters. `DataSource` with `getId()`, `getKey()`, `getName()`, `getBaseUrl()`. `DataSourceRepository.findByKey(String): Optional<DataSource>`.

- [ ] **Step 1: Write the failing test `RawRecordStoreTest extends DatabaseTest`**

`p(json)` stands for `CanonicalJson.parse(json)`. `GH = "github"`. One test per block:

```java
// created
var r = store.store(GH, "pull_request", "101", "https://github.com/opf/openproject/pull/101", p("{\"title\":\"A\"}"));
assertEquals(Outcome.CREATED, r.outcome());
assertEquals(1, r.record().getVersion());
assertEquals(CanonicalJson.sha256Hex(p("{\"title\":\"A\"}")), r.record().getContentHash());
assertEquals(p("{\"title\":\"A\"}"), r.record().getPayload());
// fetchedAt lies between the instants taken before and after the call

// unchanged: same payload again, and the same payload with other key order
store.store(GH, "pull_request", "102", url, p("{\"a\":1,\"b\":2}"));
var again = store.store(GH, "pull_request", "102", url, p("{\"b\":2,\"a\":1}"));
assertEquals(Outcome.UNCHANGED, again.outcome());
assertEquals(1, again.record().getVersion());
assertEquals(1, jdbc.queryForObject("select count(*) from raw_record", Integer.class));

// new version; the old row is untouched
store.store(GH, "pull_request", "103", url, p("{\"title\":\"A\"}"));
var v2 = store.store(GH, "pull_request", "103", url, p("{\"title\":\"B\"}"));
assertEquals(Outcome.NEW_VERSION, v2.outcome());
assertEquals(2, v2.record().getVersion());
// findVersions returns versions [1, 2] with titles ["A", "B"]

// A, B, A: three rows, version 3 is latest and holds A
// findLatest(...).get().getVersion() == 3, payload title "A"

// same external id under kind "review" and under data source "openproject": each starts at version 1

// findLatest for an unknown record: Optional.empty()

// findLatestByKind(GH, "pull_request") after 101 (one version), 103 (two versions) and one "review" record:
// exactly two records, 101 at version 1 and 103 at version 2

// unknown data source key "gitlab": IllegalArgumentException, count(*) stays 0

// null or blank recordKind, externalId, url; null payload: IllegalArgumentException, count(*) stays 0

// payloads p("[1,2]"), p("\"text\"") and p("null") are each stored with outcome CREATED

// immutable: inside one transaction load the record, set its field "url" to "changed" with
// ReflectionTestUtils.setField, commit. Then
// jdbc.queryForObject("select url from raw_record where id = ?", String.class, id) is the original URL
```

- [ ] **Step 2: Run it and see it fail**

Run: `cd backend && ./mvnw test -Dtest=RawRecordStoreTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the entities, repositories and `JpaRawRecordStore`**

- `RawRecord` and `DataSource` are `@Entity` and `@Immutable` (`org.hibernate.annotations.Immutable`), with a protected no-argument constructor and no setters.
- `payload` maps to the `jsonb` column with `@JdbcTypeCode(SqlTypes.JSON)`. If Hibernate 7.4 cannot map a Jackson 3 `JsonNode` this way, keep the column value as a `String` field with the same annotation and have `getPayload()` return `CanonicalJson.parse(...)` of it; write the choice into `notes.md`.
- `RawRecordRepository` and `DataSourceRepository` extend `org.springframework.data.repository.Repository`, not `CrudRepository`, so no delete method exists. `RawRecordRepository` declares `save` and the finders only.
- `key` is a keyword in JPQL. If a derived query on an attribute named `key` does not parse, name the attribute `sourceKey` with `@Column(name = "key")` and give `findByKey` an explicit `@Query`. `getKey()` stays.
- `JpaRawRecordStore` is a `@Service`; `store` is `@Transactional` and follows design 5.3 steps 1 to 5. `fetched_at` is `Instant.now()`.
- `RawRecordStore` is the only bean that writes raw records. `RawRecordRepository` is package-private.

- [ ] **Step 4: Run the tests**

Run: `cd backend && ./mvnw verify`
Expected: `BUILD SUCCESS`. The application context starts, so `ddl-auto=validate` accepts both entities.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat(F01): RawRecordStore with versions and no update path"
```

---

### Task 5: `SettingService`

**Files:**
- Create: `backend/src/main/java/com/dsinnovators/ei/setting/Setting.java`, `SettingRepository.java`, `SettingService.java`, `DatabaseSettingService.java`, `SettingNotFoundException.java`, `SettingTypeException.java`
- Test: `backend/src/test/java/com/dsinnovators/ei/setting/SettingServiceTest.java`

**Interfaces:**
- Consumes: `DatabaseTest` (Task 2); `Quarter` (Task 1); table `setting`.
- Produces: `SettingService` exactly as in design section 7. `SettingNotFoundException` and `SettingTypeException` are unchecked; the first has `getKey()` and `getQuarter()`, the second `getKey()`; each message contains the key.

- [ ] **Step 1: Write the failing test `SettingServiceTest extends DatabaseTest`**

Rows are inserted with `jdbc.update("insert into setting (key, valid_from_quarter, value, description) values (?, ?, ?::jsonb, ?)", …)`. `q(text)` stands for `Quarter.parse(text)`.

```java
// in force until replaced
insert("presence.threshold", "2026-Q3", "0.3333");
insert("presence.threshold", "2027-Q1", "0.5");
assertEquals(new BigDecimal("0.3333"), settings.getDecimal("presence.threshold", q("2026-Q3")));
assertEquals(new BigDecimal("0.3333"), settings.getDecimal("presence.threshold", q("2026-Q4")));
assertEquals(new BigDecimal("0.5"),    settings.getDecimal("presence.threshold", q("2027-Q1")));
assertEquals(new BigDecimal("0.5"),    settings.getDecimal("presence.threshold", q("2028-Q2")));
var e = assertThrows(SettingNotFoundException.class, () -> settings.getDecimal("presence.threshold", q("2026-Q2")));
assertEquals("presence.threshold", e.getKey());
assertEquals(q("2026-Q2"), e.getQuarter());

// unknown key
assertThrows(SettingNotFoundException.class, () -> settings.getInt("no.such.key", q("2026-Q3")));

// kinds
insert("t3.items", "2026-Q3", "2");
insert("label", "2026-Q3", "\"gate unverified\"");
insert("done.statuses", "2026-Q3", "[\"merged\",\"tested\",\"closed\"]");
insert("weights", "2026-Q3", "{\"T1\":1,\"T2\":2,\"T3\":4}");
assertEquals(2, settings.getInt("t3.items", q("2026-Q3")));
assertEquals(new BigDecimal("2"), settings.getDecimal("t3.items", q("2026-Q3")));
assertEquals("gate unverified", settings.getText("label", q("2026-Q3")));
assertEquals(List.of("merged", "tested", "closed"), settings.getTextList("done.statuses", q("2026-Q3")));
assertEquals(4, settings.getJson("weights", q("2026-Q3")).get("T3").intValue());

// wrong kind: each throws SettingTypeException whose getKey() is the key
// getInt("done.statuses"), getInt("label"), getDecimal("label"), getText("t3.items"),
// getTextList("label"), getTextList on value [1,"a"], getInt on value 2.5, getInt on value 99999999999

// no cache
insert("gate.pass", "2026-Q3", "10");
assertEquals(10, settings.getInt("gate.pass", q("2026-Q3")));
jdbc.update("update setting set value = '12'::jsonb where key = 'gate.pass'");
assertEquals(12, settings.getInt("gate.pass", q("2026-Q3")));
```

- [ ] **Step 2: Run it and see it fail**

Run: `cd backend && ./mvnw test -Dtest=SettingServiceTest`
Expected: compilation failure.

- [ ] **Step 3: Implement**

- `Setting` is an `@Entity` (not immutable). `value` maps to `jsonb` the same way as `RawRecord.payload` in Task 4. `key` and `value` are keywords in JPQL: name the attributes `settingKey` and `settingValue` with `@Column(name = …)`.
- `SettingRepository` extends `Repository` with one method: the row for a key with the greatest `valid_from_quarter` that is less than or equal to the given text (`order by … desc`, first row). The text form `YYYY-Qn` sorts in time order.
- `DatabaseSettingService` is the `@Service` that implements `SettingService`. Every call reads the database. `getInt` accepts only a whole number that fits an `int`.

- [ ] **Step 4: Run the tests**

Run: `cd backend && ./mvnw verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat(F01): SettingService with a value per quarter"
```

---

### Task 6: Job runner

**Files:**
- Create: `backend/src/main/java/com/dsinnovators/ei/job/Job.java`, `JobArguments.java`, `CommandLineJobArguments.java`, `JobRegistry.java`, `JobRunner.java`
- Modify: `backend/src/main/java/com/dsinnovators/ei/EiApplication.java`
- Test: `backend/src/test/java/com/dsinnovators/ei/job/JobRunnerTest.java`, `EiApplicationJobModeTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `Job` and `JobArguments` exactly as in design section 7, and:

```java
public class JobRegistry {                       // a Spring bean
    public JobRegistry(List<Job> jobs);          // IllegalStateException naming the name when two jobs share one
    public Optional<Job> find(String name);
    public List<String> names();                 // sorted
}
public class JobRunner {                         // a Spring bean
    public JobRunner(JobRegistry registry);
    public int run(String... args);              // 0 when the job returns, 1 in every other case
}
public class EiApplication {
    static boolean isJobMode(String[] args);     // true when an argument starts with "--job"
}
```

A later feature adds a job by declaring a `@Component` that implements `Job`.

- [ ] **Step 1: Write the failing tests**

`JobRunnerTest` is plain JUnit with `OutputCaptureExtension`; jobs are small classes in the test, among them `RecordingJob("echo")`, which keeps the `JobArguments` it was given, and `FailingJob("boom")`, which throws `new IllegalStateException("source unreachable")`.

```java
// runs the named job with its arguments
assertEquals(0, runner.run("--job=echo", "--from=2026-07-01", "--dry-run"));
assertEquals(Optional.of("2026-07-01"), echo.received.get("from"));
assertEquals("2026-07-01", echo.received.require("from"));
assertEquals(Optional.empty(), echo.received.get("to"));
assertEquals(Optional.of(""), echo.received.get("dry-run"));
assertEquals(Optional.empty(), echo.received.get("job"));      // --job is not passed on

// unknown name: exit 1, output contains "nope" and the known names "boom" and "echo"
assertEquals(1, runner.run("--job=nope"));

// empty name and a name given twice: exit 1, output lists the known names, no job ran
assertEquals(1, runner.run("--job="));
assertEquals(1, runner.run("--job=echo", "--job=boom"));

// a job that throws: exit 1, output contains "boom" and "source unreachable"
assertEquals(1, runner.run("--job=boom"));

// a missing required argument: a job that calls arguments.require("from") and is run without it:
// exit 1, output contains "from"

// two jobs named "echo": new JobRegistry(List.of(a, b)) throws IllegalStateException containing "echo"

// names() of a registry with "echo" and "boom" is ["boom", "echo"]
```

`EiApplicationJobModeTest`: `isJobMode` is true for `{"--job=x"}` and `{"--a=b", "--job=x"}`, false for `{}` and `{"--server.port=9000"}`.

- [ ] **Step 2: Run them and see them fail**

Run: `cd backend && ./mvnw test -Dtest='JobRunnerTest,EiApplicationJobModeTest'`
Expected: compilation failure.

- [ ] **Step 3: Implement**

- `CommandLineJobArguments` reads `--key=value` and `--key` (value `""`) arguments; `job` is left out.
- `JobRunner.run` logs the job's start and end at INFO, and a failure at ERROR with the message.
- `EiApplication.main`: in job mode the application starts with `WebApplicationType.NONE`, calls `JobRunner.run(args)`, closes the context and ends with `System.exit(code)`. Otherwise it starts as the web server and runs no job. Flyway runs in both modes because it is part of the context start.

- [ ] **Step 4: Run the tests**

Run: `cd backend && ./mvnw verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Check job mode by hand**

With the Compose database up, from the repository root:

Run: `java -jar backend/target/ei.jar --job=nope; echo "exit=$?"`
Expected: a line that says no job named `nope` exists and that no jobs are known; `exit=1`; no line that says a web server started on a port.

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "feat(F01): command-line job runner"
```

---

### Task 7: Web shell

**Files:**
- Create: `.nvmrc`
- Create: `web/` (Next.js application), `web/scripts/dev.mjs`, `web/vitest.config.ts`
- Create: `web/lib/health.ts`, `web/app/page.tsx` (replaces the generated page), `web/app/layout.tsx`
- Test: `web/lib/health.test.ts`

**Interfaces:**
- Consumes: `GET {BACKEND_URL}/actuator/health` answering `{"status":"UP"}` (Task 2); `BACKEND_URL` and `WEB_PORT` from the root `.env`.
- Produces:

```ts
export type BackendStatus = "up" | "down";
export async function checkBackend(
  baseUrl: string | undefined,
  fetchFn: typeof fetch = fetch,
  timeoutMs: number = 2000,
): Promise<BackendStatus>;
```

- [ ] **Step 1: Create the application**

`.nvmrc` holds `24.14.1`. Create `web/` with `create-next-app@16.4.6`, without prompts: TypeScript, App Router, ESLint, npm, no Tailwind, no `src/` directory, no nested git repository. Pin `next` to `16.4.6` and `react`, `react-dom` to `19.3.1` (exact, no `^`). Add `vitest` as a dev dependency and the script `"test": "vitest run"`. Remove the generated demo content and images.

- [ ] **Step 2: Write the failing test `web/lib/health.test.ts`**

`fetchFn` is a stub in every test.

```ts
// answers 200 with {"status":"UP"}                    -> "up"
// called with exactly "http://localhost:8080/actuator/health" for baseUrl "http://localhost:8080"
// baseUrl "http://localhost:8080/" (trailing slash)    -> same URL, no double slash
// answers 200 with {"status":"DOWN"}                  -> "down"
// answers 503 with {"status":"DOWN"}                  -> "down"
// answers 200 with the text "<html>" (not JSON)       -> "down"
// fetchFn rejects with new TypeError("fetch failed")  -> "down"
// fetchFn never resolves, timeoutMs 50                -> "down" (and the request's AbortSignal is aborted)
// baseUrl undefined or ""                             -> "down", fetchFn is not called
```

- [ ] **Step 3: Run it and see it fail**

Run: `cd web && npm test`
Expected: FAIL, `checkBackend` is not defined.

- [ ] **Step 4: Implement `checkBackend`, the page and the dev script**

- `checkBackend` never throws. It passes `cache: "no-store"` and an `AbortSignal` to `fetchFn`.
- `app/page.tsx` is a server component with `export const dynamic = "force-dynamic"`. It renders `<h1>Engineering Intelligence</h1>` and one `<p>` whose text is exactly `backend up` or `backend down`, from `checkBackend(process.env.BACKEND_URL)`.
- `app/layout.tsx`: title `Engineering Intelligence`, no external fonts.
- `scripts/dev.mjs` starts `next dev -p ${WEB_PORT or 3000}`. `package.json`: `"dev": "node --env-file-if-exists=../.env scripts/dev.mjs"`, so the root `.env` is loaded and a value already in the environment wins.

- [ ] **Step 5: Run the tests, the linter and the build**

Run: `cd web && npm test && npm run lint && npm run build`
Expected: all tests pass; no lint errors; the build succeeds.

- [ ] **Step 6: Check in the browser path by hand**

With the database and the backend running (`cd backend && ./mvnw spring-boot:run`):

Run: `cd web && npm run dev`, then `curl -s localhost:3000`
Expected: the HTML contains `backend up`. Stop the backend, run `curl -s localhost:3000` again: it contains `backend down`.

- [ ] **Step 7: Commit**

```bash
git add .nvmrc web
git commit -m "feat(F01): web shell that shows whether the backend is up"
```

Check before committing that `git status` shows no `node_modules/` and no `.next/`.

---

### Task 8: Verification and documents

**Files:**
- Create: `docs/features/F01-repository-skeleton/notes.md`
- Modify: `CLAUDE.md` (section "Commands")
- Modify: `docs/master-plan.md` (section 3 package table; section 7 status)
- Modify: `docs/progress.md`, `docs/decisions.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the commands a later session starts from.

- [ ] **Step 1: Run the checks of design section 10 from a clean state**

Stop the backend and the web app. `docker compose down` (keep the volume). Then, in this order, and paste each command with its output into `notes.md`:

1. `cd backend && ./mvnw verify` with the Compose database stopped. Expected: `BUILD SUCCESS`, zero failures, `target/ei.jar` exists.
2. `docker compose up -d && docker compose ps`. Expected: `postgres` healthy on `127.0.0.1:5433`.
3. Start the backend; `curl -s localhost:8080/actuator/health`. Expected: `{"status":"UP"}` (further fields are allowed only if the status is `UP`).
4. Stop the backend and start it again. Expected: the second start logs that the schema is up to date and no migration is necessary. `docker compose exec postgres psql -U ei -d ei -c "select version, success from flyway_schema_history"` shows one row, version `1`.
5. `docker compose exec postgres psql -U ei -d ei -c "select key from data_source order by key"`. Expected: `github`, `openproject`.
6. Start the web app; `curl -s localhost:3000`. Expected: contains `backend up`. Stop the backend; expected: contains `backend down`.
7. `java -jar backend/target/ei.jar --job=nope; echo "exit=$?"`. Expected: `exit=1`.
8. `git status --short`. Expected: no `.env`, no `target/`, no `node_modules/`, no `.next/`.

A check that fails is fixed with a failing test first, in the task that owns the code, and this step is run again from the start.

- [ ] **Step 2: Fill the "Commands" section of `CLAUDE.md`**

Replace the paragraph that begins "No code exists yet" with the command block of design section 9, the `JAVA_HOME` line, the nvm line, and one line that says tests need Docker running but not the Compose database. Write only commands that Step 1 ran.

- [ ] **Step 3: Update the master plan**

In the section 3 table add three rows: `common/` (`Quarter`, canonical JSON and hash), `job/` (the `Job` interface and the command-line runner), `setting/` (reading a setting for a quarter; writes `setting`). Set F01 to `done` in section 7 and fill the Plan column.

- [ ] **Step 4: Write `notes.md`, the progress entry and any new decision**

`notes.md`: the evidence of Step 1; every difference from this plan with its reason (among them how `jsonb` was mapped and whether `key` needed renaming); anything F02 should know. `docs/progress.md`: a new top entry with what was done and "Next: F02 GitHub connector, deep dive", and the needs "read-only GitHub token". `docs/decisions.md`: a row for each decision made while building.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md docs
git commit -m "docs(F01): commands, verification evidence, F01 done"
```
