# F15 Deck-style pages on existing data: notes

## Differences from the plan

- Tasks 1 and 2 are one commit (`e1c8f92`): both change the same controller, repository and test file. The implementer wrote test and code in one pass, without a separate red run.
- `QuarterParameter.parse` is a new shared parser in `api`; it replaces the private copies in the analytics and dimension-scores controllers. `CurrentEvidenceItems.countsPerPerson()` is removed; the developer list reuses `ScoredItems.of(quarter)`.
- Between batch 1 and batch 4 the web calls `/api/developers` and the evidence list without a quarter and gets 400; batch 4 fixes the clients.
