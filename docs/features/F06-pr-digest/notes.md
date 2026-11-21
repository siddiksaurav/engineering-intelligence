# F06 JudgmentModel, Stage 1 PR digest, and the builders that need the PR work type: notes

## Batch 1: built for later tasks

Code that nothing calls yet; the `pull-request-digest` job (task 8) uses it.
- `ProviderChain.startRun()`, `providerNames()`, `callsPerProvider()`: the job resets the limits at its start, fails when there is no provider, and writes `calls_<name>`.
- `JudgmentStore.hasModelJudgment(kind, inputHash)`: the skip-on-same-input check before a call.
- `JudgmentStore.write` supersedes the current judgment of the same subject. A new PR record version is a new subject, so the job (task 8) must supersede the digest of the older version of the same PR itself; that needs an explicit `supersedesId` on `NewJudgment`, added with the job's first test for it.
- The client sends no `temperature` (design 2 names it, plan task 4 does not list it as a property); add it if a prompt needs it.
