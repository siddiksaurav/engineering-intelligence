# F16 Spec v1.6 alignment: design

State: approved on 2026-11-25, through the question brief

## Goal

The PoC judges by the rules of spec v1.6 where they differ from v1.5 in a way that changes a judgment, a score or a screen (`docs/spec/v1.5-to-v1.6-impact.md`, changes 1 to 3):

1. The complexity of Feature work is judged by the problems the work solved and the depth of each (spec 6.2).
2. Quality check G2 is shown and not scored (spec 7.3).
3. Screens and trail notes say "check", not "gate" (decision 304).

After the build, the 42 Feature work items of `ei_latest` are judged again and the scores are calculated again. The spread of Low, Medium and High decides whether F09 runs before F08 (decision 307).

## In scope / Out of scope

In scope:
- Prompt and schema `feature-work-complexity/v2`, the answer class, the Stage 2 input with the reason and facts of each PR digest.
- The item page shows the problems and their depths.
- G2 calculated, shown, not scored, with a switch in `setting`.
- "check" in the trail notes and in every text of the web pages.
- The `/judge` skill names the v2 prompt.
- On `ei_latest`: judge the 42 items again, run `dimension-scoring` for 2026-Q3, count Low, Medium and High.

Out of scope (master plan section 4):
- The search of the repository for an earlier solved example (decision 303).
- A growing set of confirmed examples (decision 306). The prompt holds the five of spec 6.2.
- A lead correcting one depth (no correction screen, decision 3).
- Renaming "gate" and "hygiene" in code, tables, settings and API fields (decision 304).
- A new digest run. The digests stay `pull-request-digest/v1`.
- Blind labels, agreement numbers, the feasibility report (F08).
- The best-practice flag that G2 feeds in the spec (a management report, spec 10.3; never built).

## Data and tables

### Spec rules this feature uses (v1.6, copied)

Section 6.2, the depth of a problem:

| Depth | Meaning |
| --- | --- |
| Routine | The project already has a solved example of this problem, or it is standard practice that every developer on that stack knows |
| Answer exists | The tool or a well-known pattern covers the problem, but the project had no earlier example. The person had to find the answer and apply it |
| No ready answer | The tool does not support it and no known pattern fits. The person had to do R&D and work out a solution |

Section 6.2, the complexity from the depths:

| Complexity | When it applies | Tier |
| --- | --- | --- |
| Low | Every problem is routine | T1 |
| Medium | The deepest problem is "answer exists" | T2 |
| High | At least one problem has no ready answer. Or several "answer exists" problems affect each other. Or the work is a design whose mistakes stay hidden across the system, such as a permission model | T3 |
| Very high | The problems reach across projects or the organization | T4, for later use (the PoC counts it as T3, decision 171) |

"Two problems affect each other when two different people could not have built and tested them separately."

Section 6.2, the five reference examples (all five go into the prompt word for word): Organization CRUD (all routine, Low); first datatable on the project (two answer exists, Medium); role-based permission design (three answer exists, affect each other, mistakes stay hidden, High); file ingestion through NiFi (four answer exists, affect each other, mistakes stay hidden, High); clickable map with drill-down in Superset (one no ready answer, one answer exists, one routine, High).

Section 6.2, the rules: size is not complexity; the first solution gets the credit; new to the person is not new to the project; merged means accepted; the work must leave a trace; complexity does not depend on how the tickets were cut. The rule for client-owned repositories is left out of the prompt: the PoC has none.

Section 7.3: "G2 is switched off at the start. [...] G2 is shown on the dashboard and still feeds the best-practice flag, but it does not affect any dimension score. It is switched on in settings [...]. Until then the trail notes 'G2 not scored yet'."

Section 9.1 step 6, the notes on the trail: a close miss "held: G1"; a far miss "check: G4 far"; no data "check unverified".

### Facts about the data (`ei_latest`, checked 25 Nov 2026)

