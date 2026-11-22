# F11 Scoring: design

State: approved on 2026-11-22, through the question brief ("OK, all recommended")

## Goal

Two dimension scores per developer and quarter, Coding and code quality and Engineering standards and system health, each with a trail that explains it. Levels are unknown for this team, so a score is given for a level the viewer selects (L1, L2 or L3). Gates G1 and G2 can hold the Engineering standards score down. Every number on the trail opens to its evidence items and pull requests, and the score can be worked out again by hand from the trail.

## In scope / Out of scope

**In.**
- Migration V12: tables `dimension`, `dimension_evidence_type`, `dimension_gate`, `dimension_score`; seed of the two dimensions and their mapping; the scoring rows in `setting`.
- A new package `scoring`: items and tiers of a person's quarter, presence and working tier (spec 9.3), the score against a selected level (spec 9.1 step 5), gates G1 and G2 (spec 7.3, 9.1 step 6), G4 shown as "gate unverified", the trail.
- A job `dimension-scoring` that scores every person for a quarter at the three levels and stores the rows.
- An endpoint in `api` that reads the stored scores of a person for a quarter and a selected level.
- Enabling `SpecNumbersTest.theScoringPackageHoldsNoNumberConstant` (disabled "F11 creates the scoring package").

**Out** (master plan section 4, and the F11 block).
- Gates G3 and G5. G4 is never calculated: it is shown as "gate unverified".
- The clean-delivery rule (spec 9.2): Feature work keeps its plain tier.
- Dimensions other than the two. Dimension weights per level and the weighted overall score (spec 8.1, 10.1).
- Confidence (spec 9.4) and the opportunity profile (spec 6.6): "Not assessed" does not occur (master plan, "No items").
- Tiers for Bug fix and Documentation: Bug fix is listed on the trail, counted, not tiered.
- Any screen (F12). The web is not changed.
- F08 and F09 are deferred (decision 178): F11 runs on the two-week window, 1 to 14 Jul 2026.

## Data and tables

### Rules copied from the spec and the plan

Spec 8.3 (the two rows F11 builds):

| Dimension | Evidence types | Quality gates | Possible when | Details |
| --- | --- | --- | --- | --- |
| Coding and code quality | Feature work, Review comment written | None | Always | Feature work is read after the clean-delivery rule in 9.2. Review comments are read only for L2 and L3 developers, because the L1 statement does not ask for code reviews |
| Engineering standards and system health | Test change, Bug fix, Refactor and performance item, Security item, Infra and release item | G1, G2, G4 | Always | An L3 developer needs two T3 items here, not one |

`poc-plan.md` 3.1 item 6: Coding and code quality reads Feature work and Review comment written, no gates. Engineering standards reads Test change, Refactor and performance item, Security item, Infra and release item; gates G1 and G2; G4 shown as "gate unverified"; Bug fix items listed on the trail as "counted, not tiered". Weights 1, 2, 4 from spec 9.3; a Very high item counts as T3 with weight 4.

Spec 9.3, presence:

| Group | The person has presence at a tier when |
| --- | --- |
| Frequent (Feature work, Review comment written, Test change) | At least one third of this quarter's items of that type sit at that tier or higher |
| Rare (all others) | At least one item sits at that tier or higher |

"Feature work is counted by weight and not by number. A T1 item counts 1, a T2 item counts 2 and a T3 item counts 4." "The working tier of a dimension is the highest tier at which any of its evidence types has presence." Example: 2 Low and 5 Medium give weights 2 × 1 + 5 × 2 = 12; 10 of them at T2 or higher (83%), more than one third; nothing at T3; working tier T2. "For Engineering standards and system health at L3, two T3 items are needed, not one."

Spec 9.1 step 5:

| Working tier compared with the level | Dimension score |
| --- | --- |
| Lower, or no working tier at all although it was possible | 0 |
| Same number, for example T2 for an L2 | 1 |
| Higher, for example T3 for an L2 | 2 |

