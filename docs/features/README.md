# How a feature is built

Every feature in `docs/master-plan.md` follows the same five steps and leaves the same three files.

```
docs/features/F02-github-connector/
  design.md   what the feature does and why, in full
  plan.md     the tasks: files, interfaces, tests, commands
  notes.md    what was learned while building, and where the code differs from the plan
```

The folder name is the feature id and a short name in lower case with hyphens.

## Step 1: deep dive, written to `design.md`

A feature starts when the user asks for it. Set the state to "in design" in the master plan. Start from the feature's block in the master plan. Read the documents it lists under "Read first". Then work through its "Open questions" with the user, one at a time, using the brainstorming skill. Check real data where a question depends on it (call the API, look at a real PR), and do not answer from memory.

Write the result with `_template-design.md`. The user approves it. Set the state to "design approved" in the master plan.

The design must be readable alone. The session that implements it may not have read the spec. So:
- Any spec table or rule the feature uses is copied into the design in full, with its section number. Examples: the complexity table of spec 6.2, the tier rows of 6.4, the gate limits of 7.3.
- Any interface taken from an earlier feature is written out with its exact names and types.
- Any fact about the real data that the design rests on is stated with the date it was checked.

## Step 2: task plan, written to `plan.md`

A short plan, not line-by-line code (decision 40). Its "Spec" line points to the feature's `design.md`, which carries the detail. Then one entry per task, in build order:

- what the task builds, in one or two lines;
- the files it creates or changes;
- the first failing test, named, with what it checks;
- the commit message.

Close with the command that verifies the whole feature. Do not use the full-code format of the writing-plans skill: the code is written once, in step 3.

Once the plan is written, set the state to "plan approved" and go on to step 3. The user may stop the session to review the plan first.

## Step 3: implementation

Set the state to "in progress". Work task by task, test first, one commit per task. When the code must differ from the plan, write the difference and the reason into `notes.md` at once. When the difference changes what the feature does, stop and ask.

## Step 4: verification

Run the full test suite. Then check each line of the feature's "Done when" in the master plan and record the evidence (command and output, or what was checked by hand) in `notes.md`. A feature is not done because its tests pass; it is done when "Done when" holds on real data.

## Step 5: close

- Set the state to "done" in the master plan.
- Add an entry to `docs/progress.md`.
- Add decisions to `docs/decisions.md`.
- Update the commands in `CLAUDE.md` if they changed.
- If the feature changed something a later feature relies on, update that feature's block in the master plan.

## `notes.md`

Free form, short. It holds: differences from the plan and why; measurements (API limits met, cost and time of LLM runs, row counts); surprises in the real data; things a later feature should know.
