---
name: ship
description: Takes one feature of the master plan from deep dive to merged pull request. Design and question brief, then after the user's answer the plan, the build, one review, verification, push, pull request and merge.
disable-model-invocation: true
argument-hint: [feature-id]
---

# Ship feature $ARGUMENTS

You run every step of `docs/features/README.md` for feature `$ARGUMENTS` (for example `F04`) in one session. Follow `CLAUDE.md`. If no feature id was given, stop and ask for one.

The user may be on a phone. Every message to the user is short: plain lines, no wide tables, no long code blocks. Details go into the feature's files, not the chat.

The user's answer to the question brief is the only stop in the normal flow. After it, go on to the end without asking, unless a stop below applies.

## A. Branch

1. `git status` must be clean. If it is not, stop and tell the user.
2. `git checkout main && git pull`. The features this one depends on (`docs/master-plan.md`, section 5) must be "done" and merged into `main`. If one is not, stop and tell the user.
3. `git checkout -b feature/$ARGUMENTS`. If the branch exists, check it out and go on from the first step not yet done (read `design.md`, `plan.md`, `notes.md` and `git log main..HEAD`).
4. Set the state to "in design" in the status table of `docs/master-plan.md`.

## B. Read, then write `design.md`

Read, in this order:
1. The top entry of `docs/progress.md`. In `docs/decisions.md`, every decision from 72 on, and the earlier ones that name this feature or the tables and packages it touches.
2. The feature's block in `docs/master-plan.md` (section 5), and section 2 "Global constraints".
3. Every section the block lists under "Read first", in `poc-plan.md` and `docs/spec/spec-v1.5.md`. Read only those sections of the spec.
4. "What a later feature should know" in the `notes.md` of every earlier feature.
5. The existing code this feature uses or changes: the classes, tables and migrations it names or depends on.

Where a question depends on the real data, look at the real data (call the public API, open a real PR) and write down the date you checked it. Do not answer from memory.