- 42 current Feature work items, each with a current `feature_work_complexity` judgment of `feature-work-complexity/v1`: 23 low, 17 medium, 2 high.
- Pull requests per item: 33 items have 1, 4 have 2, 2 have 3, and one each has 4, 5 and 6.
- The 35 Stage 2 inputs kept in `model_exchange` are 2,556 characters on average and 7,707 at most. The budget is 14,000 (`ei.judgment.max-feature-work-characters`).
- The 172 digests: the reason is 135 characters on average (239 at most); the facts are 3 on average (6 at most), 497 characters together on average (960 at most).
- No digest states a line count in its reason, facts or nature of change (0 of 172, searched for a number followed by "lines" or "LOC").
- `dimension_gate` holds G1 applied, G2 applied, G4 not applied, from 2026-Q2. `scoring.gate_limits` holds G1 and G2. The newest migration is V13.
- `ei_latest` lives in the Compose PostgreSQL container next to `ei`. JDK 25 is at `~/.jdks/temurin-25.0.5` on this machine.

### Existing code this feature changes (survey of 25 Nov 2026)

- `judgment/FeatureWorkJudge`: `PROMPT_VERSION = "feature-work-complexity/v1"`; builds `FeatureWorkInput`, skips a known input hash, asks through `CheckedAnswers.ask` (asks once more on an invalid answer), reads the tier from the setting `evidence.complexity_tiers`, writes one `NewJudgment` of kind `FEATURE_WORK_COMPLEXITY` with `answer.toValue(...)`, `answer.reason()`, the facts and `answer.confidence()`.
- `judgment/FeatureWorkAnswer`: record of `workType`, `complexity` (`low`, `medium`, `high`, `very_high`), `Rubric` (eight v1 fields), `facts` (`fact`, `quote`, `source`), `reason`, `confidence`. `fromAnswer(JsonNode)` checks it; `toValue(judgedOnPartialInput, leftOutPullRequests)` gives the stored JSON.
- `judgment/FeatureWorkInput`: text with `[WI-n]` and `[PR-n]` parts; per PR the title, merge day and a one-line digest (work type, modules, schema or data migration, new integration, dependency changes, test kind, code read in full) and the nature of the change. `linkOf(sourceKey)` gives the link of a cited key, or the item's link. The hash covers the kind, the prompt version, the text, the item row, the source record ids and the digest judgment ids.
- `judgment/PullRequestDigest`: has `reason` and `facts` (`text`, `filePath`), not passed to Stage 2 today.
- `judgment/PromptLibrary` loads `judgment/prompts/<name>-v<n>.md` and `judgment/schemas/<name>-v<n>.json`; `PromptLibraryTest` pins the SHA-256 of each file.
- Readers of the stored value: `scoring/ScoredItems` and `analytics/WorkComplexity` read `value ->> 'complexity'`; the item API sends the whole `value`; nothing reads `rubric`.
- `scoring/DimensionScorer`: checks each mapped gate (`MappedGate(gate, applied)`), then `applyGates`: a far miss gives 0, a close miss turns a 2 into a 1. `ScoreNotes.forGate` gives "held: G1", "gate: G1 far", "gate unverified: G4". `GateResult` carries the outcome, the PR number lists, the shares, the limits and the note.
- Web: `app/items/[itemId]/JudgmentView.tsx` shows tier, complexity, reason, facts, model and prompt version. `app/developers/[personId]/profile/QualityChecksSection.tsx` shows G1 and G2 with an outcome pill. `profile/Trail.tsx` prints "Score before gates". `lib/profileText.ts` has "This gate is not calculated in the PoC." and "The gate has no result.", and builds the "why this score" and "next step" lines from the gates that missed.
- `.claude/skills/judge/SKILL.md` names `feature-work-complexity/v1` and starts Sonnet subagents.

### What is stored

No new table. One migration, `V14`, adds one setting row (the G2 switch). The answer of v2 goes into `judgment.value`:

