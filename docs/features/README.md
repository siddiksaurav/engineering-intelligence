# How a feature is built

Every feature in `docs/master-plan.md` follows the same five steps and leaves the same three files. One command runs all of them: `/ship FNN` (decision 72). Its exact instructions are in `.claude/skills/ship/SKILL.md`; this page gives the rules it follows.

```
docs/features/F02-github-connector/
  design.md   what the feature does and why, in full
  plan.md     the tasks in batches: files, first failing test, commit
  notes.md    differences from the plan, verification evidence, follow-ups, what a later feature should know
```

The folder name is the feature id and a short name in lower case with hyphens. Features before F04 also have a `summary.md`; from F04 on, the pull request description takes its place (decision 76).

## Step 1: deep dive, written to `design.md`

A feature starts when the user runs `/ship FNN`. It works on branch `feature/FNN`, made from an up-to-date `main`. Set the state to "in design" in the master plan. Start from the feature's block in the master plan. Read the documents it lists under "Read first". Check real data where a question depends on it (call the API, look at a real PR), and do not answer from memory.

Sort every open point into a business question, an unclear technical decision or a clear technical decision. Decide the clear ones alone and log each in `docs/decisions.md`. Ask the user the other two kinds in one question brief, each question with its options, trade-offs and one recommendation (`CLAUDE.md`, "How work is done"). There is no limit on the number of questions; when unsure whether the user wants a say, ask.

Write the result with `_template-design.md`. The user's answer to the question brief approves the design and the plan: record the answers in `docs/decisions.md` and leave no open point in the design.

The design must be readable alone. A later session may not have read the spec. So:
- Any spec table or rule the feature uses is copied into the design in full, with its section number. Examples: the complexity table of spec 6.2, the tier rows of 6.4, the gate limits of 7.3.
- Any interface taken from an earlier feature is written out with its exact names and types.
- Any fact about the real data that the design rests on is stated with the date it was checked.

## Step 2: task plan, written to `plan.md`

A short plan, not line-by-line code (decision 40). Its "Spec" line points to the feature's `design.md`, which carries the detail. Then one entry per task, in build order:

- what the task builds, in one or two lines;
- the files it creates or changes;
- the first failing test, named, with what it checks;
- the commit message.

Tasks are grouped into batches of at most four that touch related files; one implementer builds one batch (decision 74). Close with the command that verifies the whole feature.

## Step 3: implementation

The session coordinates and writes no code. One `implementer` subagent builds each batch, task by task, test first, one commit per task. Each task runs only the tests it touches (decision 73). When the code must differ from the plan, write the difference and the reason into `notes.md` at once. When the difference changes what the feature does, stop and ask.

## Step 4: tests, review, verification

The full test suite runs once, after the last batch. Then one read-only `reviewer` subagent compares the branch with `design.md` and the rules that never change. Blocking findings are fixed and the tests run again; follow-up findings go into `notes.md` (decision 75).

Then each line of the feature's "Done when" in the master plan is checked and the evidence (command and output, or what was checked by hand) recorded in `notes.md`. Long checks on real data run in the background; checks by hand are listed for the user (decision 77).

## Step 5: close

- Set the state to "done" in the master plan.
- Add an entry to `docs/progress.md`.
- Add decisions to `docs/decisions.md`.
- Update the commands in `CLAUDE.md` if they changed.
- If the feature changed something a later feature relies on, update that feature's block in the master plan.
- Push, open the pull request, merge it when CI is green (decision 76).

## `notes.md`

Free form, short. It holds: differences from the plan and why; verification evidence; follow-ups from the review; measurements (API limits met, time and rate limits of LLM runs, row counts); surprises in the real data; things a later feature should know.
