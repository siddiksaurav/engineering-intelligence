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

One feature at a time, in the order of the master plan. Each feature goes through five steps (`docs/features/README.md` has the detail):

1. Deep dive with the user, written to `design.md`. The user approves.
2. Short task plan, written to `plan.md`: the tasks in order, the files each touches, the first failing test of each, the commit. No code in the plan (decision 40). Once it is written, the session goes on to step 3; the user may stop it to review the plan.
3. Implementation, test first, one task at a time, one commit per task.
4. Verification against the feature's "Done when" in the master plan.
5. Update the status table, `progress.md`, `decisions.md` and the commands below.

A written design is not an approved one. Do not start step 2 before the user approves step 1.

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
```

- Backend tests need Docker running. They start their own PostgreSQL container and never use the Compose database.
- No job can be started yet. F02 adds the launcher and the endpoint that starts a job; there is no command-line runner.
- Health: `curl -s localhost:8080/actuator/health`. The web page at `localhost:3000` shows "backend up" or "backend down".
- A test that needs the database extends `com.dsinnovators.ei.DatabaseTest`.
- Do not put quotes around values in `.env`: the backend would read them as part of the value.
- The backend build fails on any compiler warning, any Error Prone finding and any unformatted file. Run `./mvnw spotless:apply` before `./mvnw verify`.
- GitHub Actions (`.github/workflows/ci.yml`) runs the backend build and the web format check, lint, type check, tests and build on every pull request and on `main`. A feature is not done while CI is red.

## End of every session

- Add an entry to `docs/progress.md`: what was done, what is next, what is blocked.
- Update the status table in `docs/master-plan.md`.
- Add any decision made in the session to `docs/decisions.md`.
- Write what was learned while building into the feature's `notes.md`.
