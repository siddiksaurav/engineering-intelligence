# Progress

One entry per working session, newest at the top. Each entry says what was done, what is next and what is blocked. Read the top entry first.

## 19 Nov 2026: Claude Code workflow set up

**Done**
- Three project commands in `.claude/skills/`: `/design FNN`, `/build FNN`, `/done FNN`, and two subagents in `.claude/agents/`: `implementer` (Sonnet, one task, test first, commits only when green) and `reviewer` (Opus, read-only, reports problems or APPROVED). Decision 50.
- The question brief: the session decides clear technical points alone and asks only business questions and unclear technical decisions, with options and an AI recommendation. The answer approves design and plan.
- Permissions in `.claude/settings.json` (decision 54). No hooks.
- Architecture tests in `backend/src/test/java/com/dsinnovators/ei/architecture/`: append-only code and tables, the `JudgmentModel` boundary, no number constants in the rule packages. 7 run now; 12 are disabled until F05, F06, F10 and F11 (decisions 51 to 53). ArchUnit 1.5.1 added as a test dependency. No feature code changed.
- `CLAUDE.md`, `docs/master-plan.md`, `docs/features/README.md` and `_template-design.md` describe the new flow.

**Next**
- Pull request 2 (`f02-github-connector`), which now also carries this setup, is merged by the user.
- Then `/design F03` from an up-to-date `main`.

**Blocked or needed from the user**
- Merging pull request 2.
- An LLM API key is needed from F06.

## 19 Nov 2026: F02 done

**Done**
- F02 deep dive with the user; design approved (`docs/features/F02-github-connector/design.md`). The user asked for fewer questions: only real business or technical choices are asked, the rest are stated as the session's calls.
- New way of working for this PoC (decision 40): `plan.md` is a short task list without code, and the session goes on to the build once it is written. `CLAUDE.md` and `docs/features/README.md` say so.
- F02 built in 13 tasks on branch `f02-github-connector`, one commit per task: tests no longer read `.env`, NUL escaping in `raw_record`, `job_run`, the job launcher, `POST /api/jobs/{name}/runs` and `GET /api/jobs/runs/{id}` (local callers only), setting `github.bot_logins`, the GitHub client, the day-by-day PR search, the PR fetcher, the recorder, the `github-import` job, an end-to-end test. 248 backend tests.
- First real import, 1 to 14 Jul 2026: 215 of 215 merged PRs stored with 674 reviews, 735 inline comments, 367 conversation comments; 6 min 22 s. A second run added no rows. Evidence: `docs/features/F02-github-connector/notes.md`.
- Decisions 40 to 49.
- Pushed; pull request 2 opened; CI green (Backend and Web).

**Next**
- Pull request 2 (`f02-github-connector`) is open and CI is green. The user merges it.
- Then F03 OpenProject connector: deep dive with the user. Read F02 `notes.md`, "What a later feature should know", first.

**Blocked or needed from the user**
- Merging pull request 2.
- An LLM API key is needed from F06.

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