Create `docs/features/$ARGUMENTS-short-name/design.md` (the short name in lower case with hyphens, from the feature's title) with exactly these headings:

```
# $ARGUMENTS Name: design
State: draft
## Goal
## In scope / Out of scope
## Data and tables
## Main flows
## Edge cases
## How it will be tested
## Open points
```

The design must be readable alone: copy every spec table or rule it uses with its section number, write out every interface taken from an earlier feature with exact names and types, date every fact about the real data. Map the master plan's "Done when" lines into "How it will be tested". Nothing listed under "Out of scope" in the master plan may appear as in scope; if you think it is needed, it is a business question.

## C. Sort the open points

Put every open point under "Open points", in one of three groups. Every "Open question" of the feature's block is an open point.

- **Business question**: what counts, who gets credit, what a number means, what a screen shows, or a choice that would change a number in a way a manager would notice or disagree with.
- **Unclear technical decision**: the documents and the existing code give no clear direction, and the options have real trade-offs or would be costly to change later.
- **Clear technical decision**: the documents, the existing code or normal good practice point to one answer.

If you are unsure whether a point is clear or unclear, it is unclear. If you are unsure whether the user would want a say, ask. There is no limit on the number of questions (decision 72): the user wants every choice that shapes what is built put to them.

## D. Decide the clear technical decisions

Decide each clear technical decision yourself. Write it into the design, and add it to `docs/decisions.md` with the reason and the source "$ARGUMENTS design, session".

Commit: `docs($ARGUMENTS): design draft, question brief`.

## E. Show the question brief

Show the user a brief with three parts, laid out for a phone screen:

```
QUESTION BRIEF: $ARGUMENTS name

1. Business questions

Q1. <the question in plain words>
A. <option> — <one-line trade-off>
B. <option> — <one-line trade-off>
Recommended: B — <why, one line>

2. Technical decisions I need your direction on

Q5. ...

3. Decisions I made myself (overrule any by number)

D82. <one line>

Reply with letters (Q1 B, Q2 A ...), "OK" for all recommendations, or your own answer.
```

Rules for parts 1 and 2:
- Plain words. No spec section numbers or code terms unless the question cannot be asked without them.
- Usually 2 to 4 options, each with a one-line trade-off. A yes/no question may have two.
- Exactly one option per question is recommended, with the reason in one line.
- The questions with the most impact come first. Number them across both parts.
- Ask every question that needs asking. Do not merge or drop questions to keep the brief short.

Part 3 lists each decision of step D in one line, with its number in `docs/decisions.md`.

Also write the brief into `design.md` under "Open points", so a later session can read it. Then stop and wait for the answer.

If the answer raises a new question or is unclear, ask a follow-up before going on. A guess here builds something the user does not want.

## F. After the answer: decisions and plan

The user's answer counts as approval of the design and the plan.

1. Record each answer in `docs/decisions.md`: the decision, the reason the user gave (or "user's choice"), the source "$ARGUMENTS question brief, user". An overruled decision of part 3 gets a new entry that names the one it replaces.
2. Update `design.md`: write each answer into the section it belongs to, and replace "Open points" with "Decisions" listing every decision with its number. No open point is left. Set `State: approved on <date>, through the question brief`.
3. Write `plan.md` in the same folder (decision 40): a "Spec" line pointing to `design.md`, then one entry per task in build order, each small enough for one commit:
   - what it builds, in one or two lines;
   - the files it creates or changes;
   - **Test first:** the name of the first failing test and what it checks;
   - the commit message, `feat($ARGUMENTS): ...` or `test($ARGUMENTS): ...`.
   Group the tasks into batches of at most four that touch related files; mark each batch (`Batch 1: tasks 1 to 3`). An architecture test that names this feature in its `@Disabled` reason is enabled in the task that creates what it checks. No code in the plan.
4. Create `notes.md` with the heading `# $ARGUMENTS Name: notes`.
5. Set the state to "in progress" in the status table and fill in the Design and Plan columns.
6. Commit: `docs($ARGUMENTS): design approved, plan`.
7. Tell the user in one line that the build has started. Go on at once.

## G. Build, one implementer per batch

You coordinate. You do not write production or test code yourself: every change to `backend/` or `web/` is made by an `implementer` subagent.

For each batch of `plan.md`, in order, start one `implementer` subagent with a prompt that holds only:
- the feature id and the path of `design.md`, which it reads;
- the full text of the batch's tasks from `plan.md`;
- the instruction to follow `CLAUDE.md`, sections "Rules that never change" and "Code quality", and the rules of `docs/master-plan.md` section 2.

Batches run one at a time, never in parallel: each builds on the commits of the one before.

When the implementer reports:
- Check that one new commit per task exists (`git log`) and that the tree is clean.
- If it reports a red test, a blocker, or a change to what the feature does, stop and tell the user. Do not start the next batch.
- If the code differs from the plan, write the difference and the reason into `notes.md`.

## H. Full tests, once

After the last batch, run from the repository root:

```
export JAVA_HOME=~/.jdks/temurin-25.0.5
cd backend && ./mvnw verify
source ~/.nvm/nvm.sh && nvm use
cd web && npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

Run the backend and web checks in parallel where you can. If anything is red, send the output to an implementer to fix, then run only the failed part again.

## I. One review round

1. Save the branch diff: `mkdir -p .git/ei-review && git diff main...HEAD > .git/ei-review/$ARGUMENTS.diff`.
2. Start the `reviewer` subagent with the feature id, the path of `design.md` and the path of the diff.
3. The reviewer sorts its findings into "Blocking" and "Follow-up" (decision 75).
4. If there are blocking findings, send them, unchanged, to one `implementer` with the instruction to fix them test first and commit (`fix($ARGUMENTS): ...`). Then run step H again. There is no second review: read the fix diff yourself against each finding.
5. Write every follow-up finding into `notes.md`, section "Follow-ups", with the file and the reason.

A blocking finding you think is wrong is not dropped silently: write it into `notes.md` with your reason and tell the user.

## J. Verification

For each line of the feature's "Done when" in `docs/master-plan.md`, and each check in "How it will be tested" of `design.md` (decision 77):
- A check that runs in under two minutes (a test, a SQL count against the Compose database) is run now.
- A check that needs a job on real data is started through `POST /api/jobs/{name}/runs` (see `CLAUDE.md`) in the background. Do not wait for it before step K; record the run id. If it finishes while the session is open, record its result.
- A check that needs a person is prepared as a list with links for the user, marked "waiting for the user".
- A check that fails is told to the user at once, with its output. It does not stop step K unless it shows the feature is wrong.

Write the evidence into `notes.md`, section "Verification". Add a section "What a later feature should know" if there is anything.

## K. Close, push, pull request, merge

1. Set the state to "done" in the status table.
2. Add an entry to `docs/progress.md`: done, the pull request, the checks still open, what is next (`/ship` of the next feature).
3. Add any decision made during the build to `docs/decisions.md`. Update the commands in `CLAUDE.md` if they changed.
4. Commit: `docs($ARGUMENTS): verification, done`.
5. `git push -u origin feature/$ARGUMENTS` and open a pull request with `gh pr create`. Its description holds: what the feature does, the tables changed, the decisions made without the user (by number), the test counts, each "Done when" line (passed, running, or waiting for the user), and the follow-ups.
6. Wait for CI. In the Claude desktop app, use its pull request tools; elsewhere run `gh pr checks --watch` in the background. If CI is red, send the failure to an implementer, push the fix, and wait again.
7. When CI is green, merge: `gh pr merge --merge --delete-branch`, then `git checkout main && git pull`.
8. Tell the user in a few lines: merged, the pull request link, the checks waiting for them, and that the next step is `/clear` then `/ship` of the next feature.