Spec 9.1 step 6:

| Gate result | Effect on the dimension score | Note stored on the trail |
| --- | --- | --- |
| All gates pass | The score stays | None |
| Any gate is a close miss | A 2 becomes 1. A 1 or a 0 stays | "held: G1" |
| Any gate is a far miss | The score becomes 0 | "gate: G4 far" |
| A gate has no data | No effect | "gate unverified" |

Spec 7.3, G1 and G2: pass when the person's share is "not lower than the same project's share in the same quarter by more than 10 points"; close miss "lower by 10 to 25 points"; far miss "lower by more than 25 points". "A gate never raises a dimension score." "When a project has fewer than three developers, there is nobody to compare with. In that case G1 and G2 have no effect and the trail notes 'gate unverified'."

`poc-plan.md` 3.3:

| Gate | Person's number | Compared with |
| --- | --- | --- |
| G1 Tests with features | Share of the person's PRs with work type feature or enhancement that change tests | The same share for all developers in the repository in the quarter |
| G2 Regression tests | Share of the person's Bug fix PRs that change tests | The same share for all developers in the repository in the quarter |

Stand-ins, shown on the trail: "changes tests" stands in for "adds a regression test" in G2; the repository stands in for the spec's "project".

Spec 14, starting values: presence threshold one third; Feature work weights T1 = 1, T2 = 2, T3 = 4; T3 items needed for Engineering standards at L3: 2; G1 and G2 pass within 10 points, far miss beyond 25 points. Spec 8.6: "The mapping has a version per quarter."

### What F11 reads (from F05 to F10)

- `evidence_item`: current item = not withdrawn and superseded by no other row (decision 181, `AnalyticsSql.CURRENT_ITEM`). Types `feature_work`, `review_comment_written`, `test_change`, `refactor_performance`, `security`, `infra_release`, `bug_fix`, `documentation`. An item counts in the quarter of `occurred_on` (decision 180).
- `judgment`: current = superseded by no other row. The tier of an item is its current judgment of its kind: `feature_work_complexity` for Feature work (tier on the row; `value.complexity` `low` to `very_high`, Very high gives T3, decision 171), `review_comment` for Review comment written (tier null when not substantive, decision 170), `item_tier` for Test change, Refactor and performance, Security, Infra and release (decision 154).
- Pull requests: `AnalyticsSql.HUMAN_PULL_REQUESTS` (human-authored PRs merged in the quarter with their person), `CURRENT_DIGEST` (work type of the PR, `value.workType`). "Changes tests" = the PR has a current Test change item (decisions 148, 182). Bug fix PRs = the PRs of current Bug fix items in the quarter, both kinds (decision 183); any test kind counts (decision 191). These are the same building blocks as F10's Testing contribution; `AnalyticsSql` is package-private today and is made public for reuse (D215).
- `setting` through `SettingService` (`getJson`, `getInt`, ... for a quarter).
- `person`: `PersonDirectory`.

Real data on the Compose database (checked 22 Nov 2026, 12:04 UTC): current items 37 Feature work (7 judged: 5 Low, 2 Medium), 379 Review comment written (41 judged: 8 T1, 23 T2, 3 T3, 7 not substantive), 38 Test change (all judged: 10 T1, 28 T2), 4 Security (all judged), 66 Bug fix, 8 Documentation; no Refactor or Infra item. Most items are "not judged yet": the free-tier model runs have not finished.

### Migration V12

`dimension`: `id`, `key` (unique: `coding_and_code_quality`, `engineering_standards_and_system_health`), `name` (the spec names).

`dimension_evidence_type`: `id`, `dimension_id`, `evidence_type`, `valid_from_quarter`, `tiered` (false for Bug fix: counted, not tiered), `from_level` (`L1`, or `L2` for Review comment written in Coding, spec 8.3 and Q4). The mapping of a dimension in force for a quarter is its rows with the latest `valid_from_quarter` not after the quarter (the rule of `setting`, decision 24). Seed valid from `2026-Q2`, like the other settings.

