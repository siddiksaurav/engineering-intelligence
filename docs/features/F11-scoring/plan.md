# F11 Scoring: plan

Spec: `docs/features/F11-scoring/design.md` (approved 22 Nov 2026; decisions 201 to 229).

## Batch 1: tasks 1 to 3 (schema, settings, mapping)

### Task 1. Migration V12
Tables `dimension`, `dimension_evidence_type`, `dimension_gate`, `dimension_score` with their seeds (two dimensions, mapping with `tiered` and `from_level`, gates with `applied`, all valid from 2026-Q2) and the seven `scoring.*` settings (design, "Migration V12"). `dimension_score` added to the truncate of `DatabaseTest`; the seed rows of the mapping are kept or restored like the seeded settings.
- Files: `backend/src/main/resources/db/migration/V12__scoring.sql`, `backend/src/test/java/com/dsinnovators/ei/DatabaseTest.java`, a new schema test in `backend/src/test/java/com/dsinnovators/ei/scoring/`.
- **Test first:** `ScoringSchemaTest.seedMapsTheTwoDimensionsToTheirEvidenceTypesAndGates`: the seed holds the rows of design "Migration V12" (Review comment written from L2, Bug fix not tiered, G4 not applied).
- Commit: `feat(F11): migration V12, dimension tables and scoring settings`

### Task 2. Scoring settings reader
Package `scoring` with a reader that turns the `scoring.*` settings in force for a quarter into one value (threshold fraction, weights by tier, frequent types, T3 items needed at L3 by dimension, gate limits, minimum developers, minimum PRs per developer); a missing key fails naming it. Enables `SpecNumbersTest.theScoringPackageHoldsNoNumberConstant`.
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/ScoringSettings*.java`, `SpecNumbersTest.java`, test `ScoringSettingsTest`.
- **Test first:** `ScoringSettingsTest.readsTheSeededValuesFor2026Q3`.
- Commit: `feat(F11): scoring settings for a quarter`

### Task 3. Dimension mapping reader
Reads the mapping and gates of each dimension in force for a quarter (latest `valid_from_quarter` not after the quarter, per dimension) with the version used.
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/DimensionMapping*.java`, test `DimensionMappingTest`.
- **Test first:** `DimensionMappingTest.aMappingValidFromALaterQuarterDoesNotChangeAnEarlierOne`.
- Commit: `feat(F11): dimension mapping in force for a quarter`

## Batch 2: tasks 4 to 6 (rules, no database)

### Task 4. Presence and working tier
Plain Java over a person's scored items of one dimension at one level: frequent types by share (Feature work by weight, others by count), rare types by one item; unjudged, not substantive and not-read-at-level items left out and kept for the trail (decisions 218, 219, 221); the L3 two-T3 rule (decision 220); exact fraction compare (decision 207).
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/` (working tier classes), test `WorkingTierTest`.
- **Test first:** `WorkingTierTest.twoLowAndFiveMediumFeatureWorkGiveT2` (spec 9.3: weights 12, 10 at T2 or higher).
- Commit: `feat(F11): presence and working tier`

### Task 5. Gate check
One gate from the person's and the repository's tested and total PRs and the number of developers in the group: pass, close miss, far miss, or unverified (fewer developers than the minimum, or no PR of the kind for the person) with the difference in points (decisions 209, 222, 223, 224).
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/` (gate classes), test `GateCheckTest`.
- **Test first:** `GateCheckTest.exactlyTenPointsLowerPasses`; then 9.99, 10.01, 25, 25.01, above, two developers, no PR.
- Commit: `feat(F11): gates G1 and G2`

### Task 6. Dimension score and trail
Score from working tier and level (0, 1, 2), gate effects (any far miss 0, close miss 2 to 1), notes ("held: G1", "gate: G2 far", "gate unverified"), G4 unverified, Bug fix "counted, not tiered", stand-ins and the clean-delivery note; the trail value of design "The trail".
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/` (scorer, trail records), test `DimensionScorerTest`.
- **Test first:** `DimensionScorerTest.aCloseMissHoldsATwoAtOne`.
- Commit: `feat(F11): dimension score with gates and trail`

## Batch 3: tasks 7 and 8 (queries)

### Task 7. Scored items query
`AnalyticsSql`'s shared pieces made public (decision 215). One query for the persons to score in a quarter (decision 227) and their current items of the quarter with tier, complexity, PR numbers, item key and source link.
- Files: `backend/src/main/java/com/dsinnovators/ei/analytics/AnalyticsSql.java`, `backend/src/main/java/com/dsinnovators/ei/scoring/` (items query), test `ScoredItemsTest`.
- **Test first:** `ScoredItemsTest.readsCurrentItemsOfTheQuarterWithTheTierOfTheirKind`.
- Commit: `feat(F11): read the scored items of a quarter`

### Task 8. Gate numbers query
For every person: G1 (human PRs merged in the quarter, digest feature or enhancement, with a Test change item; PRs without digest counted apart) and G2 (PRs of current Bug fix items in the quarter, both kinds, with a Test change item), with PR numbers.
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/` (gate numbers query), test `GateNumbersTest`.
- **Test first:** `GateNumbersTest.countsFeatureAndEnhancementPullRequestsWithATestChange`.
- Commit: `feat(F11): gate numbers of every developer`

## Batch 4: tasks 9 and 10 (job and API)

### Task 9. Job `dimension-scoring`
Argument `quarter` (required, checked). Reads in one repeatable-read snapshot, scores every scored person, both dimensions, L1 to L3, and inserts all rows in one transaction with the run id; counts `persons` and `scores_written`.
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/` (job, store, entity), test `DimensionScoringJobTest`.
- **Test first:** `DimensionScoringJobTest.scoresEveryPersonInBothDimensionsAtThreeLevels`; also `aNewPresenceThresholdChangesTheWorkingTier` and `aNewGateLimitChangesTheGateResult` (setting changes the result).
- Commit: `feat(F11): job dimension-scoring`

### Task 10. Endpoint
`GET /api/developers/{personId}/dimension-scores?quarter=&level=` reads the rows of the newest succeeded run for the quarter (decisions 217, 229): `person`, `quarter`, `level`, `scored`, `jobRunId`, `calculatedAt`, `dimensions` (key, name, working tier, score, gates, notes, trail). 404 unknown person, 400 missing or wrong quarter or level.
- Files: `backend/src/main/java/com/dsinnovators/ei/scoring/` (reader), `backend/src/main/java/com/dsinnovators/ei/api/` (controller, response), test `DeveloperDimensionScoresControllerTest`.
- **Test first:** `DeveloperDimensionScoresControllerTest.returnsBothDimensionsOfTheNewestRunAtTheSelectedLevel`.
- Commit: `feat(F11): dimension scores endpoint`
