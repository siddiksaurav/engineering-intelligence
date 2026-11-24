# F15 Deck-style pages on existing data: notes

## Differences from the plan

- Tasks 1 and 2 are one commit (`e1c8f92`): both change the same controller, repository and test file. The implementer wrote test and code in one pass, without a separate red run.
- `QuarterParameter.parse` is a new shared parser in `api`; it replaces the private copies in the analytics and dimension-scores controllers. `CurrentEvidenceItems.countsPerPerson()` is removed; the developer list reuses `ScoredItems.of(quarter)`.
- Between batch 1 and batch 4 the web calls `/api/developers` and the evidence list without a quarter and gets 400; batch 4 fixes the clients.
- Batch 2: no separate red run for tasks 3 and 4 (tests did not compile before the types existed).
- `WorkLevel` takes each item's tier from its current complexity judgment (set from `evidence.complexity_tiers` at judging time), as scoring does; it does not read that setting again. Weights and the line come from `ScoringSettingsReader.in(quarter)`. This keeps the person's share equal to the Coding and code quality trail.
- `analytics.WorkLevel` imports `scoring.ScoringSettingsReader` while `scoring.ScoredItems` imports `analytics`: a package cycle no architecture test forbids (left for the review).
- `workClassification` changes shape to `{distribution, coverage}`. The review analytics carry coverage on their comment breakdown (`comments.coverage`), not a field of their own.
