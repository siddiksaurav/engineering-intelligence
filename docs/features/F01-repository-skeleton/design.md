# F01 Repository skeleton: design

State: approved on 2026-11-17

## 1. Goal

After F01 the repository holds a backend that starts, a database that is migrated, a web shell that shows "backend up", and one command that builds and tests the backend. Every later feature has a place for its code, a table to write raw records to, a way to read a setting, a way to run a job, and a test setup. Nobody outside the build sees anything yet; the first visible result is F05.

## 2. Scope

**In.**
- JDK 25 on the build machine.
- `backend/`: Spring Boot application, Maven wrapper, package layout, health endpoint, job runner, test setup.
- Migration `V1__base_tables.sql`: `data_source` (with two seeded rows), `raw_record`, `person`, `person_identity`, `project`, `setting`.
- Java code for `data_source`, `raw_record` and `setting`, and the `Quarter` type.
- `docker-compose.yml` with PostgreSQL.
- `web/`: Next.js shell with one page that shows whether the backend is up.
- `.env.example`, `.nvmrc`, and the commands in `CLAUDE.md`.

**Out.**
- Java classes for `person`, `person_identity` and `project`. F04 and F03 write them when they first use the tables.
- Any real job. F02 writes the first one.
- Any real row in `setting`. F03 adds the done statuses, F11 the scoring values.
- A REST endpoint that starts a job. A later feature adds it if it needs one.
- A screen or endpoint to edit settings.
- CORS, authentication, a scheduler, a production deployment.
- The tables of later features (`evidence_item` in F05, `judgment` in F06, `human_label` in F08, the scoring tables in F11).

## 3. Rules from the spec and the PoC plan

**Master plan, "Rules that never change" (the ones F01 touches).**
- `raw_record` is an unchanged copy of the source. It is never edited.
- No number from the spec is hard-coded. Thresholds, weights and done statuses are rows in `setting` (spec 14).
- Schema changes only through Flyway migrations.
- Secrets live in a local `.env` that is not committed. `.env.example` lists the names.
- Jobs are started by a REST endpoint or a command-line runner. There is no scheduler.
- Test first. One commit per task.

**Spec 14, Settings (opening paragraph, in full).** "No number in this spec is hard-coded. Each one is a setting. The EM or the Director sets the values. Every setting has a version per quarter, so an old score can always be explained with the values that were in force."

**Spec 5, "What happens to each record" (the rule F01 prepares for).** "Every item keeps a link back to its source record, so a person can always open the original." This is why `raw_record` has a `url` column.

**`poc-plan.md` 3.4, the rows F01 creates.**

| Table | Purpose |
| --- | --- |
| `data_source` | GitHub, OpenProject. Later: CI/CD and others |
| `raw_record` | Unchanged copy of each pulled record (PR, review comment, work item, activity) as JSONB, with external id, URL, content hash. Never edited |
| `person`, `person_identity` | One person, many outside identities (GitHub login, OpenProject user id). `level` is nullable |
| `project` | OpenProject project |
| `setting` | Presence threshold, weights, done statuses, gate limits, with a version per quarter (spec 14) |

**`poc-plan.md` 3.5, package layout.** One Spring Boot application with packages that match the spec's steps: `source`, `evidence`, `judgment`, `analytics`, `scoring`, `review`, `api`. The master plan section 3 adds `identity`.

**Where the PoC differs from the spec.** The spec has no raw record; it turns each pulled record straight into an evidence item. The PoC keeps the unchanged copy first, so that evidence items can be rebuilt and a judgment's input can be reproduced.

## 4. What the real data looks like

F01 reads no source data. The facts it rests on are about the build machine and about released versions.

**The build machine, checked 17 Nov 2026 with `java -version`, `mvn -version`, `docker --version`, `ss -ltn`, `ls ~/.nvm/versions/node`.**
- Ubuntu 24.04. OpenJDK 21.0.10 only, in `~/.jdks/ms-21.0.10`. No JDK 25.
- Maven 3.8.7 from the system package.
- Docker 29.8.2 and Docker Compose v5.6.0.
- Node v24.14.1 under nvm. `node` is not on the default `PATH`; a shell must load nvm first.
- Host port 5432 is taken by a PostgreSQL that runs on the machine. Host ports 8080 and 3000 are free. Other containers on the machine use 8080 only inside their own network.

