# Progress

One entry per working session, newest at the top. Each entry says what was done, what is next and what is blocked. Read the top entry first.

## 22 Nov 2026: F10 analytics and the hygiene number shipped

**Done**
- `/ship F10`: question brief answered "OK" (decisions 189 to 200); decisions 179 to 188 by the session. 7 tasks in 3 batches and the review fixes on `feature/f10`, built in a worktree beside the repository so the running model jobs were not disturbed.
- Package `analytics` (one SQL query per analytic, nothing stored) and `GET /api/developers/{personId}/analytics?quarter=`. Backend 910 tests green.
- Real data, developer 9: every API number equals a separate SQL count (`docs/features/F10-analytics/notes.md`, "Verification"). Most numbers are still "not judged yet": the model jobs keep running in the background.
- Pull request: see the F10 row of the status table and `notes.md`.

**Checks still open (waiting for the user)**
- When the background model runs finish: restart the backend on `main` and compare developer 9's analytics with the SQL again (notes, "Verification").
- From F06 and F07: the SQL checks of their `notes.md`. From F05: the 20 Feature work groups. From F04: the 19 identity proposals.

**Next**
- `/clear`, then `/ship F11`.

**Blocked or needed from the user**
- Nothing for F11 to start.

## 22 Nov 2026: Groq key in, model jobs running in the background, F08 and F09 deferred

**Done**
- The Groq key is in `.env`. A one-PR digest worked (run 12).
- The model jobs run in the background on the running backend, one after the other (decision 174): `pull-request-digest`, `evidence-build`, `feature-work-judgment`, `review-comment-judgment`, repeated every 30 minutes while the free quota leaves work for later (at most 12 rounds). The first round started with run 13. Check progress with `select id, job_name, status, counts from job_run order by id desc limit 8;`.
- Gemini is off: the `backend` entry in `.claude/launch.json` blanks `LLM_PROVIDER_2_API_KEY`, and `LLM_PROVIDER_2_MODEL` is empty in `.env` (an empty line overrides the default of decision 177).
- F08 and F09 deferred to after F12 (decision 178).

**Next**
- `/ship F10` on the two-week window. Its code and tests need no model; its real-data check needs the judgments of one developer, which the background runs are producing.

**Blocked or needed from the user**
- Nothing for F10 to start. The F06 and F07 SQL checks follow once the background runs finish.

## 22 Nov 2026: F07 Stage 2 judgment built while the user slept (no LLM key)

**Done**
- `/ship F07` on the user's instruction to decide everything and skip the model runs: no question brief was answered; decisions 163 to 176 are the session's and may be overruled by number.
- Migration V11 (kinds `feature_work_complexity`, `review_comment`; settings `evidence.complexity_tiers`, `evidence.review_comment_types`); prompts `feature-work-complexity/v1` and `review-comments/v1`; jobs `feature-work-judgment` and `review-comment-judgment`; `ModelJobGuard` (the three model jobs never run at once). 12 tasks in 3 batches, a confidence fix and 7 review-fix commits on `feature/f07`. Nothing new on screens (decision 175).
- Pull request: see the F07 row of the status table and `notes.md`.

