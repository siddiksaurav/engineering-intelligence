# Engineering Intelligence PoC

A system that measures a developer's performance from the work they leave in Git and OpenProject. An AI judges each piece of work (work type, complexity, tier); everything after that is counting. This PoC proves the judgment on public data (`github.com/opf/openproject`, `community.openproject.org`) and shows one developer profile where every score can be followed back to a pull request. It is the first slice of the real system.

The whole project is built by Claude Code, one feature at a time. You have no memory of earlier sessions. The documents below are that memory. Keep them current.

## Start of every session

Read in this order:
1. `docs/progress.md`: the last entry says where the work stands and what is next.
2. `docs/master-plan.md`: the feature list, global constraints, status table.
3. The folder of the current feature in `docs/features/`: `design.md`, `plan.md`, `notes.md`.
4. The sections of `poc-plan.md` and `docs/spec/spec-v1.5.md` that the feature names. Do not read the whole spec unless needed; it is 1,000 lines.

`docs/decisions.md` holds every decision with its reason. Check it before changing direction.

## How work is done

One feature at a time, in the order of the master plan (section 6 allows a little overlap). Each feature goes through five steps (`docs/features/README.md` has the detail):

1. Deep dive, written to `design.md`, closed by a question brief the user answers.
2. Short task plan, written to `plan.md`: the tasks in order, grouped into batches, the files each touches, the first failing test of each, the commit. No code in the plan (decision 40).
3. Implementation, test first, one commit per task. Each task runs only the tests it touches; the full suite runs once at the end (decision 73).
4. One review, then verification against the feature's "Done when" in the master plan.
5. Status table, `progress.md`, `decisions.md`, push, pull request, merge when CI is green.

One command runs all five: `/ship FNN` (decision 72). Its exact rules are in `.claude/skills/ship/SKILL.md`. The user's answer to the question brief is the only stop in the normal flow; after it the session builds, reviews once, verifies, opens the pull request and merges it when CI is green.

The flow of one feature: `/ship FNN`, the user answers the brief, the session ships it. Then `/clear` (or a new session) and `/ship` of the next feature. One feature per session keeps the context small; everything a later session needs is in the files.

**The question brief.** A deep dive sorts every open point into a business question, an unclear technical decision or a clear technical decision. The session decides the clear technical decisions alone and logs each in `decisions.md`. It asks the user the first two kinds, in a brief with three parts: "Business questions", "Technical decisions I need your direction on", "Decisions I made myself". There is no limit on the number of questions: the user wants every choice that shapes what is built put to them. Each question has usually 2 to 4 options with a one-line trade-off and one recommendation. When unsure whether a point is clear, it is asked. The user answers with option letters, "OK" for all recommendations, or their own answer. That answer counts as approval of the design and of the plan.

A written design is not an approved one. Do not start step 2 before the user has answered the question brief.

**The user may be on a phone.** Messages are short, with no wide tables; detail goes into the files.

**Anything a later session needs goes into a file, not only the chat.** The chat is cleared between features; a decision, a fact about the data or a blocker that lives only in the chat is lost.

Architecture tests in `backend/src/test/java/com/dsinnovators/ei/architecture/` check the rules that never change. A test disabled with a feature id in its reason is enabled by that feature, in the task that creates what it checks.

A feature starts when the user asks for it. Documents are committed in the same step that changes them. "Checked by hand" in a plan means the session prepares the list and the user checks it.

When something is unclear or a document is wrong, ask. Do not guess, and do not build anything listed under "Out of scope" in the master plan.

## Rules that never change

- `raw_record` is never edited. `evidence_item` and `judgment` are append-only; a correction is a new row with `supersedes_id`.
- The tier lives in `judgment`, not on the evidence item.
- Every judgment stores reason, facts with source links, model, prompt version and input hash. The same input hash is not judged twice.
- The person on an evidence item is the PR author. Code written with AI belongs to the person who pushed it.
- A work item of type Bug is never Feature work.
- Lines of code are never given to the model for complexity.
- For Feature work the model gives complexity; the tier is read from the table (Low T1, Medium T2, High T3, Very high counts as T3).
- The model is called only through the `JudgmentModel` interface.
- Thresholds and weights are rows in `setting`, never constants in code.
- Use the names of spec section 3 and the master plan glossary in code, tables and screens.
- Secrets stay in `.env`. Never print, commit or type a token.