**Released versions, checked 17 Nov 2026 against Maven Central, the npm registry, postgresql.org and the Adoptium API.**

| Piece | Version | Note |
| --- | --- | --- |
| JDK | Eclipse Temurin 25.0.5+8 | Latest GA of 25 |
| Spring Boot | 4.1.2 | Latest release of the 4.x line |
| PostgreSQL | 18 (image `postgres:18`) | Current major, supported until Nov 2030 |
| Next.js | 16.4.6 | Needs Node 20.9 or newer |
| React | 19.3.1 | |
| Node | 24.14.1 | Already installed; 24 is an LTS line |
| Maven | 3.9.16, through the Maven wrapper | Maven 4 is still a release candidate |

Flyway, Hibernate, Testcontainers and the other libraries take the versions that Spring Boot 4.1.2 manages.

## 5. Behaviour

### 5.1 JDK 25

Temurin 25.0.5+8 for Linux x64 is downloaded from Adoptium, its SHA-256 is checked against the value the Adoptium API gives, and it is unpacked to `~/.jdks/temurin-25.0.5`. Nothing is installed with sudo. The system default stays JDK 21, so other projects on the machine are not affected.

Every backend command is run with `JAVA_HOME=~/.jdks/temurin-25.0.5`. The Maven build fails with a clear message when it is run on another Java version (Maven Enforcer rule, Java 25 required).

### 5.2 Backend application