| Field | Values | Written by |
| --- | --- | --- |
| `workType` | one of the 13 work types | model |
| `problems[]` | at least one; each has `name`, `depth` (`routine`, `answer_exists`, `no_ready_answer`), `depthReason`, `quote`, `source`, `earlierExample` | model, except `earlierExample`, which the system sets to `not_checked` (decision 303) |
| `problemsAffectEachOther`, `problemsAffectEachOtherReason` | one yes or no for the item, and one sentence that names the problems and says why (decision 324) | model |
| `mistakesStayHidden`, `mistakesStayHiddenReason` | true when the work is a design whose mistakes stay hidden across the system | model |
| `reachesAcrossProjects` | true when the problems reach across projects or the organization | model |
| `thinEvidence` | true when the model could not really see the problems of the item (decision 321) | model |
| `complexity` | `low`, `medium`, `high`, `very_high`; key and values unchanged, so scoring and analytics read on | model, by its own judgment (decision 323) |
| `complexityByTable` | what the table of spec 6.2 gives for the listed depths and flags; information only, nothing reads it for a score | system |
| `reason` | one to four sentences; names the nearest of the five examples | model |
| `judgedOnPartialInput`, `leftOutPullRequests` | as in v1 | system |

The tier stays on the row, read from `evidence.complexity_tiers`. The confidence stays in its column. `judgment.facts` gets one fact per problem: the problem, its depth and its quote, linked to the cited source (through `FeatureWorkInput.linkOf`). So every judgment still has at least one fact with a link.

The new input hash (new prompt version, new text) means every item is judged again; the store supersedes the v1 row. Nothing is edited.

## Main flows

1. **Input.** Under each `[PR-n]` part the text gets two more lines from the stored digest: `Digest reason:` and `Digest facts:` (each fact with its file path when it has one). Nothing else changes: no diff, no file list, no line count.
2. **Prompt v2.** See "The prompt" below.
3. **Answer.** `FeatureWorkAnswer` reads and checks v2: at least one problem, every enum spelled exactly, a non-blank name and depth reason per problem. An invalid answer is asked once more, as today.
4. **Complexity.** The model picks it and the code stores it as given; no answer is refused because its complexity differs from the table (decision 323). The code also applies the table of spec 6.2 to the answer and stores the result as `complexityByTable`: `very_high` when `reachesAcrossProjects`; else `high` when a problem is `no_ready_answer`, or two or more are `answer_exists` and `problemsAffectEachOther`, or `mistakesStayHidden`; else `medium` when the deepest problem is `answer_exists`; else `low`. The tier is read from the model's `complexity`.
5. **Store.** As today, with the value above and one fact per problem.
6. **Item page.** For a `feature_work_complexity` judgment with `problems`: a list "Problems solved", each with its name, a depth chip (Routine, Answer exists, No ready answer), the depth reason, the quote linked to its source, and the small line "earlier example not checked". Under the list: whether the problems affect each other and whether mistakes stay hidden, with the reason of each when true; "thin evidence" when the mark is set; and, only when it differs from the AI's complexity, the line "The table of spec 6.2 would give Medium." A judgment without `problems` (a v1 row on another database) shows as today.
7. **G2 not scored.** The scorer reads the switch per gate. A gate that is calculated but not scored keeps its numbers and its outcome on the result, carries `scored: false`, never lowers the score, and its note is "G2 not scored yet" whatever its outcome. On the profile its pill shows the result as today (for example "Far miss") with a small tag "not scored" next to it (decision 322). The stand-in note "changes tests stands in for adds a regression test" stays, because G2 is still shown. The "why this score" and "next step" lines of the profile leave an unscored check out.
8. **Names.** Trail notes: "check: G1 far", "check unverified: G4"; "held: G1" stays. Web texts: "Score before checks", "This check is not calculated in the PoC.", "The check has no result."
9. **Run on `ei_latest`.** Dump the database first. Start the backend with `POSTGRES_DB=ei_latest JUDGMENT_MODE=exchange` (V14 is applied at start). Follow the steps of `/judge feature-work` until nothing waits; the subagents run on Sonnet (decision 327). Run `dimension-scoring` for 2026-Q3. Count the items per complexity.

### The prompt (`feature-work-complexity/v2`)

The file is written in the plain style of v1 and holds, in this order:

1. **Task.** Judge one piece of Feature work of OpenProject: list the problems it had to solve, give each a depth, then give the complexity and the work type. Answer with one JSON object of the schema.
2. **What you are given.** As v1, plus the digest reason and digest facts under each `[PR-n]`. The parts are the only evidence.
3. **Move 1, the problems.** One to eight distinct problems the work had to solve, each named in a few words. A problem is something that had to be worked out, not a file, a ticket or a pull request. All tasks and pull requests of the item are read together: how the tickets were cut changes nothing.
4. **Move 2, the depth.** The depth table of spec 6.2 with its examples. The model cannot see the repository, so (decision 320): a problem is `routine` only when it is standard practice that every developer on the stack knows (Ruby on Rails, Angular, PostgreSQL), or when the input itself shows an earlier solved example in the project ("like the existing ...", "same as for ..."). Otherwise a problem that a tool or a well-known pattern covers is `answer_exists`. `no_ready_answer` needs evidence in the input that no tool support and no known pattern fitted. New to the person is not new to the project. Each problem gets a `depthReason` of one sentence, a `quote` copied exactly from the input (empty when no text shows it) and the `source` key of the part it comes from.
5. **Move 3, the complexity.** The complexity table of spec 6.2 and the sentence on "affect each other", given as guidance. The model weighs the problems, their depths and how they hang together, places the item among the five examples and picks the complexity by its own judgment. It may depart from the table when the evidence asks for it and then says why in the reason (decision 323). `problemsAffectEachOther`, `mistakesStayHidden` and `reachesAcrossProjects` are answered from the evidence, each with its reason sentence where the schema has one.
6. **The five examples** of spec 6.2, as a table, word for word.
7. **The rules** of spec 6.2: size is not complexity (no line counts are given and none may be estimated); merged means accepted; the work must leave a trace; complexity does not depend on how the tickets were cut; new to the person is not new to the project. "The first solution gets the credit" is stated with the limit above: the repository cannot be searched.
8. **Thin evidence** (decision 321). When the input is too thin to really see the problems, the model still judges as for any item, with no push toward a lower depth, sets `thinEvidence` to true and says in the reason what was missing.
9. **Work type**, the 13 types, as v1. A work item of type Bug is never `feature`.
10. **Reason and confidence.** The reason is one to four sentences: why this complexity, and which of the five examples the item is nearest to. Confidence as v1.
11. **Style**, as v1.

The schema is strict (`additionalProperties: false`, every field required): `workType`, `problems[]` (`name`, `depth`, `depthReason`, `quote`, `source`), `problemsAffectEachOther`, `problemsAffectEachOtherReason`, `mistakesStayHidden`, `mistakesStayHiddenReason`, `reachesAcrossProjects`, `thinEvidence`, `complexity`, `reason`, `confidence`. The two reason texts may be empty when their flag is false.

## Edge cases

- The model lists no problem, or a depth outside the three values: invalid answer, asked once more; a second failure fails the item and the run names it (as today).
- A problem cites a source key that is not in the input, or none: its fact gets the item's link (as today).
- A problem with an empty quote: allowed; the fact is then the problem and its depth.
- An item whose input is cut for size: `judgedOnPartialInput` as today.
- A database that still holds v1 judgments (the Compose `ei`): pages keep working; the item page shows no problem list for them.
- The switch setting has no entry for a calculated gate: the scoring run fails and names the gate and the quarter. No default lives in code.
- G2 unverified (fewer than three developers) while not scored: the note is still "G2 not scored yet"; the pill says "Unverified" with the tag "not scored".
- Scores stored by an earlier run keep "gate" in their notes. The pages read the newest run only; the run of flow 9 replaces them. On `ei` they stay until scoring runs there again.
- "A test gate in CI for a module" on the next-step line is the wording of spec v1.6 6.4 for a T3 Test change. It is another meaning of the word and stays.

## How it will be tested