`dimension_gate`: `id`, `dimension_id`, `gate` (`G1`, `G2`, `G4`), `valid_from_quarter`, `applied` (false for G4: shown as "gate unverified"). Versioned like the mapping.

`dimension_score`: `id`, `job_run_id`, `person_id`, `dimension_id`, `quarter`, `selected_level`, `working_tier` (null when none), `score` (0, 1, 2), `gates` (JSONB), `trail` (JSONB), `calculated_at`. Unique (`job_run_id`, `person_id`, `dimension_id`, `selected_level`). Rows are never updated: a new run inserts new rows (D213).

Settings (valid from `2026-Q2`):

| Key | Value |
| --- | --- |
| `scoring.presence_threshold` | `{"numerator": 1, "denominator": 3}` (exact one third) |
| `scoring.feature_work_weights` | `{"T1": 1, "T2": 2, "T3": 4}` |
| `scoring.frequent_evidence_types` | `["feature_work", "review_comment_written", "test_change"]` |
| `scoring.t3_items_needed_at_l3` | `{"engineering_standards_and_system_health": 2}` |
| `scoring.gate_limits` | `{"G1": {"passWithinPoints": 10, "farMissBeyondPoints": 25}, "G2": {...same}}` |
| `scoring.gate_minimum_developers` | `3` |
| `scoring.gate_minimum_pull_requests_per_developer` | `1` (Q7) |

### The trail (JSONB on `dimension_score`)

Enough to work the score out again by hand: the mapping version and every setting value used; per evidence type the items (id, key, tier, complexity for Feature work, weight, PR numbers, source link), the items not judged yet, not substantive or not read at this level, and the presence sums per tier (weight or count at the tier or higher, total, threshold, presence yes or no); the T3 count for the L3 rule; the working tier; the score before gates; per gate the person's tested and total PRs with numbers, the repository's, the shares, the difference in points, the number of developers in the group, the result and its note; Bug fix items under "counted, not tiered"; the notes; the final score.

## Main flows

1. **Run.** `POST /api/jobs/dimension-scoring/runs` with `{"quarter": "2026-Q3"}`. The job reads, in one repeatable-read snapshot, the settings and mapping for the quarter, the gate numbers of every developer in the repository, and the items and tiers of each person to score (Q10). For each person, each dimension and each level L1, L2, L3 it works out the score and inserts a row. All rows of a run are written in one transaction: a failed run writes none. Counts: persons, scores written.
2. **Working tier.** For each tiered evidence type of the dimension read at the level: frequent types have presence at a tier when (weight or count at the tier or higher) × denominator ≥ total × numerator; rare types when at least one item sits at the tier or higher. The working tier is the highest tier with presence in any type. At level L3 in a dimension with `t3_items_needed_at_l3`, T3 also needs that many T3 items (Q3).
3. **Score.** Working tier below the level or none: 0; same: 1; higher: 2.
4. **Gates.** For each applied gate: the person's share and the repository's share (Q6), difference in points = (repository − person) × 100. No PR of the kind for the person, or fewer than the minimum developers: "gate unverified", no effect. Else pass, close miss or far miss (Q5). Any far miss: 0; else any close miss: a 2 becomes 1. G4: "gate unverified".
5. **Read.** `GET /api/developers/{personId}/dimension-scores?quarter=2026-Q3&level=L2` returns the two dimension scores of the newest succeeded `dimension-scoring` run for that quarter, with gates, notes and trail (Q12).

## Edge cases