- Maven project in `backend/`, group `com.dsinnovators`, artifact `ei`, base package `com.dsinnovators.ei`. The built jar is `backend/target/ei.jar`.
- Persistence is JPA with Hibernate. Hibernate never changes the schema: `spring.jpa.hibernate.ddl-auto=validate`. Flyway owns the tables; Hibernate only checks that the entities match them.
- `spring.jpa.open-in-view=false`.
- Flyway runs when the application starts. A second start finds `flyway_schema_history` and applies nothing.
- Health: Spring Boot Actuator, `GET /actuator/health`, with the database check on. It answers `{"status":"UP"}` only when the application runs and reaches PostgreSQL. No other Actuator endpoint is exposed.
- The server port is read from `BACKEND_PORT`. The database connection is read from `POSTGRES_HOST` (default `localhost`), `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.
- Packages. Each one that holds no class yet gets a `package-info.java` with one sentence that says what belongs there (taken from the master plan section 3).

| Package | F01 puts here |
| --- | --- |
| `com.dsinnovators.ei` | The application class |
| `...ei.common` | `Quarter`, the JSON canonical form and hash |
| `...ei.job` | `Job`, `JobArguments`, the job runner |
| `...ei.setting` | `Setting`, `SettingRepository`, `SettingService` |
| `...ei.source` | `DataSource`, `DataSourceRepository`, `RawRecord`, `RawRecordRepository`, `RawRecordStore` |
| `...ei.identity`, `evidence`, `judgment`, `analytics`, `scoring`, `review`, `api` | `package-info.java` only |

`common`, `job` and `setting` are not in the master plan's package table. They are added here because the things they hold belong to no single spec step. The master plan table is updated when F01 closes.

### 5.3 Storing a raw record

A connector hands over: data source key, record kind, external id, URL and the payload as JSON.

1. The content hash is calculated: SHA-256, as lower-case hex, over the canonical form of the payload. The canonical form is the JSON with object keys sorted, no extra white space, and arrays in their given order. The same content gives the same hash whatever key order the source used.
   The hash is taken from the payload as the connector hands it over, not from the stored `jsonb`. Numbers are parsed without loss of precision and keep their digits, trailing zeros included, so `1` and `1.0` are different content. They are written in plain decimal form, so `1E+2` becomes `100`: this is the form `jsonb` gives back, so a payload read from the database has the same hash as when it was stored (decision 32, made at the F01 review). This rule is not changed once F02 has stored records, because a new rule would make every record look changed.
2. The latest stored version for `(data source, record kind, external id)` is looked up.
3. No version exists: a row is written with `version = 1`. Result `CREATED`.
4. The latest version has the same hash: nothing is written. Result `UNCHANGED`.
5. The latest version has another hash: a row is written with `version = latest + 1`. The old row stays as it is. Result `NEW_VERSION`.

The comparison is only with the latest version. A record that changes and later changes back to earlier content gets a new version, and that version is the current one.

There is no update and no delete. A database trigger on `raw_record` refuses both (migration `V2__raw_record_is_never_changed.sql`, decision 33, added at the F01 review). `RawRecordStore` is the only way to write a raw record. The `RawRecord` entity is marked `@Immutable` (Hibernate), so a change to a loaded object writes nothing.

When a record is fetched again and is unchanged, nothing records that it was seen again. `fetched_at` is the time the stored copy was pulled.

### 5.4 Reading a setting

`SettingService` returns, for a key and a quarter, the row with that key whose `valid_from_quarter` is the latest one that is not after the asked quarter.

- A value stays in force until a row with a later quarter replaces it. A value set for `2026-Q3` also answers for `2026-Q4`, until a `2026-Q4` row exists. After that, `2026-Q3` still gets the Q3 value.
- No row at or before the quarter: the call fails with `SettingNotFoundException`, which names the key and the quarter. There is no default in code.
- A value of the wrong kind for the reader used (for example a list read as a number): the call fails with `SettingTypeException`, which names the key.
- Settings are read from the database on every call. There is no cache, so a value changed by SQL is used by the next call.
- A value is changed by SQL or by a new migration.

`Quarter` is a value type: a year and a number from 1 to 4. Its text form is `YYYY-Qn` (`2026-Q3`). Text forms sort in time order, which the query uses. Quarters are calendar quarters; dates are plain dates, and a caller that starts from a timestamp converts it in UTC.

### 5.5 Running a job

- `Job` is an interface with a name and a run method. A job is a Spring bean. Names are unique; two jobs with one name stop the application at start.
- The application is started with `--job=<name>` and further `--key=value` arguments. It then does not open the web port, runs that one job, prints what the job writes to the log, and exits with code 0 when the job returns and 1 when it throws. The failure message is printed.
- An unknown name: the application prints the known job names and exits with 1.
- Without `--job`, the application starts as the web server and runs no job.
- Flyway runs before the job, as at every start.

F01 builds this mechanism and tests it with a job that exists only in the test sources.

### 5.6 Database for development

`docker-compose.yml` at the repository root has one service, `postgres`, with image `postgres:18`, a named volume for the data, a health check, and the host port from `POSTGRES_PORT` (5433) mapped to 5432 in the container. Database name, user and password come from `.env`.

Only standard PostgreSQL is used: no extensions. Compose is the development database. Where a production database runs is not decided in the PoC (section 12).

### 5.7 Web shell

- `web/` is a Next.js 16.4.6 application: App Router, TypeScript, npm. `.nvmrc` at the repository root holds `24.14.1`.
- The home page is rendered on the server on every request. It calls `GET {BACKEND_URL}/actuator/health` from the server side with a short timeout.
- It shows the title "Engineering Intelligence" and one line: "backend up" when the answer is HTTP 200 with `"status":"UP"`, and "backend down" in every other case (no connection, timeout, another status, another body).
- The browser never calls the backend. This holds for later features too, so no CORS setup exists.
- The dev server listens on `WEB_PORT`.

### 5.8 Configuration

One `.env` at the repository root, not committed. Compose, the backend and the web app all read it. `.env.example` is committed:

```
POSTGRES_DB=ei
POSTGRES_USER=ei
POSTGRES_PASSWORD=ei_local
POSTGRES_PORT=5433
BACKEND_PORT=8080
WEB_PORT=3000
BACKEND_URL=http://localhost:8080
```

`ei_local` is a development password for a database that listens on the local machine only. F02 adds `GITHUB_TOKEN` and F06 the LLM key; their values are never committed.

Values set in the real environment win over `.env`.

## 6. Data

One migration, `backend/src/main/resources/db/migration/V1__base_tables.sql`. All `id` columns are `bigint generated always as identity primary key`. All columns are `not null` unless marked nullable.

**`data_source`**

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint | |
| `key` | text, unique | `github`, `openproject` |
| `name` | text | Display name |
| `base_url` | text | |

Seeded in V1: (`github`, `GitHub`, `https://github.com`) and (`openproject`, `OpenProject`, `https://community.openproject.org`).

**`raw_record`** — never updated, never deleted.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint | |
| `data_source_id` | bigint, FK to `data_source` | |
| `record_kind` | text | What the record is, for example `pull_request`. Each connector defines its kinds |
| `external_id` | text | The id in the source system |
| `version` | int | 1 for the first copy, then 2, 3 |
| `url` | text | Link to the record at the source |
| `content_hash` | text | SHA-256 of the canonical payload, 64 hex characters |
| `payload` | jsonb | The record as the source returned it. Any JSON value; an object in practice |
| `fetched_at` | timestamptz | When this copy was pulled |