## Code quality

Production-grade, industry-standard clean code is mandatory in this repository. The PoC is the first slice of the real system; nothing is written as throwaway code. This applies to every line produced, tests included.

- Follow the standard conventions of the language and framework: Java and Spring Boot conventions in `backend/`, TypeScript, React and Next.js conventions in `web/`.
- Names say what a thing is or does. No abbreviations that a new reader must look up.
- Classes and methods are small and do one thing. No duplicated logic.
- No dead code. When new code takes over the work of old code, the old code is removed in the same change: if `methodB()` replaces `methodA()` and nothing calls `methodA()` any more, `methodA()` goes. The same holds for a class, a file, a column mapping or a dependency that nothing uses any more.
- Code that a planned feature will use may stay unused until then. It carries a comment that names the feature, and it is listed in the feature's `notes.md`. This is not dead code.
- No commented-out code, no placeholder files, no `TODO` without a feature number.
- Dependencies are injected through constructors. Fields are `final` where they can be. Nothing is more visible than it needs to be.
- Errors are never swallowed. An exception carries a message that says what went wrong and with which value. No secret appears in a message or a log line.
- Inputs are validated at the boundary: API requests, job arguments, connector responses.
- Every behaviour has a test, written first. Tests are held to the same standard as the code they test.
- A comment says why, never what. Public interfaces carry a short description.
- The build is free of compiler warnings and lint errors before a commit. The build and CI enforce this; a check is never switched off to get a commit through.

## Stack

Java 25, Spring Boot 4.x, Maven, PostgreSQL (Docker Compose), Flyway, Next.js (App Router, TypeScript). Backend in `backend/`, front end in `web/`. Exact versions: `docs/decisions.md`.

## Commands

All commands start from the repository root.

