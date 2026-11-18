# Progress

One entry per working session, newest at the top. Each entry says what was done, what is next and what is blocked. Read the top entry first.

## 18 Nov 2026: F01 merged into `main`

**Done**
- The repository is on GitHub (`origin`, private). `main` and `f01-repository-skeleton` are pushed. F01 is pull request 1.
- The seven placeholder `package-info.java` files were removed (decision 34).
- `CLAUDE.md` has a new section "Code quality": production-grade, industry-standard clean code is mandatory (decision 35).
- The F01 code was checked against that section and fixed. The build now enforces it, and GitHub Actions runs on every pull request (decisions 36 and 37). 121 backend tests, 27 web tests. Detail: `docs/features/F01-repository-skeleton/notes.md`, section "Changed after the build".
- Amendment 1 to the F01 design, approved by the user: the command-line job runner is withdrawn. Jobs will be started through an HTTP endpoint and every run recorded; F02 builds the launcher, the `job_run` table and the endpoint. F01 keeps `Job`, `JobArguments` and `JobRegistry` (decisions 38 and 39). 106 backend tests.
- Pull request 1 is merged into `main` (merge commit `532b802`). CI is green.

**Next**
- F02 GitHub connector: deep dive with the user. It now includes the job launcher (F01 design, section 13). Read `docs/features/F01-repository-skeleton/notes.md`, section "What a later feature should know", first.

**Blocked or needed from the user**
- A read-only GitHub token is needed for F02. It goes into `.env` as `GITHUB_TOKEN`; the user puts it there.
- An LLM API key is needed from F06.

## 17 Nov 2026: F01 built and verified

**Done**
- F01 Repository skeleton is done: design and plan approved by the user, eight tasks built test first on branch `f01-repository-skeleton`, one commit per task.
- Temurin JDK 25.0.5+8 installed to `~/.jdks/temurin-25.0.5`. The system default is still JDK 21.
- Backend: Spring Boot 4.1.2, migrations `V1__base_tables.sql` and `V2__raw_record_is_never_changed.sql`, `RawRecordStore`, `SettingService`, `Quarter`, `CanonicalJson`, the `Job` runner, `/actuator/health`. 74 tests.
- A fresh reviewer read the whole branch. Two findings were fixed (a delete path on `raw_record`; number spelling in the content hash). The rest are listed in `notes.md` for F02 and F06.
- Development database: PostgreSQL 18 in Docker Compose on host port 5433.
- Web: Next.js 16.4.6 shell that shows "backend up" or "backend down". 11 tests.
- The commands are in `CLAUDE.md`. Every "Done when" check is recorded in `docs/features/F01-repository-skeleton/notes.md`.
- Decisions 28 to 33 added.

**Next**
- F02 GitHub connector: deep dive with the user. Read `docs/features/F01-repository-skeleton/notes.md`, section "What a later feature should know", first.

**Blocked or needed from the user**
- The branch `f01-repository-skeleton` is not merged into `main` yet; the user decides how.
- A read-only GitHub token is needed for F02. It goes into `.env` as `GITHUB_TOKEN`; the user puts it there.
- An LLM API key is needed from F06.

## 17 Nov 2026: F01 design approved, task plan written

**Done**
- F01 deep dive held with the user. All open questions of the F01 block are answered.
- `docs/features/F01-repository-skeleton/design.md` written and approved by the user.
- Decisions 19 to 27 added to `docs/decisions.md`.
- `docs/features/F01-repository-skeleton/plan.md` written: 8 tasks. State: draft, waiting for the user's approval.
- Library names and versions under Spring Boot 4.1.2 were checked against Maven Central for the plan (Flyway 12.4.0, Hibernate 7.4, Jackson 3.1, Testcontainers 2.0.5).

**Next**
- The user reads and approves the plan and chooses how it is executed. Then implementation, starting with Task 1.

**Blocked or needed from the user**
- Approval of the F01 plan.
- JDK 25 is still not installed. It is step 1 of Task 1 (Temurin 25 into `~/.jdks/`, approved by the user).
- A read-only GitHub token is needed from F02.
- An LLM API key is needed from F06.

## 17 Nov 2026: documentation set created

**Done**
- `poc-plan.md` updated: Java 25, Spring Boot 4.x, Maven.
- Spec copied to `docs/spec/spec-v1.5.md`.
- `docs/master-plan.md` written: 13 features (F01 to F13), global constraints, out of scope, status table, glossary.
- `CLAUDE.md`, `docs/features/README.md`, `docs/features/_template-design.md`, `docs/decisions.md` written.
- Git repository created. No application code exists.

**Next**
- F01 Repository skeleton: deep dive with the user, written to `docs/features/F01-repository-skeleton/design.md`.

**Blocked or needed from the user**
- JDK 25 is not installed on this machine (OpenJDK 21 only). F01 needs it. Installing needs the user's approval.
- A read-only GitHub token is needed from F02.
- An LLM API key is needed from F06.