Unique: `(data_source_id, record_kind, external_id, version)`. This index also serves the lookup of the latest version.

PostgreSQL's `jsonb` does not keep key order or white space. "Unchanged copy" means every key and value is kept; the text layout is not.

**`setting`**

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint | |
| `key` | text | For example `presence.threshold` |
| `valid_from_quarter` | text | `YYYY-Qn`, with a check constraint on the format |
| `value` | jsonb | A number, a text, a list or an object |
| `description` | text | What the setting means and where the spec uses it |

Unique: `(key, valid_from_quarter)`. V1 inserts no rows.

**`person`** — table only in F01.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint | |
| `display_name` | text | |
| `level` | text, nullable | `L1`, `L2` or `L3`, with a check constraint. Empty for this team |
| `created_at` | timestamptz, default `now()` | |

**`person_identity`** — table only in F01.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint | |
| `person_id` | bigint, FK to `person` | |
| `data_source_id` | bigint, FK to `data_source` | |
| `external_id` | text | The id in that system |
| `username` | text, nullable | GitHub login or OpenProject user name |

Unique: `(data_source_id, external_id)`. F04 adds the columns for a proposed, confirmed or rejected match.

**`project`** — table only in F01.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint | |
| `data_source_id` | bigint, FK to `data_source` | |
| `external_id` | text | OpenProject's project id |
| `identifier` | text | The key prefix, for example `COMMS` |
| `name` | text | |
| `url` | text | |

Unique: `(data_source_id, external_id)`.

## 7. Interfaces

**Uses.** Nothing. F01 is the first feature.

**Gives.** Names are exact. `JsonNode` is the tree type of the Jackson version that Spring Boot 4.1.2 ships.

```java
package com.dsinnovators.ei.common;

public record Quarter(int year, int number) implements Comparable<Quarter> {
    public static Quarter parse(String text);      // "2026-Q3"; IllegalArgumentException on any other form
    public static Quarter of(LocalDate date);
    public LocalDate firstDay();
    public LocalDate lastDay();
    public boolean contains(LocalDate date);
    public Quarter previous();
    public Quarter next();
    @Override public String toString();            // "2026-Q3"
}
```

```java
package com.dsinnovators.ei.source;

public interface RawRecordStore {
    StoreResult store(String dataSourceKey, String recordKind, String externalId,
                      String url, JsonNode payload);
    Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId);
    List<RawRecord> findVersions(String dataSourceKey, String recordKind, String externalId); // oldest first
    List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind);                // current copy of each record
}

public record StoreResult(Outcome outcome, RawRecord record) {   // record: the row written, or the unchanged latest row
    public enum Outcome { CREATED, UNCHANGED, NEW_VERSION }
}
```

`RawRecord` is a JPA entity with getters for every column of the table and no setters. `store` throws `IllegalArgumentException` for an unknown data source key. `DataSourceRepository.findByKey(String)` returns the `DataSource` entity.

```java
package com.dsinnovators.ei.setting;

public interface SettingService {
    BigDecimal getDecimal(String key, Quarter quarter);
    int getInt(String key, Quarter quarter);
    String getText(String key, Quarter quarter);
    List<String> getTextList(String key, Quarter quarter);
    JsonNode getJson(String key, Quarter quarter);
}
```

All five throw `SettingNotFoundException` and `SettingTypeException` as described in 5.4.

```java
package com.dsinnovators.ei.job;

public interface Job {
    String name();
    void run(JobArguments arguments) throws Exception;
}

public interface JobArguments {
    Optional<String> get(String name);
    String require(String name);        // IllegalArgumentException that names the missing argument
}
```

**Also given.**
- Endpoint `GET /actuator/health`.
- Test setup: an abstract base class `com.dsinnovators.ei.DatabaseTest` in the test sources. A test class that extends it runs as a Spring Boot test against a PostgreSQL 18 container with all Flyway migrations applied. One container is shared by all test classes of a run. Each test starts from empty tables, except the seeded `data_source` rows.
- For the web app: `BACKEND_URL` is available to server-side code.
- Tables `data_source`, `raw_record`, `setting`, `person`, `person_identity`, `project` as in section 6.