Before any backend command, select Java 25 (the machine's default is 21 and the build refuses it):

```
export JAVA_HOME=~/.jdks/temurin-25.0.5
```

On the build machine used from 21 Nov 2026, Java 25 is the system JDK at `/usr/lib/jvm/jdk-25-oracle-x64`; use that path there.

Before any web command, in a shell where `node` is not on the `PATH`:

```
source ~/.nvm/nvm.sh && nvm use
```

```
cp .env.example .env                                         once; .env is never committed
docker compose up -d                                         start the database (host port 5433)
cd backend && ./mvnw verify                                  build, run all backend tests, check the formatting
cd backend && ./mvnw spotless:apply                          format the backend code
cd backend && ./mvnw test -Dtest=ClassName                   run one test class
cd backend && ./mvnw spring-boot:run                         start the backend (port 8080)
cd web && npm install                                        once
cd web && npm run dev                                        start the web app (port 3000)
cd web && npm test                                           run the web tests
cd web && npm run lint                                       lint the web app
cd web && npm run typecheck                                  type-check the web app
cd web && npm run format                                     format the web code (format:check only checks)
curl -s -X POST localhost:8080/api/jobs/github-import/runs \
  -H 'Content-Type: application/json' \
  -d '{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14"}'
                                                             start a job (backend running); answers with the run id
curl -s -X POST localhost:8080/api/jobs/openproject-import/runs \
  -H 'Content-Type: application/json' \
  -d '{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14","quarter":"2026-Q3"}'
                                                             link stored PRs to OpenProject work items (after github-import)
curl -s localhost:8080/api/jobs/runs/1                       see a run: status, counts, error
curl -s -X POST localhost:8080/api/jobs/identity-build/runs \
  -H 'Content-Type: application/json' -d '{}'
                                                             make persons of the stored GitHub accounts and propose their OpenProject users (after openproject-import)
curl -s -X POST localhost:8080/api/jobs/pull-request-digest/runs \
  -H 'Content-Type: application/json' -d '{}'
                                                             Stage 1 digest of every human PR (after github-import; needs LLM_PROVIDER_1_API_KEY in .env); add "pullRequestNumbers" for some PRs only
curl -s -X POST localhost:8080/api/jobs/evidence-build/runs \
  -H 'Content-Type: application/json' -d '{}'
                                                             build the evidence items from the stored records (after identity-build)
curl -s -X POST localhost:8080/api/jobs/feature-work-judgment/runs \
  -H 'Content-Type: application/json' -d '{}'
                                                             Stage 2: complexity of every Feature work item (after evidence-build; needs a provider key)
curl -s -X POST localhost:8080/api/jobs/review-comment-judgment/runs \
  -H 'Content-Type: application/json' -d '{}'
                                                             Stage 2: type and tier of every review comment item (after evidence-build; needs a provider key)
curl -s "localhost:8080/api/developers/9/analytics?quarter=2026-Q3"
                                                             the five analytics and the hygiene number of developer 9 (F10)
curl -s -X POST localhost:8080/api/jobs/dimension-scoring/runs \
  -H 'Content-Type: application/json' -d '{"quarter":"2026-Q3"}'
                                                             score every developer of the quarter at L1, L2 and L3 (after the model jobs; run again after new judgments)
curl -s "localhost:8080/api/developers/9/dimension-scores?quarter=2026-Q3&level=L2"
                                                             the two dimension scores of developer 9 at L2, with gates and trail (F11)
docker compose exec postgres psql -U ei -d ei -c "select confirm_identity_proposal(7);"
                                                             confirm proposal 7 (list them in identity_proposal)
docker compose exec postgres psql -U ei -d ei -c "select reject_identity_proposal(7);"
                                                             reject proposal 7; the next identity-build proposes the next candidate
```

- Backend tests need Docker running. They start their own PostgreSQL container and never use the Compose database.
- Jobs are started only through `POST /api/jobs/{name}/runs`, from the machine the backend runs on. Every run is recorded in `job_run`. A second start of a running job answers `409`. `github-import` takes `repository` and either `mergedFrom` and `mergedTo` or `pullRequestNumbers` (comma-separated).
- `github-import` needs a read-only `GITHUB_TOKEN` in `.env` (a fine-grained token with public repository access). Two weeks take about six minutes.
- `pull-request-digest` calls a free LLM through `LLM_PROVIDER_1_*` (Groq) and, when that one is at its limit, `LLM_PROVIDER_2_*` (Gemini) from `.env` (decisions 144, 146, 158). With every provider at its limit the run stops, counts what is left and succeeds; run it again later. Never several keys of one provider. `feature-work-judgment` and `review-comment-judgment` use the same providers and rules; the three model jobs never run at once (decision 174).
- Tests run with the profile `test` and never read `.env`. They never call GitHub; `GitHubApiFixture` gives a client on a mock server.
- Health: `curl -s localhost:8080/actuator/health`. The web page at `localhost:3000` shows "backend up" or "backend down".
- Evidence list: `localhost:3000/developers` (after `evidence-build`).
- Never start a second backend against the Compose database while a job runs: startup marks every running run as interrupted.
- A test that needs the database extends `com.dsinnovators.ei.DatabaseTest`.
- Do not put quotes around values in `.env`: the backend would read them as part of the value.
- The backend build fails on any compiler warning, any Error Prone finding and any unformatted file. Run `./mvnw spotless:apply` before `./mvnw verify`.
- GitHub Actions (`.github/workflows/ci.yml`) runs the backend build and the web format check, lint, type check, tests and build on every pull request and on `main`. A feature is not done while CI is red.

## End of every session

- Add an entry to `docs/progress.md`: what was done, what is next, what is blocked.
- Update the status table in `docs/master-plan.md`.
- Add any decision made in the session to `docs/decisions.md`.
- Write what was learned while building into the feature's `notes.md`.
