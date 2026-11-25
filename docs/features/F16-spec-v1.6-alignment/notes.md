# F16 Spec v1.6 alignment: notes

## Differences from the plan

- Batch 1 (`0b277f3`, `e8f821c`, `f274bbe`): as planned. `ComplexityTable.complexityOf(depths, problemsAffectEachOther, mistakesStayHidden, reachesAcrossProjects)` writes "two or more" without a number constant (more than one), so `SpecNumbersTest` has nothing to object to. The fact of a problem reads `Problem name: answer exists, "quote"`. Two v1 enum assertions left `PromptLibraryTest` with the v1 enums; the v1 files and their pinned hashes stay. The implementer wrote tests first but ran only the green run.
- Batch 2 (`d35f534`, `bcaa6d0`): `ScoringSettings.gateScored` (`Map<Gate, Boolean>`); the reader asks for an entry for every gate that has limits. `GateCheck` sets `scored` and the note; `ScoreNotes.notScoredYet`. The note "G2 not scored yet" is written whatever the outcome, pass and unverified included. G4 (never calculated) has `scored: false` and the note "check unverified: G4". `scored` sits in `dimensions[].gates[]` and in the trail's `gates`, next to `outcome`; the API passes the stored JSON through, so a row of an earlier run has no `scored` key. Tests and code were written together, without a separate red run.
- Batch 3 (`ab5d94c`, `8e901eb`, `788fcbc`): new `web/app/items/[itemId]/ProblemsSolved.tsx` and `web/lib/featureWorkProblems.ts` (types and reader of the v2 value; not in `lib/items.ts`, which the page tests mock). For a v2 judgment the problems list replaces the plain Facts list; each quote (or the name, when the quote is empty) links to the fact at the same position. Depth colours in `palette.ts`. `GateResult.scored` is optional on the web: a row of an earlier run has no key and counts as scored. The tag "not scored" shows only for `scored === false` on a calculated check (not G4). `isScored` and `isShownNotScored` in `profileText.ts`. The check header wraps at phone width.
- `.claude/skills/judge/SKILL.md`: a Model column per stage; Feature work is `feature-work-complexity/v2` on Opus, and `answeredBy` names the model that answered.
- During the run the user changed the model of the Feature work stage from Opus to Sonnet (decision 327, replaces 326): Opus would use too much of their Claude usage.

## Review (one round, 25 Nov 2026)

Nothing blocking. Fixed as follow-ups in `9aa05fa`: `ScoreNotesTest` (no note of any gate and outcome holds "gate"); `ComplexityTable` counts "two or more" plainly with a comment citing decision 313 (`SpecNumbersTest` still passes); React keys of the problems by position and name; `web/lib/words.ts` replaces two copies of `spaced` and `capitalised`; `featureWorkProblems` checks the fields of the value and falls back to the Facts view when one is wrong; `isScored` no longer exported; `FeatureWorkInputTest` builds its hashes with the v2 version; a web test for G2 unverified and not scored; a fixture note the backend never writes replaced; javadoc of `FeatureWorkAnswer` completed.

## Follow-ups

- `backend/src/main/resources/judgment/prompts/feature-work-complexity-v2.md`, the five examples: the table leaves out the spec's column "Trace the AI needs" and the tiers after each complexity. On purpose for the tiers (the model gives no tier); the trace column was dropped by the implementer and tells the model little. The 42 judgments rest on this text and the hash is pinned: a change is a new prompt version.
- `judgment/FeatureWorkInput.java` (digest facts) and `judgment/DigestFacts.java` both write "fact text (file path)". Not merged in the review round, because the input text must not change by one character once hashes are stored; share one formatter when a new prompt version is made anyway.
- `web/lib/featureWorkProblems.ts`: the boundary check has a test for an unknown depth only, not for a wrong flag or a missing text.
- One stored quote (request `fef43718...`) differs from the input by an HTML-escaped apostrophe (`&#39;`): the first answer was taken before the subagent corrected it. The code does not check that a quote is in the input; F08 reads the quotes when it marks reasons.

## Verification (25 Nov 2026)

Full suites after the review fixes: backend `./mvnw verify` 1,061 tests, 0 failures, 0 skipped. Web: format check, lint, type check, 264 tests, build green.

Run on `ei_latest` (dump before it: `~/ei_latest-before-F16-2026-11-25.dump`). Backend from the jar built at `9b08d66` (before the review fixes, which change no behaviour of the run), `POSTGRES_DB=ei_latest JUDGMENT_MODE=exchange`; V14 applied at start. Run 76 exported 42 requests of `feature-work-complexity/v2`; six Sonnet subagents answered them (42 times 204); run 77 stored 42 judgments, none refused; run 78 `dimension-scoring` for 2026-Q3 wrote 156 scores for 26 persons. The backend was stopped afterwards.