Tests (written first):
- `FeatureWorkAnswerTest`: reads a v2 answer; refuses no problem, an unknown depth, a blank name; `toValue` holds the problems with `earlierExample: not_checked`.
- The table of spec 6.2 (`complexityByTable`): all routine gives low; deepest answer exists gives medium; one no ready answer gives high; two answer exists that affect each other give high; mistakes stay hidden gives high; reaches across projects gives very high; the five examples of spec 6.2 as five cases. An answer whose `complexity` differs from the table is accepted and stored with both values.
- `FeatureWorkInputTest`: the text holds the digest reason and facts of each PR; no line count.
- `FeatureWorkJudgeTest`: the judgment has prompt version `feature-work-complexity/v2`, one fact per problem with a link, the tier from the table; a v1 judgment of the same item is superseded.
- `PromptLibraryTest`: v2 prompt and schema load, hashes pinned, the schema is strict, the prompt names the three depths, the four complexities, the 13 work types and the five examples.
- `DimensionScorerTest`: with G2 not scored, a G2 far miss leaves the score as it is and the notes hold "G2 not scored yet" (master plan "Done when"); with the switch on, the far miss gives 0. `ScoreNotesTest`: no note holds "gate".
- `DimensionScoringJobTest`: the switch is read from `setting`; changing the row changes the score without a code change.
- Web: `JudgmentView` shows problems, depths and "earlier example not checked"; `QualityChecksSection` shows G2 with its result pill and the tag "not scored"; `Trail` says "Score before checks"; `profileText` leaves an unscored check out of the next step.

Master plan "Done when", line by line:
1. Every Feature work item of `ei_latest` has a current `feature-work-complexity/v2` judgment with at least one problem and a depth on each: SQL count after flow 9.
2. The item page of one item shows them: the session opens `/items/<id>` itself and reads the HTML. The user does not open item pages of Feature work items before F08 (blind check).
3. A G2 far miss leaves the score and the trail says "G2 not scored yet": the tests above, and one developer of `ei_latest` with a G2 far miss checked by SQL.
4. No screen and no trail note says "gate": a search of `web/` texts (the spec 6.4 wording above is the one exception) and a SQL search of the notes of the newest scoring run.
5. The count of items at Low, Medium and High is in `notes.md`, with what it means for F09 (decision 307).

## Decisions

From the question brief (user, 25 Nov 2026):
- 320 (Q1). Without a repository search the AI assumes the project had no earlier example: beyond standard practice of the stack a problem is "answer exists", unless the input shows an earlier example.
- 321 (Q2). An item with thin evidence is judged normally, with no push toward the lower depth, and marked `thinEvidence`.
- 322 (Q3). The profile shows G2 with its result pill and a small tag "not scored".
- 323 (Q4). The AI decides the complexity itself and reasons on it; the table and the five examples of spec 6.2 are guidance. The code derives nothing and refuses nothing; it stores what the table would give as `complexityByTable`, for information. Replaces decision 313 as a rule on the complexity.
- 326 (Q7), replaced by 327 on the same day: Sonnet answers the Feature work requests in exchange mode, as for v1. Opus would use too much of the user's Claude usage.

By the session (the user found Q5 too technical; Q6 was not asked):
- 324 (Q5). One yes or no per item for "the problems affect each other", with one sentence.
- 325 (Q6). The prompt and schema files of `feature-work-complexity/v1` stay; the Java code only v1 used is removed.
- 310. One fact per problem in `judgment.facts`; no `rubric`, no separate `facts` list.
- 311. `earlierExample: "not_checked"` on each problem, set by the system.
- 312. The prompt holds the tables, the five examples and the rules of spec 6.2; one to eight problems; no upper limit in code.
- 313. Kept only inside the table comparison: hidden-mistakes design work is High by the table; "several" means two or more.
- 314. The Stage 2 input gets the digest reason and facts; the budget stays.
- 315. The G2 switch is the setting `scoring.gate_scored`, migration V14.
- 316. A gate result gets `scored`; an unscored gate never changes the score; note "G2 not scored yet".
- 317. Trail notes "check: G1 far", "check unverified: G4"; earlier runs are not rewritten.
- 318. "A test gate in CI for a module" stays.
- 319. `ei_latest` is dumped before the run; the session runs the judging.