**Checks still open (waiting for the user)**
- The LLM key in `.env`, then the job sequence and SQL checks in `docs/features/F07-stage-2-judgment/notes.md`, "Verification" (they include F06's open checks).
- Read decisions 163 to 176 and overrule any by number.
- From F05: the 20 Feature work groups. From F04: the 19 identity proposals.

**Next**
- `/clear`, then `/ship F08` (its blind labels can be made once F07 has judged on real data; building it can start now).

**Blocked or needed from the user**
- The LLM keys (Groq, optionally Gemini).

## 22 Nov 2026: F06 JudgmentModel, PR digest and the digest-based builders shipped

**Done**
- `/ship F06`: question brief answered (Q1 own answer: free keys, free alternatives, minimal processing; after a follow-up F1 "all PRs of the two weeks", F2 and Q2 to Q11 as recommended): decisions 144 to 156; 127 to 143, 157, 158 by the session, 159 to 162 from the build and review. 14 tasks in 4 batches and 5 review fixes on `feature/f06`.
- Migrations V9 (`judgment`, new evidence types, settings) and V10; package `judgment` (`JudgmentModel`, OpenAI-compatible client paced by rate-limit headers, provider chain Groq then Gemini, versioned prompt `pull-request-digest/v1`, diff filter and budget, judgment store, job `pull-request-digest`); `evidence` builds Test change, Refactor and performance, Security, Infra and release, Documentation, and Feature work and Bug fix of PRs without a usable work item, with `item_tier` judgments; API and web show the new types with their tier.
- Real data without a key (runs 10, 11): the digest job refuses to start without a key; `evidence-build` leaves all 476 F05 items unchanged.
- Pull request: see the F06 row of the status table and `notes.md`.

**Checks still open (waiting for the user)**
- Put `LLM_PROVIDER_1_API_KEY` (Groq) and, for the fallback, `LLM_PROVIDER_2_API_KEY` and `LLM_PROVIDER_2_MODEL` (Gemini) in `.env` (`cp .env.example .env` first; also set `POSTGRES_PASSWORD`). Then the 50-PR measurement, the run over all 172 human PRs, `evidence-build`, and the SQL checks: `docs/features/F06-pr-digest/notes.md`, "Verification".
- From F05: the 20 Feature work groups. From F04: the 19 identity proposals.

**Next**
- `/clear`, then `/ship F07` (it can be built while the digests run; its real-data check needs them).

**Blocked or needed from the user**
- The LLM keys above. Several keys of one provider are not used (Groq's policy, decision 144).
- The scope of F09 (the July run) is open after decision 145.

## 22 Nov 2026: F05 Evidence builders and the evidence list page shipped

**Done**
- `/ship F05`: question brief answered (Q1 to Q11 as recommended, Q12 A, Q13 B after an explanation): decisions 112 to 124; decisions 100 to 111 and 125 by the session, 126 from the review. 12 tasks in 4 batches, a fix for decision 125 and 2 review fixes on `feature/f05`.
- Migration V8 (`evidence_item`, `evidence_item_source`, `evidence_item_link`, append-only trigger), package `evidence` (three builders, the store, job `evidence-build`), package `api` (`GET /api/developers`, `GET /api/developers/{personId}/evidence-items`), web pages `/developers` and `/developers/[personId]`.
- Real data (runs 7 to 9): 476 items (35 Feature work, 62 Bug fix, 379 Review comment written), 0 failed; a second run writes nothing; no Bug in a Feature work group. Backend 494 tests, web 48 tests, all green.
- Pull request: see the F05 row of the status table and `notes.md`.

**Checks still open (waiting for the user)**
- Check the 20 Feature work groups listed in `docs/features/F05-evidence-builders/notes.md`, "Verification", against GitHub and OpenProject.
- From F04: the 19 identity proposals.

**Next**
- `/clear`, then `/ship F06`.

**Blocked or needed from the user**
- A Groq Cloud API key in `.env` before F06 (and `cp .env.example .env` on this machine).
- A `GITHUB_TOKEN` to finish the 109 completion PRs of `openproject-import`.

## 21 Nov 2026: F04 Identity shipped

**Done**
- Restored the user's database dump from the earlier machine (decision 91): F02's 215 PRs of 1 to 14 Jul 2026. Ran `openproject-import` on it (run 3): 320 work items, 133 PRs linked; its completion step failed for 109 PRs for lack of a `GITHUB_TOKEN`.
- `/ship F04`: question brief answered one question at a time (decisions 92 to 98); decisions 82 to 91 by the session, 99 from the review. 8 tasks in 3 batches plus 3 review fixes on `feature/f04`.
- Job `identity-build`, table `identity_proposal`, functions `confirm_identity_proposal` and `reject_identity_proposal`, `PersonDirectory`, `BotAccounts` (for F05), `StoredGitHubAccounts`.
- Real data (run 6): 30 persons, no bot, 19 proposals each with evidence; a second run changes nothing. Backend 435 tests, web 27 tests, all green.

**Checks still open (waiting for the user)**
- Check the 19 proposals listed in `docs/features/F04-identity/notes.md`, "Verification", and confirm or reject them with the two SQL functions (commands in `CLAUDE.md`).

**Next**
- `/clear`, then `/ship F05`.

**Blocked or needed from the user**
- `cp .env.example .env` on this machine (sessions may not write it), and a `GITHUB_TOKEN` in it to finish the 109 completion PRs of `openproject-import`.
- A Groq Cloud API key before F06.

## 21 Nov 2026: faster workflow, one-month window, free LLM

**Done**
- `/ship FNN` replaces `/design`, `/build` and `/done` (decisions 72 to 77): one stop per feature (the question brief, no limit on questions), each task runs only the tests it touches, one implementer per batch of tasks, one review round with blocking and follow-up findings, the session pushes and merges when CI is green.
- Window cut to 1 to 31 Jul 2026 (decision 78). LLM is a free hosted model, Groq Cloud first, through an OpenAI-compatible API (decision 79). F13 dropped, F08 prompt tuning one round (decision 80). The next design may overlap the current build; F11 may run in parallel after F06 (decision 81).
- `CLAUDE.md`, `docs/features/README.md`, `docs/master-plan.md` and `.claude/agents/` updated. `.claude/settings.json` is unchanged: new allow rules are the user's to add.

**Next**
- `/ship F04`. Deadline for the PoC: 22 Nov 2026.
- Still open from F03: run `openproject-import` on real data and record it in F03 `notes.md`, section "Verification".

**Blocked or needed from the user**
- A Groq Cloud API key in `.env` before F06 (the name is fixed in F06).
- Groq's free-tier daily limits may not cover about 450 PRs in one day; F06 checks them and asks.

## 19 Nov 2026: F03 built, merged without the last review round

**Done**
- `/build F03`: 12 tasks on `feature/F03`, one commit each, plus fixes from two review rounds (14 problems: fetch each work package once per run, `openproject` links for every pull request, answers checked at the boundary, exact done-status match, shared helpers instead of copies, missing tests, dead code). Decisions 70 and 71 found while building.
- Backend `./mvnw verify`: 378 tests, 0 failures, 12 skipped (architecture tests disabled until later features). Web: format, lint, type check, 27 tests, build all green.
- At the user's request, review round 3 was not run and the branch was pushed, opened as a pull request and merged.

**Not done**
- The real-data checks of the design's "How it will be tested" (resolved share about 77% on 1 to 14 Jul 2026, PRs 26057 and 26061 to 81224, a second run adds no rows, completion pull requests of two done Features checked by hand). No `summary.md` yet.

- `F03` done (branch `feature/F03`, pull request 3, merged). Marked done at the user's word while the real-data checks above are still open; there is no `summary.md`.

**Next**
- F04 Identity: `/design F04`.
- Still open from F03: run `openproject-import` on real data (1 to 14 Jul 2026, quarter 2026-Q3) and record the evidence in `docs/features/F03-openproject-connector/notes.md`, section "Verification".

**Blocked or needed from the user**
- `GITHUB_TOKEN` in `.env` for the completion step.
- An LLM API key is needed from F06.

## 19 Nov 2026: F03 design approved, plan written

**Done**
- Branch `feature/F03` from `main` (local `main` fast-forwarded to `origin/main`, which has F02 merged as pull request 2).
- Deep dive on the real data (checked 19 Nov 2026): 133 of 172 human pull requests of 1 to 14 Jul resolve to a visible work item (77%); old keys resolve on the server (`SE-694` is 81224); 12 references point to non-public projects. Detail: `docs/features/F03-openproject-connector/design.md`.
- Question brief answered by the user, one question at a time: done statuses `tested`, `deployed`, `resolved`, `released`, `closed`; all pull requests pulled for Features done in the quarter only; OpenProject's pull request lists stored as a reduced copy. Decisions 56 to 69.
- The master plan's F03 block now says F03 builds only the readers it uses (decision 62).
- `plan.md` with 12 tasks; empty `notes.md`.

**Next**
- `/clear`, then `/build F03`.

**Blocked or needed from the user**
- Nothing for F03 (no login needed; `GITHUB_TOKEN` is already in `.env` for the completion step).
- An LLM API key is needed from F06.

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
