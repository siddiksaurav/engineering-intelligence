# F11 Scoring: design

State: draft

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

## Open points

The question brief is below (and in the chat). Decisions D201 to D217 are numbered 201 to 217 in `docs/decisions.md`.

### Business questions

**Q1. Items the AI has not judged yet** (most items today). How do they count for presence?
- A. Left out of the shares; listed on the trail as "not judged yet" with a count. The score rests on what is judged.
- B. Counted as sitting at no tier: they lower the shares, so scores drop until the model runs finish.
- C. A person with any unjudged item gets no score ("not scored yet").
- Recommended: A. The score is honest about what it rests on, and B punishes the person for the free-tier quota.

**Q2. Review comments that are not substantive** (style, acknowledgement; no tier). Do they count in the one-third share?
- A. Left out of the share; listed on the trail as "not substantive".
- B. Counted as sitting at no tier: many small style remarks lower the reviewer's share.
- Recommended: A. Spec 7.2 leaves style comments out of review counts; a careful reviewer is not punished for nits.

**Q3. L3 needs two T3 items in Engineering standards.** How is "two T3 items" counted?
- A. Across all tiered types of the dimension: presence at T3 in some type and at least two T3 items in total. One T3 item alone gives T2 (score 0 at L3).
- B. Within one type: that type alone must have presence at T3 and two T3 items.
- C. Not applied.
- Recommended: A. The spec says "two T3 items here", that is in the dimension.

**Q4. Review comments at a selected level of L1** (Coding and code quality).
- A. Not read at L1, as spec 8.3 says; listed on the trail as "not read at L1".
- B. Read at every level.
- Recommended: A. Follows the spec; the trail still shows them.

**Q5. Gate boundaries** (difference in percentage points, person lower than the repository).
- A. Up to and including 10 passes; above 10 up to and including 25 is a close miss; above 25 is a far miss.
- B. Below 10 passes; 10 to 25 inclusive is a close miss; above 25 is a far miss.
- Recommended: A. Spec 7.3 says "lower by more than 10 points" fails and "more than 25" is far.

**Q6. The repository's share a gate compares with.**
- A. One share over all developers' PRs of the kind together, the person included.
- B. The same, without the person.
- C. The average of each developer's own share.
- Recommended: A. It is "the project's share" of spec 7.3 and matches Testing contribution.

**Q7. Who counts as one of the "three developers"** of a gate's group?
- A. A developer with at least one PR of the gate's kind in the quarter (minimum kept as a setting, 1).
- B. A developer with at least three such PRs.
- C. Every person with any PR in the quarter.
- Recommended: A. Simple, and the minimum can be raised in `setting` without code.

**Q8. Anchor rule** (spec 16, open): one rare T3 item (say one Security item) lifts the whole dimension's working tier.
- A. Keep as written: any type with presence can set the working tier.
- B. Only the frequent types set it; rare items are shown but cannot lift it.
- Recommended: A. The master plan says keep as written unless decided.

**Q9. "Hardest work available" exception** (spec 9.1 step 5: an L3 on a project with no High work is scored against the hardest work offered).
- A. Not applied; it rests on the opportunity profile, which is out of scope.
- B. Applied, using the repository's closed Feature work complexities.
- Recommended: A. Out of scope by the master plan; scores are for a selected level anyway.

**Q10. Who is scored.**
- A. Every person with at least one current evidence item or merged PR in the quarter.
- B. Every person in the table.
- Recommended: A. A person with no work in the quarter has nothing to show; B gives rows of zeros.

### Technical decisions I need your direction on

**Q11. Stored or calculated on request.**
- A. The job `dimension-scoring` stores the scores at all three levels for every person; the API reads them. Run it again after new judgments.
- B. Calculated on each request; nothing stored (the `dimension_score` table would go unused).
- Recommended: A. Spec 9 freezes a score with its trail; the master plan asks to store it.

**Q12. API shape.**
- A. `GET /api/developers/{id}/dimension-scores?quarter=2026-Q3&level=L2`: both dimensions at that level, with gates, notes and trail.
- B. The same without `level`: all three levels in one answer.
- Recommended: A. F12's level control reloads with the level in the address; answers stay small.

### Decisions I made myself (overrule any by number)

- D201. New package `scoring`; `SpecNumbersTest` for it is enabled.
- D202. Migration V12: `dimension`, `dimension_evidence_type` (with `tiered`, `from_level`), `dimension_gate` (with `applied`), `dimension_score`; mapping versioned per quarter like `setting`.
- D203. The scoring settings of the table above, valid from 2026-Q2; one third stored as a fraction so "at least one third" is exact.
- D204. The score scale (0, 1, 2 from comparing tier and level) and the tier order are the spec's definition, not settings.
- D205. Scoring reads items and tiers with SQL through `JdbcClient` in one repeatable-read snapshot, as analytics does.
- D206. The tier of an item is its current judgment of its kind: complexity for Feature work, review comment for Review comment written, item tier for the others. Very high is T3 with weight 4.
- D207. "At least one third" is inclusive and compared exactly with whole numbers.
- D208. G1 counts the person's human PRs merged in the quarter whose digest says feature or enhancement; a PR without a digest is left out and counted on the trail. "Changes tests" = a current Test change item.
- D209. A person with no PR of a gate's kind: "gate unverified", no effect (spec 9.1 step 6, "a gate has no data").
- D210. G4 is listed on every Engineering standards score as "gate unverified" and never calculated.
- D211. Several gates: any far miss gives 0; otherwise any close miss turns a 2 into 1. Each gate writes its own note.
- D212. Bug fix items are on the Engineering standards trail as "counted, not tiered"; Documentation is in neither dimension.
- D213. `dimension_score` rows are never updated; each run inserts its rows with its `job_run_id`, all in one transaction.
- D214. The trail names the stand-ins ("changes tests" for "adds a regression test"; the repository for the project) and that the clean-delivery rule is not applied.
- D215. `AnalyticsSql`'s shared query pieces are made public so scoring reuses them instead of copying SQL.
- D216. The job takes a required `quarter` argument and scores L1, L2 and L3 in one run.
- D217. Before any run for the quarter the endpoint answers 200 with `scored: false`; 404 for an unknown person; 400 for a missing or wrong quarter or level.
