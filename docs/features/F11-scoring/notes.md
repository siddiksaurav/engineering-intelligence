# F11 Scoring: notes

## Build

- Batch 1: added the types `Tier`, `Level`, `Gate`, `PresenceThreshold`, `GateLimits`, `MappedEvidenceType`, `MappedGate` (not named in the plan; later batches need them). `DatabaseTest` truncates the mapping and gate tables and restores their seed rows like the settings; `dimension` is not truncated so its ids stay stable. The `dimension_gate` check allows G1 to G5, `Gate` has G1, G2, G4 only (G3, G5 out of scope): a G3 or G5 row would fail when read. `dimension_score` has no append-only trigger (not asked; decision 213 is held by the code, which only inserts).
- Batch 2: `ScoredItem` carries a `JudgmentState` (judged, not judged yet, not substantive). A frequent type with no judged item has no presence (0 of 0 is not "at least one third"). On the trail, `weight` is the tier weight for Feature work, 1 for other judged items, null otherwise. Under the L3 rule the T3 presence flags stay as computed and `LevelThreeRule.met` decides whether T3 counts. A close miss on a score of 1 or 0 still writes "held: G1". Stand-in notes appear whenever G1 or G2 is applied, whatever the outcome. The gate compare is exact in whole numbers. Tests were written with the code in the same step, not run red first (implementer's own report).
- Batch 3: `AnalyticsSql`, `AnalyticsQuery` made public; the Test change CTE moved into `AnalyticsSql.TEST_CHANGE_ITEM` and `TestingContribution` uses it; `EvidenceType.fromDatabaseName`; test helper `AnalyticsRows` made public for scoring tests. Two readers: `ScoredItems.of(quarter)` (persons and their items) and `GateNumbersQuery.of(quarter, settings, personIds)`; the gate rows are summed in Java from two plain queries. G1's PRs without a digest are left out of the person's, the repository's and the group's numbers and counted apart.
- Batch 4: `dimension_score` is written with `JdbcClient` (no JPA entity: it would be unused). A job cannot know its run id through `Job`; `DimensionScoringJob` reads its own running `job_run` row (safe: the unique index `job_run_one_running_per_job` allows one running run per job). The failing-run test fails before any insert (quarter without settings); no test fails midway through the inserts. A quarter with a succeeded run but no rows for the person answers `scored: false`, like a quarter without a run.

## Follow-ups

From the review (decision 75), not blocking:
- `scoring/GateCheck.java` (`unverifiedReason`): `UnverifiedReason.NO_REPOSITORY_PULL_REQUESTS` cannot happen (the repository includes the person, decision 223); make it an invariant check or remove it.
- `scoring/DimensionMappings.java` and `ScoringSettingsReader.frequentEvidenceTypes` still parse evidence types with `valueOf(toUpperCase)` instead of `EvidenceType.fromDatabaseName`.
- `api/DeveloperDimensionScoresController.parseQuarter` copies `DeveloperAnalyticsController.parse`; one shared helper.
- `api/DeveloperDimensionScoresController.parseLevel` drops the caught exception; `InvalidLevelException` has no constructor with a cause.
- `scoring/DimensionScoreReader.of` collects `calculatedAt` into an outside list as a side effect of the row mapper.
- `scoring/GateNumbersQuery.numbersOf` keys repository PRs by number, not `external_id`; fine for one repository, wrong for two.
- `GateNumbersQueryTest.countsThePullRequestsOfBugFixItemsOfBothKindsWithATestChange` builds only one kind; no G1 test with a superseded digest or Test change.
- `scoring/DimensionMappings` uses `Gate.valueOf` on the row: a G3 or G5 row (allowed by the check) fails without naming the dimension and quarter.
- Visibility: `WorkingTierCalculator.weightOf` could be private; most `scoring` records are public though only `scoring` uses them (`api` needs `DimensionScoreReader`, `Level`, `Tier`, `StoredDimensionScore(s)`).
- The test helper `analytics/AnalyticsRows` was made public for the scoring tests; a shared test-fixture package would be cleaner.

## Verification

Full suites on 22 Nov 2026: backend `./mvnw verify` 979 tests (980 after the review fixes), 0 failures; web format, lint, typecheck, 50 tests, build all green.

Master plan "Done when":
- Spec 9.3 example: `WorkingTierTest.twoLowAndFiveMediumFeatureWorkGiveT2` green (weights 12, 10 at T2 or higher, working tier T2).
- Gate limits at 10 and 25 points on both sides and the fewer-than-three case: `GateCheckTest` (9 tests) green.
- A setting changes the result without a code change: `DimensionScoringJobTest.aNewPresenceThresholdChangesTheWorkingTier` and `aNewGateLimitChangesTheGateResult` green.
- A score recalculated by hand from its trail: below.

Real data, on a throwaway copy of the Compose database (`ei_f11_check`, made with `pg_dump`, deleted after the check; the user asked not to wait for the model runs). A check backend from `backend/target/ei.jar` on port 8081 ran `dimension-scoring` for 2026-Q3: run 61 succeeded, 26 persons, 156 rows (scores at all levels: 0 ×120, 1 ×26, 2 ×10; many items still not judged).

Person 11 (klaustopher), level L2, from the trail of `GET /api/developers/11/dimension-scores?quarter=2026-Q3&level=L2`, checked with separate SQL (current items and judgments of the quarter, PR sources, digests):
- Coding and code quality. Feature work: items 2 and 3 Medium (T2, weight 2 each); 13, 25, 33 not judged yet. At T2 or higher 4 of 4 ≥ 1/3: presence T2; T3 0 of 4. Review comments: item 121 T1; 5 not judged yet; presence T1 only. Working tier T2 = level L2: score 1. No gates. SQL: same items, same tiers.
- Engineering standards and system health. Security item 530 T2 (rare: presence T2). Test change 488 T1, 495 T2, 513 T2: at T2 or higher 2 of 3 ≥ 1/3: presence T2; no T3. Working tier T2: score 1 before gates. G1: person 2 of 2 feature or enhancement PRs tested (23912, 24026; 10 PRs without digest left out), repository 11 of 15, 11 developers: person 100% vs 73.3%, higher: pass. G2: person 0 of 5 Bug fix PRs tested (24028, 24073, 24124, 24168, 24174), repository 22 of 63 (34.9%), 17 developers: lower by 34.9 points > 25: far miss, score 0. G4 "gate unverified". Bug fix items 55, 67, 76 counted, not tiered. Final score 0. After the review fixes, run 62 on the same copy gave the same scores, with every gate number now a list (2 of 2, 11 of 15, 11 developers, 10 without digest; 0 of 5, 22 of 63, 17 developers). SQL: G1 15 / 11 / 11 developers, G2 63 / 22 / 17 developers, person 11's PRs and tested flags the same.

## What a later feature should know

- Run `dimension-scoring` with `{"quarter":"2026-Q3"}` after the model jobs, and again after new judgments: each run inserts new rows; the endpoint reads the newest succeeded run with rows for the quarter.
- Endpoint: `GET /api/developers/{personId}/dimension-scores?quarter=2026-Q3&level=L2` gives `person`, `quarter`, `level`, `scored`, `jobRunId`, `calculatedAt`, `dimensions[]` with `key`, `name`, `workingTier`, `score`, `gates`, `notes`, `trail`. The trail's `evidenceTypes[]` hold `counted`, `notJudgedYet`, `notSubstantive`, `notReadAtLevel` items (id, key, tier, complexity, weight, PR numbers, source link) and `presence` per tier; `gates[]` hold PR number lists for person and repository, the group's person ids and the outcome; `countedNotTiered` holds the Bug fix items.
- F12 links each item id to its evidence item and each PR number to GitHub; G4 always comes back "gate unverified" with reason `NOT_CALCULATED`.
- The check backend for real data needs `LLM_PROVIDER_2_API_KEY=` blank (as `.claude/launch.json` does) because `LLM_PROVIDER_2_MODEL` is empty in `.env`.

