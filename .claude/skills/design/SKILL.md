---
name: design
description: Deep dive for one feature of the master plan. Writes design.md, decides the clear technical points, shows the user a question brief, and after the answer writes plan.md.
disable-model-invocation: true
argument-hint: [feature-id]
---

# Design feature $ARGUMENTS

You run step 1 and step 2 of `docs/features/README.md` for feature `$ARGUMENTS` (for example `F03`). Follow `CLAUDE.md`. If no feature id was given, stop and ask for one.

## a. Branch

1. `git status` must be clean. If it is not, stop and tell the user.
2. Check that the feature before `$ARGUMENTS` is merged into `main` (`git log main` shows its commits, or its status is "done" and its branch is merged). If it is not, stop and tell the user: a branch from `main` would lack its code.
3. `git checkout main`, then `git checkout -b feature/$ARGUMENTS`.
4. Set the feature's state to "in design" in the status table of `docs/master-plan.md`.

## b. Read, then write `design.md`

Read, in this order:
1. The top entry of `docs/progress.md` and all of `docs/decisions.md`.
2. The feature's block in `docs/master-plan.md` (section 5), and the "Rules that never change".
3. Every section the block lists under "Read first", in `poc-plan.md` and `docs/spec/spec-v1.5.md`. Read only those sections of the spec.
4. "What a later feature should know" in the `notes.md` of every earlier feature.
5. The existing code this feature uses or changes: the classes, tables and migrations it names or depends on.

Where a question depends on the real data, look at the real data (call the public API, open a real PR) and write down the date you checked it. Do not answer from memory.

Create `docs/features/$ARGUMENTS-short-name/design.md` (the short name in lower case with hyphens, from the feature's title). Use exactly these headings:

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

The design must be readable alone (`docs/features/README.md`, step 1): copy every spec table or rule it uses with its section number, write out every interface taken from an earlier feature with exact names and types, date every fact about the real data. Map the master plan's "Done when" lines into "How it will be tested". Nothing listed under "Out of scope" in the master plan may appear as in scope; if you think it is needed, it is a business question.

## c. Sort the open points

Put every open point under "Open points", in one of three groups. Every "Open question" of the feature's block is an open point.

- **Business question**: what counts, who gets credit, what a number means, or a choice that would change a number in a way a manager would notice or disagree with.
- **Unclear technical decision**: the documents and the existing code give no clear direction, and the options have real trade-offs or would be costly to change later.
- **Clear technical decision**: the documents, the existing code or normal good practice point to one answer.

If you are unsure whether a point is clear or unclear, it is unclear.

## d. Decide the clear technical decisions

Decide each clear technical decision yourself. Write it into the design, and add it to `docs/decisions.md` with the reason and the source "$ARGUMENTS design, session". Do not ask the user about these.

Commit: `docs($ARGUMENTS): design draft, question brief`.

## e. Show the question brief

Show the user a brief with exactly three parts:

```
QUESTION BRIEF: $ARGUMENTS name

1. Business questions
   Q1. <the question in plain words>
       A. <option> — <one-line trade-off>
       B. <option> — <one-line trade-off>   AI recommendation: <why, one line>
       ...
2. Technical decisions I need your direction on
   Q5. ...
3. Decisions I made myself (overrule any by number)
   D51. <one line>
   ...

Reply with option letters (Q1 B, Q2 A ...), "OK" to accept all recommendations, or your own answer.
```

Rules for parts 1 and 2:
- Plain words. No spec section numbers or code terms unless the question cannot be asked without them.
- Usually 2 to 4 options, each with a one-line trade-off. A yes/no question may have two.
- Exactly one option per question is marked "AI recommendation", with the reason in one line.
- The questions with the most impact come first. Number them across both parts.
- If parts 1 and 2 hold more than 8 questions together, group the small ones into one question with sub-points.

Part 3 lists each decision of step d in one line, with its number in `docs/decisions.md`.

Also write the brief into `design.md` under "Open points", so a later session can read it. Then stop and wait for the answer.

## f. After the answer

The user's answer counts as approval of the design and the plan.

1. Record each answer in `docs/decisions.md`: the decision, the reason the user gave (or "user's choice"), the source "$ARGUMENTS question brief, user". An overruled decision of part 3 gets a new entry that names the one it replaces.
2. Update `design.md`: write each answer into the section it belongs to, and replace "Open points" with "Decisions" listing every decision with its number in `docs/decisions.md`. No open point is left. Set `State: approved on <date>, through the question brief`.
3. Write `plan.md` in the same folder, in the short form of `docs/features/README.md`, step 2 (decision 40): a "Spec" line pointing to `design.md`, then one entry per task in build order, each small enough for one commit:
   - what it builds, in one or two lines;
   - the files it creates or changes;
   - **Test first:** the name of the first failing test and what it checks;
   - the commit message, `feat($ARGUMENTS): ...` or `test($ARGUMENTS): ...`.
   An architecture test in `backend/src/test/java/com/dsinnovators/ei/architecture/` that names this feature in its `@Disabled` reason is enabled in the task that creates what it checks. Close the plan with the commands that verify the whole feature. No code in the plan.
4. Create an empty `notes.md` in the folder with the heading `# $ARGUMENTS Name: notes`.
5. Set the state to "plan approved" in the status table and fill in the Design and Plan columns.
6. Add an entry to `docs/progress.md`: design approved through the question brief, plan written, next is `/build $ARGUMENTS`.
7. Commit: `docs($ARGUMENTS): design approved, plan`.
8. End with exactly: `Plan ready. Run /build $ARGUMENTS.`
