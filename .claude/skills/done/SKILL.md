---
name: done
description: Closes a feature after the user's review and push. Marks it done in the master plan and adds a line to progress.md.
disable-model-invocation: true
argument-hint: [feature-id]
---

# Close feature $ARGUMENTS

If no feature id was given, stop and ask for one.

1. Read `docs/features/$ARGUMENTS-*/summary.md`. If it lists checks still waiting for the user or problems left, show them and ask whether the feature is done anyway. Stop unless the user says yes.
2. Set the state of `$ARGUMENTS` to "done" in the status table of `docs/master-plan.md`.
3. Add a line to the top entry of `docs/progress.md` if it is today's, otherwise a new entry: "`$ARGUMENTS` done", the branch and pull request if known, and what is next (the next feature in the master plan, started with `/design FNN`).
4. Commit on the feature branch: `docs($ARGUMENTS): done`.
5. Tell the user the commit is made and that pushing it is theirs to do.
