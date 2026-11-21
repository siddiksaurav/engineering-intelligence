# F06 JudgmentModel, Stage 1 PR digest, and the builders that need the PR work type: notes

## Batch 1: built for later tasks

Code that nothing calls yet; the `pull-request-digest` job (task 8) uses it.
- `ProviderChain.startRun()`, `providerNames()`, `callsPerProvider()`: the job resets the limits at its start, fails when there is no provider, and writes `calls_<name>`.
- `JudgmentStore.hasModelJudgment(kind, inputHash)`: the skip-on-same-input check before a call.
- `JudgmentStore.write` supersedes the current judgment of the same subject. A new PR record version is a new subject, so the job (task 8) must supersede the digest of the older version of the same PR itself; that needs an explicit `supersedesId` on `NewJudgment`, added with the job's first test for it.
- The client sends no `temperature` (design 2 names it, plan task 4 does not list it as a property); add it if a prompt needs it.

## Differences from the plan

- Batch 1: extra classes `JudgmentProvider`, `CallPolicy`, `JudgmentSubject`, `JudgedBy` (the client needed the provider shape before task 4's properties). The provider error text is cut at `ei.judgment.max-error-characters`, a property, because `judgment` holds no number constant (decision 53). `judgment.confidence` is not mapped on the entity: F06 never reads it; F07 maps it. `NewJudgment` has one constructor and no factory.
- Task 6: the docs group that orders patches (`docs/`, `*.md`, `*.adoc`, `*.rdoc`, `*.txt`) is a constant in `DiffFileFilter`, not a setting: it only decides which patches are dropped first when the budget is short and decides no evidence; the test group reads `evidence.test_paths`. A patch that does not fit is left out and the next, smaller ones are still tried. A file GitHub gave no patch for (binary or very large) is named, and does not set `judgedOnPartialCode`; the `truncated` flag of the diff record is not read.
- Task 7: extra class `InvalidDigestException` (the answer check needs an exception the job can retry on) and a test `DigestFactsTest`. The yes/no fields of the digest are JSON booleans. `workTypeTier` is an object `{tier, reason}` with `tier` one of T1, T2, T3, not_applicable (the strict schema allows no null); an answer with work type refactor, performance, security or infra and no tier is refused. A fact with a changed file links to `<PR url>/files` (no per-file anchor, as the design says) and its text carries the path in brackets; a path not in the PR keeps the PR link. The yes/no, enum and text values of the answer are checked; extra fields are ignored. `PromptLibraryTest` pins the SHA-256 of the prompt and of the schema file.
