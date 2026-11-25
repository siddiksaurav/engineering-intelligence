# F16 Spec v1.6 alignment: plan

Spec: `docs/features/F16-spec-v1.6-alignment/design.md` (approved 25 Nov 2026; decisions 310 to 326).

Backend paths are under `backend/src/main/java/com/dsinnovators/ei/` (tests under `backend/src/test/java/com/dsinnovators/ei/`, resources under `backend/src/main/resources/`). Web code follows `web/AGENTS.md`.

## Batch 1: tasks 1 to 3 (complexity by problems and depths)

### Task 1. Prompt and schema `feature-work-complexity/v2`
The prompt and the strict schema of design "The prompt". The v1 files stay untouched (decision 325).
- Files: `judgment/prompts/feature-work-complexity-v2.md`, `judgment/schemas/feature-work-complexity-v2.json`; test `judgment/PromptLibraryTest.java`.
- **Test first:** `theFeatureWorkPromptOfVersionTwoNamesTheDepthsTheComplexitiesAndTheExamples`: v2 loads; the prompt names the three depths, the four complexities, the 13 work types and the five examples of spec 6.2; the schema is strict and holds exactly the fields of the design; both file hashes are pinned.
- Commit: `feat(F16): prompt and schema feature-work-complexity/v2`

### Task 2. Digest reason and facts in the Stage 2 input
Under each `[PR-n]` part two more lines, `Digest reason:` and `Digest facts:` (a fact with its file path when it has one). Budget and cuts unchanged (decision 314).
- Files: `judgment/FeatureWorkInput.java`; test `judgment/FeatureWorkInputTest.java`.
- **Test first:** `theTextHoldsTheReasonAndTheFactsOfEachDigest`: a PR part with a digest reason and two facts; the text holds them under that PR; no line count appears.
- Commit: `feat(F16): digest reason and facts in the feature work input`

