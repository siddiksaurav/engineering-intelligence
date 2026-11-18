# F01 Repository skeleton: notes

## Result

All eight tasks of `plan.md` are built, and two findings of the final review are fixed. 74 backend tests and 11 web tests pass. Every "Done when" line of the master plan holds; the evidence is at the end of this file.

## Differences from the plan, and why

| Where | Plan | Built | Why |
| --- | --- | --- | --- |
| Task 2 | Add `spring-boot-starter-webmvc-test` | Not added. `HealthEndpointTest` calls the real port with the JDK's `HttpClient` | One dependency fewer; no Boot test-client API to follow |
| Task 4 | Map `payload` as a `JsonNode` with `@JdbcTypeCode(SqlTypes.JSON)`; fall back to text if that fails | Text from the start: the field is a `String` with `@JdbcTypeCode(SqlTypes.JSON)`, and `getPayload()` parses it | The JSON literal `null` must reach `jsonb` as `null`, not as SQL `NULL`; text does that with certainty. The `JsonNode` mapping was not tried |
| Task 4 | Try a derived query on an attribute named `key` | The attribute is `sourceKey` with `@Column(name = "key")`; `findByKey` has an explicit query | `key` is a keyword in JPQL. `getKey()` and the column are as designed |
| Task 4 | Not stated | `RawRecord.dataSource` is fetched eagerly | `getDataSource().getKey()` must work outside a transaction, because `open-in-view` is off |
| Task 5 | Not stated | `Setting` and `SettingRepository` are package-private | Only `SettingService` is given to later features |
| Task 6 | `isJobMode` is true when an argument starts with `--job` | True for `--job` and `--job=...` only | `--jobs=x` must not switch the web server off |
| Task 6 | `JobRegistry` and `JobRunner` are beans | They are created in `JobConfiguration` from an `ObjectProvider<Job>` | A `List<Job>` constructor does not start when no `Job` bean exists, which is the state of F01 |
| Task 6, design 9 | `java -jar backend/target/ei.jar ...` | `$JAVA_HOME/bin/java -jar ...` | The default `java` on the machine is 21 |
| Task 7 | Not stated | `cacheComponents` and `partialPrefetching`, which `create-next-app` 16.4.6 switches on, are removed from `next.config.ts` | The page uses `dynamic = "force-dynamic"`, which that mode does not accept |
| Task 7 | Not stated | `@types/node` is `^24`, not the generated `^20` | Vitest 5.1.0 does not accept `^20`; the project runs on Node 24 |

None of these changes what F01 does.

## Changed after the build (18 Nov 2026)

- The seven placeholder `package-info.java` files (`identity`, `evidence`, `judgment`, `analytics`, `scoring`, `review`, `api`) were removed at the user's request. A package now appears when a feature puts its first class there; the layout stays in the master plan, section 3. Decision 34.
- The plan still lists the files in Task 1. The plan is left as the record of what was planned.
- The code was checked against the new "Code quality" section of `CLAUDE.md` and brought to it (decisions 36 and 37):
  - **Behaviour that changed.** `RawRecordStore.findLatest`, `findVersions` and `findLatestByKind` throw `IllegalArgumentException` for a missing record kind or external id. A job argument without `--`, or one given twice, fails the run with exit code 1 and names the argument. `Quarter.of(null)`, `Quarter.contains(null)` and a `SettingService` call with a null key or quarter throw `NullPointerException` with the name of the argument. The web health check logs why the backend counted as down. `WEB_PORT` is checked to be a port number.
  - **Names.** `SettingRepository.findInForce`, `RawRecordRepository.findLatestVersion` and `findVersions` replace the long generated names; each has an explicit query.
  - **Removed.** Three unused getters and the `description` attribute of `Setting` (the column stays), the unused `testcontainers-junit-jupiter` dependency. `JobRegistry` is package-private.
  - **Enforced by the build.** Compiler warnings are errors (`-Xlint:all`), Error Prone 2.53.0 runs in the compiler, Spotless 3.11.0 with palantir-java-format 2.103.0 checks the formatting in `verify`. Web: Prettier 3.10.1, `npm run typecheck`. GitHub Actions runs all of it.
  - **Tests.** 121 backend tests and 27 web tests. The page component and the port rule now have tests.
  - **Left as they are, on purpose:** the five-argument `RawRecordStore.store` (fixed by the design), the hand-written job argument parser, plain JUnit assertions. `DataSourceRepository` stays public because design section 7 names it as an interface for later features.
  - **Open:** `npm audit` reports 16 high-severity findings, all in development dependencies (`picomatch` under `vite`, which Vitest uses); the dependencies that ship have none. The only offered fix is a forced, breaking upgrade, so it is not applied.
  - **Three earlier open points are now closed:** the silent job arguments, the null quarter, and reads without validation.

