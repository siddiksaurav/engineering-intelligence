---
name: build
description: Builds one feature from its approved plan.md. Coordinates implementer and reviewer subagents, checks every "Done when" line and writes summary.md.
disable-model-invocation: true
argument-hint: [feature-id]
---

# Build feature $ARGUMENTS

You are the coordinator. You do not write production or test code yourself: every change to `backend/` or `web/` is made by an `implementer` subagent. You run commands, keep the documents current and report to the user. Follow `CLAUDE.md`. If no feature id was given, stop and ask for one.

## 1. Start

1. Find the folder `docs/features/$ARGUMENTS-*/`. Read `design.md` and `plan.md`, the feature's block in `docs/master-plan.md`, and the top entry of `docs/progress.md`.
2. The branch must be `feature/$ARGUMENTS`, the tree clean, and the state "plan approved" or "in progress". If not, stop and tell the user.
3. Set the state to "in progress" in the status table of `docs/master-plan.md` and commit it: `docs($ARGUMENTS): build started`.
4. If tasks are already done (their commits are in `git log main..HEAD`), go on with the first task that is not.

## 2. One implementer per task

For each task in `plan.md`, in order, start the `implementer` subagent with a prompt that holds only:
- the feature id and the path of `design.md`, which it reads;
- the full text of that one task from `plan.md`;
- the instruction to follow `CLAUDE.md`, sections "Rules that never change" and "Code quality", and the rules of `docs/master-plan.md` section 2.

Run the tasks one at a time, never in parallel: each task builds on the commit of the one before.

When the implementer reports:
- Check that a new commit exists (`git log -1`) and that the tree is clean.
- If it reports a red test, a blocker, or a change to what the feature does, stop and tell the user. Do not start the next task.
- If the code differs from the plan, write the difference and the reason into `notes.md`.

## 3. Full tests

After the last task, run from the repository root:

```
export JAVA_HOME=~/.jdks/temurin-25.0.5
cd backend && ./mvnw verify
source ~/.nvm/nvm.sh && nvm use
cd web && npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

If anything is red, send the output to an implementer to fix, then run all of it again.

## 4. Review, at most 3 rounds

1. Save the branch diff where the reviewer can read it: `mkdir -p .git/ei-review && git diff main...HEAD > .git/ei-review/$ARGUMENTS.diff`.
2. Start the `reviewer` subagent with the feature id, the path of `design.md` and the path of the diff.
3. If it answers APPROVED, go on to step 5.
4. Otherwise send its problems, unchanged, to one `implementer` with the instruction to fix them test first, run all tests and commit (`fix($ARGUMENTS): ...`). Then run step 3 again and review again from step 4.1.
5. After 3 rounds with problems left, stop. Tell the user which problems remain and what was tried. Do not go on.

A reviewer finding you think is wrong is not dropped silently: list it for the user with your reason.

## 5. Check every "Done when" line

For each line of the feature's "Done when" in `docs/master-plan.md`, and each check in the "How it will be tested" section of `design.md`:
- Run the check: the command, the job (`POST /api/jobs/{name}/runs`, see `CLAUDE.md`), the SQL against the Compose database. Record the command and its output.
- A check that needs a person ("checked by hand") is prepared as a list with links for the user; it is marked "waiting for the user", never as passed.
- A check that fails stops the build: tell the user.

Write the evidence into `notes.md`, section "Verification". Add a section "What a later feature should know" if there is anything.

## 6. `summary.md`

Write `docs/features/$ARGUMENTS-*/summary.md`:

```
# $ARGUMENTS Name: summary
## What the feature does
## Files changed            (grouped: backend, migrations, web, docs; from git diff --stat main...HEAD)
## Tables changed           (new tables and columns, new setting rows)
## Decisions made without the user   (with their numbers in docs/decisions.md)
## Test results             (counts of backend and web tests, passed, skipped; the review rounds)
## Done when                (each line: passed, with evidence and links, or waiting for the user)
## Open for the user        (checks by hand, problems left, anything to decide)
```

Evidence links point to GitHub and OpenProject pages and to the sections of `notes.md`.

## 7. Close the session

1. Add an entry to `docs/progress.md`: built and verified, waiting for the user's review and push, then `/done $ARGUMENTS`.
2. Add any decision made during the build to `docs/decisions.md`. Update the commands in `CLAUDE.md` if they changed.
3. Commit: `docs($ARGUMENTS): verification and summary`.
4. Show the user the content of `summary.md`.

Do not push and do not open a pull request; the user reviews and pushes.
