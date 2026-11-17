# F01 Repository skeleton: notes

## Result

All eight tasks of `plan.md` are built. 70 backend tests and 11 web tests pass. Every "Done when" line of the master plan holds; the evidence is at the end of this file.

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

The migration itself was applied on the first backend start, during Task 6. Both starts below are therefore "second" starts and apply nothing; `flyway_schema_history` has one row.

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