## Final review (one fresh reviewer over the whole branch)

Two findings were fixed, each with a test that failed first:

| Finding | Fix | Tests |
| --- | --- | --- |
| A `raw_record` row could be deleted through JPA (`EntityManager.remove`, a JPQL delete) or changed by plain SQL. `@Immutable` stops only updates through Hibernate | Migration `V2__raw_record_is_never_changed.sql`: a trigger that refuses every update and delete. `TRUNCATE` still works, which the tests rely on | `RawRecordStoreTest.aStoredRecordCannotBeDeletedThroughJpa`, `theDatabaseRefusesAnUpdateOrDeleteOfARawRecord` |
| A number with an exponent was written as `1E+2` in the canonical form, but `jsonb` returns `100`. A payload read back from the database then had another hash than the stored `content_hash`, and storing it again made a false new version | Numbers are written in plain decimal form (decision 32) | `CanonicalJsonTest.numbersAreWrittenInPlainDecimalFormWhateverExponentTheSourceUsed`, `RawRecordStoreTest.aStoredPayloadHashesToItsContentHashAndStoresAsUnchanged` |

Findings that were not fixed, for the features they concern:

- **F02 must decide: text with a NUL character.** `jsonb` cannot store the character U+0000. A payload whose text contains it (possible in a PR body) makes `store` fail with `DataIntegrityViolationException`. F02 decides whether such a character is removed, replaced or the record skipped.
- **F02, F03: always parse with `CanonicalJson.parse`.** `store` accepts any `JsonNode`. A tree read by another mapper holds decimals as binary floating point, so `1.10` is stored as `1.1` and the hash differs. Nothing in the code stops this yet.
- **F02, F06: tests see the developer's `.env`.** Backend tests run in `backend/` and load `../.env`. Harmless today. Once `GITHUB_TOKEN` and the LLM key are in `.env`, a test that wires the real client would call the real service. Decide before F06 (for example a test profile that does not import `.env`).
- **Job arguments are also Spring properties.** `--debug` or `--spring.*` given to a job changes the application. A repeated argument (`--from=A --from=B`) keeps the first value without a message. An argument without `--` is ignored.
- **`.env` is read by three parsers.** Compose and Node strip quotes around a value; Spring keeps them. Do not quote values in `.env`.
- **`npm start` does not load the root `.env`;** only `npm run dev` does. Production start is out of scope.
- **A fresh clone** needs `npm run dev` or `npx next typegen` once before the TypeScript compiler knows `LayoutProps`.
- `SettingService` readers given a `null` quarter throw `NullPointerException`. A lone surrogate character in a text hashes like `?`.

## What a later feature should know