## 8. Cases that must work

| Case | What happens |
| --- | --- |
| The same record is stored twice with the same payload | One row. The second call returns `UNCHANGED` |
| The same payload with keys in another order | Same hash. `UNCHANGED` |
| A changed payload | A second row with `version = 2`. The first row is untouched |
| Content A, then B, then A again | Three rows. Version 3 is the latest and holds A |
| The same external id under another record kind or data source | A separate record with its own version 1 |
| A loaded `RawRecord` is changed in code and the transaction commits | No update is issued. The row is as before |
| An unknown data source key | `IllegalArgumentException`; nothing is written |
| Two writers store the same new version at once | The unique constraint rejects one; that call fails. Jobs in the PoC run one at a time, so no retry is built |
| A setting with rows for `2026-Q3` and `2027-Q1` | Asked for `2026-Q3` and `2026-Q4`: the Q3 value. Asked for `2027-Q1` and later: the Q1 value. Asked for `2026-Q2`: `SettingNotFoundException` |
| An unknown setting key | `SettingNotFoundException` that names the key |
| A list setting read with `getInt` | `SettingTypeException` |
| `--job=` with an unknown name | The known names are printed; exit code 1 |
| A job throws | The message is printed; exit code 1 |
| A job is given without a required argument | `require` throws; exit code 1 with the argument's name |
| The backend starts and the database is down | The start fails with the connection error. Nothing is retried |
| The backend starts a second time | Flyway reports that the schema is up to date and applies nothing |
| The web page is opened and the backend is down or slow | The page shows "backend down". It does not fail |
| Maven is run with JDK 21 | The build stops with the Enforcer message that Java 25 is required |
| The tests run while the Compose database is stopped | They pass. They use their own container |

## 9. How it is run

Commands, from the repository root. These go into `CLAUDE.md`.

```
cp .env.example .env                                         once
docker compose up -d                                         start the database
cd backend && ./mvnw verify                                  build and run all tests
cd backend && ./mvnw test -Dtest=ClassName                   run one test class
cd backend && ./mvnw spring-boot:run                         start the backend
cd web && npm install                                        once
cd web && npm run dev                                        start the web app
cd web && npm test                                           run the web tests
java -jar backend/target/ei.jar --job=<name> --key=value     run a job
```

Before the backend commands: `export JAVA_HOME=~/.jdks/temurin-25.0.5`. Before the web commands, in a shell where `node` is not on the `PATH`: `source ~/.nvm/nvm.sh && nvm use`.

The job command is run from the repository root so that `.env` is found.

## 10. How it is checked

| "Done when" in the master plan | Exact check | Expected |
| --- | --- | --- |
| One command starts the database | `docker compose up -d`, then `docker compose ps` | Service `postgres` is running and healthy; `psql` on port 5433 connects |
| One command builds and tests the backend with all tests green | `cd backend && ./mvnw verify`, with the Compose database stopped | `BUILD SUCCESS`, zero failures and errors, `target/ei.jar` exists |
| The web shell shows "backend up" | Database and backend started, then `npm run dev`, then `curl -s localhost:3000` | The HTML contains "backend up". After the backend is stopped it contains "backend down" |
| A second start does not run the migrations again | Start the backend, stop it, start it again | The second start logs that no migration is necessary; `flyway_schema_history` still has one row for version 1 |

Further checks:
- `curl -s localhost:8080/actuator/health` returns `{"status":"UP"}`.
- `select key from data_source` returns `github` and `openproject`.
- The jar started with `--job=nope` prints that no such job exists and exits with 1.
- `git status` shows no `.env`, no `target/`, no `node_modules/`.
- The commands in `CLAUDE.md` are the ones that were run for these checks.

The evidence (command and output) is written to `notes.md`.

## 11. Decisions made in this deep dive

**Approaches considered.** F01 has no single "how". It has three choices where real alternatives existed.

