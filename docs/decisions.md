# Decisions

Every decision that shapes the PoC, newest at the bottom. A later entry overrides an earlier one; when it does, it names the entry it replaces. Add an entry whenever the user decides something or a feature deep dive closes an open question.

| # | Date | Decision | Reason | Source |
| --- | --- | --- | --- | --- |
| 1 | 16 Nov 2026 | The PoC uses public data: `github.com/opf/openproject` and `community.openproject.org` | Real, rich data that can be shown to management without access to DSi projects | `poc-plan.md` Context |
| 2 | 16 Nov 2026 | Scope is option B: judgment plus a profile slice. Option C comes later | The smallest scope where the whole chain from source record to dimension score is visible | `poc-plan.md` 2 |
| 3 | 16 Nov 2026 | No screen for a lead to correct a judgment in the PoC. The data model supports corrections | Keeps the PoC small; the screen can be added without table changes | `poc-plan.md` 3.1 |
| 4 | 16 Nov 2026 | The accuracy measurement is optional and comes after all planned features | Nothing in F01 to F12 depends on it | `poc-plan.md` 4 |
| 5 | 16 Nov 2026 | The demo is planned separately | Not part of the build | `poc-plan.md` Context |
| 6 | 16 Nov 2026 | Real developer names are shown | The data is public | `poc-plan.md` Context |
| 7 | 16 Nov 2026 | Any cloud LLM may be used, behind the `JudgmentModel` interface. Start with a Claude model | The provider can be changed without touching the rest | `poc-plan.md` 3.2 |
| 8 | 16 Nov 2026 | Stack: PostgreSQL, Spring Boot, Next.js | User's choice | `poc-plan.md` Context |
| 9 | 17 Nov 2026 | Code written with AI belongs to the person who pushed it. The AI-usage labels on PRs are ignored | Decided by the user at the plan review; reason not recorded | Plan review |
| 10 | 17 Nov 2026 | A bug is never Feature work. The tracker type decides, not the AI | Decided by the user at the plan review; reason not recorded | Plan review |
| 11 | 17 Nov 2026 | The second dimension is Engineering standards and system health, not Planning and execution | Its evidence (tests, refactor, security, infra) is present in the data; sprint data is not | Plan review |
| 12 | 17 Nov 2026 | A 30-item blind check is added at build step 8 (F08) | A first check of the AI before scores are built on it | Plan review |
| 13 | 17 Nov 2026 | Java 25, not Java 21 | User's decision | This session |
| 14 | 17 Nov 2026 | Spring Boot 4.x, not 3 | Current major line with Java 25 as a first-class target; longer support for a system meant to become the real product | This session |
| 15 | 17 Nov 2026 | Maven as the build tool | Already installed on the build machine; no reason to add another | This session |
| 16 | 17 Nov 2026 | One repository with `backend/` and `web/` | One place for code and documents; a session sees everything | This session |
| 17 | 17 Nov 2026 | The project is built feature by feature: master plan, then per feature a design, a task plan and the implementation, each approved by the user | The whole build is done by Claude Code across sessions with no memory; the documents carry the context | This session |
| 18 | 17 Nov 2026 | The spec is kept in the repository as `docs/spec/spec-v1.5.md` and never edited | Every session can read it; proposed changes stay in `poc-plan.md` section 5 | This session |
| 19 | 17 Nov 2026 | JDK 25 is Eclipse Temurin 25.0.5+8, unpacked to `~/.jdks/` without sudo. The system default stays JDK 21 | Other projects on the build machine keep their Java | F01 deep dive, user |
| 20 | 17 Nov 2026 | Versions: Spring Boot 4.1.2, PostgreSQL 18, Next.js 16.4.6 with React 19.3.1, Node 24.14.1, Maven 3.9.16 through the Maven wrapper | Latest releases, looked up on 17 Nov 2026. Maven 4 is still a release candidate | F01 deep dive |
| 21 | 17 Nov 2026 | Persistence is JPA with Hibernate. Never-edited and append-only entities are `@Immutable`; Hibernate only validates the schema, Flyway owns it | User's choice over Spring Data JDBC. The guards keep Hibernate from updating rows that must not change | F01 deep dive, user |
| 22 | 17 Nov 2026 | Tests get their database from Testcontainers, never from the Compose database | One command builds and tests; tests cannot touch development data, which from F06 holds paid judgments | F01 deep dive, user |
| 23 | 17 Nov 2026 | `raw_record` has one row per version of a record. A fetched record is compared with the latest version's content hash; a different hash writes the next version | A record can change and change back; uniqueness on the hash would drop the return to earlier content | F01 deep dive, user |
| 24 | 17 Nov 2026 | `setting` has `valid_from_quarter`. A value is in force until a row with a later quarter replaces it. A missing setting is an error, never a default in code | Spec 14 asks for a version per quarter. Not strictly needed for one quarter, but cheap now and costly to add later | F01 deep dive, user |
| 25 | 17 Nov 2026 | F01 creates tables with only the columns certain today. Later features add columns in their own migrations | Nothing is guessed and reworked | F01 deep dive, user |
| 26 | 17 Nov 2026 | Local wiring: PostgreSQL on host port 5433, backend 8080, web 3000; one `.env` at the repository root; health is Actuator's endpoint with the database check; the web app calls the backend only from the server side; jobs are `Job` beans started with `--job=<name>`; base package `com.dsinnovators.ei` | Port 5432 is taken on the build machine; the rest keeps the setup small | F01 deep dive, user approved |
| 27 | 17 Nov 2026 | Docker Compose is the development database only. Only standard PostgreSQL is used, with no extensions | The production database can then be a VM or a managed service without code changes | F01 deep dive |
| 28 | 17 Nov 2026 | JSON columns are held as JSON text in the entities (`RawRecord.payload`, `Setting` value) and parsed with `CanonicalJson.parse` on read | Every JSON value, the literal `null` included, reaches `jsonb` unchanged, and nothing depends on how Hibernate maps Jackson 3 types | F01 build |
| 29 | 17 Nov 2026 | Entity attributes for the columns `key` and `value` are named `sourceKey`, `settingKey`, `settingValue` | `key` and `value` are keywords in JPA's query language. The columns keep their names | F01 build |
| 30 | 17 Nov 2026 | The web app does not use Next.js cache components. Pages that show live data are rendered on every request (`dynamic = "force-dynamic"`) | The health page must never show a cached answer. A later feature can turn caching on per page | F01 build |
| 31 | 17 Nov 2026 | A job is run with `$JAVA_HOME/bin/java -jar ...`, not plain `java` | The default `java` on the build machine is 21 and cannot run the jar | F01 build |
| 32 | 17 Nov 2026 | In the canonical form of JSON a number is written in plain decimal form with its digits kept (`1.50` stays `1.50`, `1E+2` becomes `100`). This narrows decision-level wording in the F01 design ("as the source spelled them") | It is the form PostgreSQL's `jsonb` returns, so a stored payload hashes to its own content hash. Found by the F01 review; fixed before any record is stored, because the rule is frozen after F02 | F01 review |
| 33 | 17 Nov 2026 | A database trigger refuses every update and delete of `raw_record` (migration V2) | `@Immutable` stops updates through Hibernate only; a delete through JPA or plain SQL was still possible. The first rule of the project now holds in the database itself | F01 review |
| 34 | 18 Nov 2026 | No placeholder files for empty packages. A package is created by the feature that puts its first class there | Seven `package-info.java` files held no behaviour and were noise in the code. The package layout is already in the master plan, section 3 | User, F01 pull request |
| 35 | 18 Nov 2026 | Production-grade, industry-standard clean code is mandatory for all code in this repository. The rule is in `CLAUDE.md`, section "Code quality" | The PoC is the first slice of the real system, not throwaway code | User |

## Not decided yet

- The version of the LLM SDK. Fixed in F06.
- Where the production database runs: PostgreSQL on a VM or a managed service. The user leans to a VM. Decided when a DSi pilot is planned.
- The LLM models for Stage 1 and Stage 2. Fixed in F06 and F07.
- Each feature's "Open questions" in `docs/master-plan.md`.
- The 14 proposed spec changes in `poc-plan.md` section 5 are proposals to the spec owner. The PoC already follows them.