- A person with no item in a dimension: no working tier, score 0 (master plan, "No items").
- Items without a current judgment: Q1. Non-substantive review comments: Q2.
- Review comments at level L1: Q4.
- One T3 item in Engineering standards at L3: Q3.
- A person with no PR of a gate's kind (no feature PR, no Bug fix PR): "gate unverified" (D209).
- Repository with fewer than three developers in a gate's group: "gate unverified".
- Difference exactly 10 or 25 points: Q5. Shares are compared unrounded.
- A PR without a digest is left out of G1 and counted on the trail (D208).
- No `dimension-scoring` run for the quarter yet: the endpoint answers 200 with `scored: false` and no dimensions (D217). Unknown person 404; missing or wrong quarter or level 400.
- A setting or mapping missing for the quarter: the run fails naming the key (decision 24).
- A second start while the job runs: 409 (launcher).

## How it will be tested

Master plan "Done when":
- **Spec 9.3 example.** Unit test: 2 Low and 5 Medium Feature work give weights 12, 10 at T2 or higher (83%), working tier T2.
- **Gate limits.** Unit tests at differences 9.99, 10, 10.01, 25, 25.01 points, and a person above the repository; the fewer-than-three-developers case; a person with no PR of the kind.
- **Setting changes the result.** Database test: a new `scoring.presence_threshold` row for the quarter changes the working tier without a code change; the same for a gate limit.
- **Recalculated by hand from the trail.** The session takes one stored score from a run and works it out again from the trail alone, step by step, in `notes.md`.

Also:
- Unit tests for presence of frequent and rare types, Feature work weights with Very high, the L3 two-T3 rule, the score table, gate effects (close miss holds a 2, far miss gives 0, unverified has no effect), the review comment rule at L1.
- Database tests for the item and gate queries (current rows only, quarter, bots left out, Bug fix PRs of both kinds), the mapping and gate versions per quarter, the job (rows per person, dimension and level; a second run adds rows; a failure writes none), the endpoint (shape, 404, 400, not scored yet).
- Architecture: `SpecNumbersTest` for `scoring`.
- Real data: the job runs on a throwaway copy of the Compose database (the user's instruction: do not wait for the model runs; delete the copy afterwards). The scores of one developer are checked against separate SQL.

## Decisions

All open points are closed. Session decisions (numbers in `docs/decisions.md`):
201 package `scoring`; 202 V12 tables, versioned mapping and gates; 203 scoring settings; 204 score scale and tier order are definitions; 205 SQL in one repeatable-read snapshot; 206 tier = current judgment of the item's kind, Very high T3 weight 4; 207 one third inclusive and exact; 208 G1 PRs and "changes tests"; 209 no PR of a gate's kind gives "gate unverified"; 210 G4 always "gate unverified"; 211 far miss 0, close miss 2 to 1; 212 Bug fix counted, not tiered, Documentation in neither; 213 rows never updated, one transaction per run; 214 stand-ins and clean-delivery note on the trail; 215 `AnalyticsSql` public; 216 job `dimension-scoring` with `quarter`, three levels; 217 `scored: false`, 404, 400.

User's answers to the question brief ("OK, all recommended"):
- 218 (Q1 A) unjudged items are left out of the shares, listed "not judged yet".
- 219 (Q2 A) non-substantive review comments are left out of the share, listed "not substantive".
- 220 (Q3 A) L3 in Engineering standards: presence at T3 and two T3 items across the dimension's types.
- 221 (Q4 A) review comments are not read at L1, listed "not read at L1".
- 222 (Q5 A) up to 10 points pass; above 10 to 25 close miss; above 25 far miss.
- 223 (Q6 A) repository share pooled over all developers, the person included.
- 224 (Q7 A) a developer counts in a gate's group with at least one PR of its kind (setting).
- 225 (Q8 A) anchor rule kept: any type can set the working tier.
- 226 (Q9 A) "hardest work available" exception not applied.
- 227 (Q10 A) persons with a current item or a merged human PR in the quarter are scored.
- 228 (Q11 A) the job stores the three levels; the API reads them.
- 229 (Q12 A) `GET /api/developers/{personId}/dimension-scores?quarter=&level=`.