| Choice | Alternatives | Picked | Why |
| --- | --- | --- | --- |
| How the backend talks to the database | (1) Spring Data JDBC with plain SQL for analytics: no session and no change tracking, fits append-only tables, needs a small JSONB converter. (2) JPA with Hibernate: most familiar, maps JSONB itself, but tracks and updates loaded objects, which this data model forbids. (3) `JdbcClient` only: least machinery, every insert and mapping by hand | JPA with Hibernate (the user's choice) | Familiarity. The risk is handled by `@Immutable` on never-edited entities, `ddl-auto=validate`, and a test that a changed `RawRecord` writes nothing. Analytics in F10 are native SQL |
| Where tests get a database | (1) Testcontainers: a throwaway PostgreSQL per run, one command, can never touch development data. (2) The Compose database with a separate test database: faster start, but two commands, leftover data, and a wrong setting points tests at real data | Testcontainers (the user's choice) | From F06 the Compose database holds judgments that cost money |
| How a changed raw record is versioned | (1) A `version` number, compared with the latest version's hash. (2) Uniqueness on the content hash, current copy by `fetched_at`: simpler, but a record that changes back to earlier content is dropped and the wrong copy stays current | Version number (confirmed by the user) | Statuses and labels do change back |

**Open questions of the master plan.**

| Question | Answer | Reason | Whose |
| --- | --- | --- | --- |
| Exact versions | The table in section 4 | Latest releases on 17 Nov 2026 | Proposed by the session, not objected to |
| Spring Data JDBC or JPA | JPA with Hibernate | See above | User |
| How tests get a database | Testcontainers | See above | User |
| Columns of `raw_record` | Section 6. One row per version | See above | User confirmed |
| How JDK 25 is installed | Temurin 25 into `~/.jdks/`, no sudo, system default unchanged | Other projects on the machine keep JDK 21 | User |

**Further decisions.**

| Decision | Reason | Whose |
| --- | --- | --- |
| `setting` has `valid_from_quarter`; a value is in force until a later row replaces it | Spec 14 asks for a version per quarter. The PoC scores one quarter and would work without it, but the column costs little and adding a quarter argument to every caller later costs more | User kept it after asking whether it was needed |
| F01 creates tables with only the columns certain today; `person`, `person_identity`, `project` get no Java class yet | Nothing is guessed and reworked; no unused code | User approved the tables |
| `data_source` rows are seeded in V1 | Fixed reference data; `raw_record` needs them from F02's first insert | Session's call, approved with the tables |
| PostgreSQL on host port 5433 | 5432 is taken on the build machine | Session's call, approved with the wiring |
| Health is Actuator's endpoint with the database check; the web page calls it from the server side | "Up" then includes the database; no CORS setup | Session's call, approved with the wiring |
| Jobs: a `Job` interface, started with `--job=<name>`, no web port, exit code 0 or 1 | The master plan asks for a way to run a job from the command line | Session's call, approved with the wiring |
| One `.env` at the repository root for Compose, backend and web | One place for local values and secrets | Session's call, approved with the wiring |
| Base package `com.dsinnovators.ei`, database `ei`, npm | | Session's call, approved with the wiring |
| Maven wrapper on 3.9.16; an Enforcer rule requires Java 25 | The Maven version is pinned and the system Maven is left alone; a wrong JDK fails with a clear message | Session's call |
| Packages `common`, `job`, `setting` added to the layout | `Quarter`, jobs and settings belong to no single spec step | Session's call |
| The content hash is SHA-256 over the canonical JSON (sorted keys), taken from the incoming payload, with numbers as the source spelled them | Key order must not look like a change; the rule must stay fixed once records are stored | Session's call |
| Settings are not cached | F11 needs "changing a value changes the result"; the reads are few | Session's call |
| Web tests use Vitest; the health check is one function with tests for up, down and timeout | "Test first" also holds for the web shell; the function is the only logic in it | Session's call |
| Compose is for development only; nothing depends on the container | The production database is not chosen yet | Session's call, discussed with the user |

## 12. Still open

| Point | Who decides | When |
| --- | --- | --- |
| Where the production database runs: PostgreSQL on a VM or a managed service. The user leans to a VM for the long term | The user, with whoever runs DSi's infrastructure | When a DSi pilot is planned. Nothing in F01 depends on it |
| Which record kinds exist, and whether a PR is one raw record or several | F02 | F02 deep dive |
| Which fields are left out of a payload so that a fetch without a real change does not create a version | F02, F03 | Their deep dives |

**Left to the builder.** The Maven artifact names for Flyway, Testcontainers and the PostgreSQL driver under Spring Boot 4.1.2 (looked up in its documentation at build time, not taken from memory). How the JSONB columns are mapped in Hibernate. How the backend and the web app load the root `.env`. How the job runner keeps the web port closed. The layout of the Next.js page. Class names inside a package that section 7 does not fix.