### Task 3. The v2 answer, the table comparison, the judge on v2
`FeatureWorkAnswer` reads and checks the v2 answer (problems with depth, the flags, `thinEvidence`, the model's `complexity`) and writes the stored value with `earlierExample: "not_checked"` per problem and `complexityByTable` (design flow 4; decisions 311, 323). The table of spec 6.2 is one small class of its own. `FeatureWorkJudge` uses `feature-work-complexity/v2`, writes one fact per problem (decision 310) and reads the tier from the model's complexity. The v1 `Rubric`, `DataChange`, `Integration` and `Fact` go (decision 325). `FakeJudgmentModel` and the fixtures answer in v2.
- Files: `judgment/FeatureWorkAnswer.java`, new `judgment/ComplexityTable.java`, `judgment/FeatureWorkJudge.java`; tests `judgment/FeatureWorkAnswerTest.java`, new `judgment/ComplexityTableTest.java`, `judgment/FeatureWorkJudgeTest.java`, `judgment/FeatureWorkJudgmentJobTest.java`, `judgment/FakeJudgmentModel.java`.
- **Test first:** `ComplexityTableTest.theFiveExamplesOfTheSpecGiveTheirComplexity` (plus all routine, deepest answer exists, no ready answer, two answer exists that affect each other, hidden mistakes, across projects). Then `FeatureWorkAnswerTest.refusesAnAnswerWithoutAProblem` and `keepsTheComplexityOfTheModelWhenTheTableDiffers`; `FeatureWorkJudgeTest.writesOneFactPerProblemUnderVersionTwo` and `aVersionOneJudgmentOfTheItemIsSuperseded`.
- Commit: `feat(F16): feature work judged by problems and depths`

## Batch 2: tasks 4 and 5 (G2 shown, not scored; check notes)

### Task 4. The switch `scoring.gate_scored`
Migration V14 adds the setting row `{"G1": true, "G2": false}` from 2026-Q2 (decision 315). `ScoringSettings` carries it; the reader fails naming the gate and the quarter when a gate with limits has no entry. `DatabaseTest` restores the row like the other scoring settings.
- Files: `db/migration/V14__gate_scored_setting.sql`, `scoring/ScoringSettings.java`, `scoring/ScoringSettingsReader.java`, `DatabaseTest.java` if it lists seed settings; tests `scoring/ScoringSettingsTest.java`, `scoring/ScoringSchemaTest.java`.
- **Test first:** `ScoringSettingsTest.readsWhichGatesAreScored`: G1 scored, G2 not; a row without G2 fails naming G2 and the quarter.
- Commit: `feat(F16): setting that switches a quality gate's scoring`

### Task 5. An unscored gate never changes the score; notes say "check"
`GateResult` gets `scored`. The scorer leaves unscored results out when it lowers the score; the note of an unscored, calculated gate is "G2 not scored yet" whatever the outcome (decision 316). Notes: "check: G1 far", "check unverified: G4"; "held: G1" stays (decision 317). The API sends `scored` with each gate.
- Files: `scoring/GateResult.java`, `scoring/GateCheck.java`, `scoring/DimensionScorer.java`, `scoring/ScoreNotes.java`, `scoring/MappedGate.java` (javadoc), API response classes that copy a gate result; tests `scoring/DimensionScorerTest.java`, `scoring/GateCheckTest.java`, `scoring/DimensionScoringJobTest.java`, API test of the dimension scores.
- **Test first:** `DimensionScorerTest.aFarMissOfAnUnscoredGateLeavesTheScoreAndNotesNotScoredYet`: score 2 before gates, G2 far miss, not scored: final score 2, notes hold "G2 not scored yet" and no "gate"; with the switch on the same numbers give 0 and "check: G2 far". `DimensionScoringJobTest.switchingG2OnInSettingsLowersTheScore`.
- Commit: `feat(F16): G2 shown and not scored, trail notes say check`

## Batch 3: tasks 6 to 8 (web)

### Task 6. The item page shows the problems and depths
For a `feature_work_complexity` judgment with `problems`: the list "Problems solved" (name, depth chip, depth reason, quote linked to its fact's source where one matches, "earlier example not checked"), the two flags with their reasons when true, "thin evidence" when set, and the table line only when `complexityByTable` differs (design flow 6). A judgment without `problems` shows as today.
- Files: `web/lib/items.ts`, `web/app/items/[itemId]/JudgmentView.tsx`, `item.module.css`, `page.test.tsx` (or a new `JudgmentView.test.tsx`); depth colours from `web/lib/palette.ts`.
- **Test first:** `shows each problem with its depth and "earlier example not checked"`; `shows the table line only when it differs`; `a judgment without problems shows as before`.
- Commit: `feat(F16): item page shows problems and depths`

### Task 7. G2 with its result and the tag "not scored"
`GateResult` type gets `scored`. The G2 card keeps its result pill and gets the tag "not scored" (decision 322). The "why this score" and "next step" lines leave an unscored check out (decision 316).
- Files: `web/lib/profile.ts`, `web/lib/profileText.ts`, `web/app/developers/[personId]/profile/QualityChecksSection.tsx`, `profile.module.css`; tests `web/lib/profileText.test.ts`, `profile/page.test.tsx`.
- **Test first:** `profileText.test.ts` `the next step leaves an unscored check out`; `page.test.tsx` `G2 shows its result with the tag "not scored"`.
- Commit: `feat(F16): profile shows G2 as not scored`

### Task 8. "check" in every screen text
"Score before checks", "This check is not calculated in the PoC.", "The check has no result." and any other shown text with "gate", except the spec 6.4 wording in `tierTable.ts` (decision 318). Fixtures in tests use the new note texts.
- Files: `web/app/developers/[personId]/profile/Trail.tsx`, `web/lib/profileText.ts`, tests and fixtures that hold the old texts.
- **Test first:** `no profile text says "gate"`: the rendered profile of a fixture with a far miss, a close miss and an unverified check holds no "gate" outside the tier table wording.
- Commit: `feat(F16): screens say check, not gate`

## After the batches (the session, no implementer)

- `.claude/skills/judge/SKILL.md`: the Feature work stage names `feature-work-complexity/v2` and runs on Opus (decision 326). Commit `docs(F16): judge skill names the v2 prompt`.
- Full tests once, one review, then the run on `ei_latest` (design flow 9) and the checks of "How it will be tested".