- **Health body.** `GET /actuator/health` answers `{"groups":["liveness","readiness"],"status":"UP"}`, not the bare `{"status":"UP"}` of the design. The web check reads only `status`.
- **Parsing source JSON.** Turn a source response into a `JsonNode` with `CanonicalJson.parse(String)`. It keeps numbers exact (`1.10` stays `1.10`), which the content hash depends on. A `JsonNode` built another way may spell a number differently and so give another hash.
- **What is stored.** `raw_record.payload` receives the canonical text of the payload (sorted keys). PostgreSQL's `jsonb` keeps every key and value and the scale of numbers; it does not keep key order.
- **Reading a payload.** `RawRecord.getPayload()` parses the text on every call. Keep the result in a variable when it is used more than once.
- **Jackson.** Spring Boot 4.1.2 ships Jackson 3: the packages are `tools.jackson.*`, text nodes are read with `asString()` or `stringValue()`.
- **Tests.** Extend `com.dsinnovators.ei.DatabaseTest`. It gives `jdbc` and empties `raw_record`, `setting`, `person_identity`, `person`, `project` before each test. A feature that adds a table adds it to that `truncate` line.
- **A new job.** Declare a `@Component` that implements `Job`. Nothing else is registered.
- **Jobs and the database.** Job mode starts the whole application context without the web server, so Flyway runs and the database must be up.
- **Compose volume.** The `postgres:18` image keeps its data under `/var/lib/postgresql`, not `/var/lib/postgresql/data`. The volume is mounted there.
- **`web/AGENTS.md`.** `next dev` writes this file and writes it again when it is removed. It is committed so the tree stays clean. It tells a coding session to read the guides in `web/node_modules/next/dist/docs/` before writing Next.js code, because Next.js 16 differs from older versions.
- **Next.js telemetry.** The checks were run with `NEXT_TELEMETRY_DISABLED=1`. Without it Next.js sends anonymous usage data.

## Not covered by a test

- Two writers that store the same new version at the same moment. The unique constraint rejects one (design section 8). PoC jobs run one at a time.
- The backend started while the database is down. It fails at start with the connection error; this was not run as a check.
- The web port staying closed in job mode is checked by hand only (no "Tomcat started" line in the job run below).

## Measurements

- `./mvnw verify`: about 40 seconds, of which the PostgreSQL container start is a few seconds.
- Versions in use: Temurin 25.0.5+8, Maven 3.9.16, Spring Boot 4.1.2, Hibernate 7.4.13, Flyway 12.4.0, Testcontainers 2.0.5, PostgreSQL 18.7, Next.js 16.4.6, React 19.3.1, Vitest 5.1.0, Node 24.14.1.

## Verification evidence (17 Nov 2026)

Run from a clean state: backend and web app stopped, `docker compose down` (volume kept).

The first migration was applied on the first backend start, during Task 6. Both starts below are therefore "second" starts and apply nothing. The evidence was taken before the review fix added migration V2; at that time `flyway_schema_history` had one row. After the fix it has two (versions 1 and 2), and a further start again applies nothing.

```
$ docker compose ps   (database stopped)
(no services running)
$ cd backend && ./mvnw verify
[INFO] Tests run: 70, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
target/ei.jar
$ docker compose up -d && docker compose ps
postgres  Up 5 seconds (healthy)  127.0.0.1:5433->5432/tcp
$ curl -s localhost:8080/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
$ first start, Flyway log lines
org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5433/ei (PostgreSQL 18.7)
o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.031s)
o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
$ curl -s localhost:3000 | grep -oE 'backend (up|down)'   (backend running)
backend up
$ curl -s localhost:3000 | grep -oE 'backend (up|down)'   (backend stopped)
backend down
$ second start, Flyway log lines
org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5433/ei (PostgreSQL 18.7)
o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.017s)
o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
$ docker compose exec postgres psql -U ei -d ei -c "select version, success from flyway_schema_history"
 version | success 
---------+---------
 1       | t
(1 row)

$ docker compose exec postgres psql -U ei -d ei -c "select key from data_source order by key"
     key     
-------------
 github
 openproject
(2 rows)

$ $JAVA_HOME/bin/java -jar backend/target/ei.jar --job=nope; echo exit=$?
com.dsinnovators.ei.job.JobRunner        : No job named 'nope'. No jobs are known.
exit=1
$ git status --short
(clean)
```