Master plan "Done when":
1. Every Feature work item has a current v2 judgment with at least one problem and a depth on each: passed. SQL: 42 current `feature_work_complexity` judgments, all `feature-work-complexity/v2` by `claude-sonnet-5-5 via claude-code`; 0 current items without one; 0 problems with a depth outside the three values; 0 facts without a link.
2. The item page of one item shows them: passed. The session read `/items/1` on the production build: "Problems solved", each problem with its depth, depth reason, quote and "earlier example not checked". The user has not opened an item page.
3. A G2 far miss leaves the score and the trail says "G2 not scored yet": passed. `DimensionScorerTest.aFarMissOfAnUnscoredGateLeavesTheScoreAndNotesNotScoredYet` and `DimensionScoringJobTest.switchingG2OnInSettingsLowersTheScore` green. On `ei_latest` at L2, six developers have a G2 far miss with `scored: false` in Engineering standards; for each the score equals the score before gates (persons 8, 16 and 22: 1, was 0 in run 75). The profile of person 8 shows "Far miss" with the tag "not scored".
4. No screen and no trail note says "gate": passed. SQL: 0 scores of run 78 with "gate" in the notes; the distinct notes are "check: G1 far", "check unverified: G1", "check unverified: G4", "held: G1", "G2 not scored yet" and the stand-in and counted notes. The pages `/`, `/developers`, `/developers/9`, `/developers/compare?a=9&b=10` hold no "gate"; the profile holds it twice, both allowed: "A test gate in CI for a module" (decision 318) and the title of a Feature work item of OpenProject, "Overview widget for project phases and gates" (data, not a screen text).
5. The count at Low, Medium and High: **13 Low, 19 Medium, 10 High, 0 Very high** (v1: 23, 17, 2). Each has 6 or more, so by decision 307 **F09 does not run before F08**.

More numbers of the run, for F08:
- v1 to v2 per item: Low stayed Low 13, Low to Medium 8, Low to High 2, Medium stayed 11, Medium to High 6, High stayed 2. No item went down. The model is the same (Sonnet), but two things changed at once: the rule (problems and depths, with decision 320: no earlier example assumed) and the input (digest reason and facts). The shift cannot be split between them.
- Depths of the 117 problems: 70 answer exists, 47 routine, 0 no ready answer.
- The model against the table of spec 6.2 (`complexityByTable`): 39 of 42 equal; 3 items the model called Medium where the table gives High (the table gives 13 Low, 16 Medium, 13 High).
- Flags: 6 items thin evidence, 13 problems affect each other, 7 mistakes stay hidden, 0 reach across projects.
- Scores, run 75 to run 78 (all levels): Coding and code quality 5 scores went 0 to 1 and 5 went 1 to 2; Engineering standards 5 went 0 to 1, 1 went 0 to 2, 1 went 1 to 2. None went down.

Waiting for the user: nothing for F16. The user still does not open item pages of Feature work items before the F08 labels are imported.

## What a later feature should know

- `feature_work_complexity` value under v2: `workType`, `problems[]` (`name`, `depth`, `depthReason`, `quote`, `source`, `earlierExample`), `problemsAffectEachOther` and its reason, `mistakesStayHidden` and its reason, `reachesAcrossProjects`, `thinEvidence`, `complexity` (the model's, the one scoring reads), `complexityByTable` (information), `reason`, `judgedOnPartialInput`, `leftOutPullRequests`. `judgment.facts` has one fact per problem in the same order.
- The model decides the complexity (decision 323). F08 can report three things apart: agreement of `complexity` with the user's label, agreement of `complexityByTable` with it, and whether each depth is right.
- With decision 320 nothing is Routine unless it is standard practice of the stack; 0 problems are "no ready answer". If the blind labels show the AI too high, this default is the first thing to look at.
- 6 items carry `thinEvidence`; F08 can pick or report them apart.
- G2 is scored again by a new row of `scoring.gate_scored` with `"G2": true` for a quarter, then a `dimension-scoring` run. A gate with limits and no entry in that setting fails the run.
- `/judge feature-work` names v2 and runs on Sonnet. To judge again after a prompt change, add `feature-work-complexity/v3` (new files, hashes pinned); never edit v2.
- A backend for `ei_latest` can run from the built jar without touching the working tree: `POSTGRES_DB=ei_latest JUDGMENT_MODE=exchange LLM_PROVIDER_2_API_KEY= java -jar backend/target/ei.jar` from the repository root.
