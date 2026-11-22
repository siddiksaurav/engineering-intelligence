# F11 Scoring: notes

## Build

- Batch 1: added the types `Tier`, `Level`, `Gate`, `PresenceThreshold`, `GateLimits`, `MappedEvidenceType`, `MappedGate` (not named in the plan; later batches need them). `DatabaseTest` truncates the mapping and gate tables and restores their seed rows like the settings; `dimension` is not truncated so its ids stay stable. The `dimension_gate` check allows G1 to G5, `Gate` has G1, G2, G4 only (G3, G5 out of scope): a G3 or G5 row would fail when read. `dimension_score` has no append-only trigger (not asked; decision 213 is held by the code, which only inserts).
- Batch 2: `ScoredItem` carries a `JudgmentState` (judged, not judged yet, not substantive). A frequent type with no judged item has no presence (0 of 0 is not "at least one third"). On the trail, `weight` is the tier weight for Feature work, 1 for other judged items, null otherwise. Under the L3 rule the T3 presence flags stay as computed and `LevelThreeRule.met` decides whether T3 counts. A close miss on a score of 1 or 0 still writes "held: G1". Stand-in notes appear whenever G1 or G2 is applied, whatever the outcome. The gate compare is exact in whole numbers. Tests were written with the code in the same step, not run red first (implementer's own report).
- Batch 3: `AnalyticsSql`, `AnalyticsQuery` made public; the Test change CTE moved into `AnalyticsSql.TEST_CHANGE_ITEM` and `TestingContribution` uses it; `EvidenceType.fromDatabaseName`; test helper `AnalyticsRows` made public for scoring tests. Two readers: `ScoredItems.of(quarter)` (persons and their items) and `GateNumbersQuery.of(quarter, settings, personIds)`; the gate rows are summed in Java from two plain queries. G1's PRs without a digest are left out of the person's, the repository's and the group's numbers and counted apart.
- Batch 4: `dimension_score` is written with `JdbcClient` (no JPA entity: it would be unused). A job cannot know its run id through `Job`; `DimensionScoringJob` reads its own running `job_run` row (safe: the unique index `job_run_one_running_per_job` allows one running run per job). The failing-run test fails before any insert (quarter without settings); no test fails midway through the inserts. A quarter with a succeeded run but no rows for the person answers `scored: false`, like a quarter without a run.

## Follow-ups

## Verification
