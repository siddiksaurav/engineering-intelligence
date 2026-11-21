# F07 Stage 2 Feature work judgment and review comment judgment: design

State: approved on 2026-11-22, by the session on the user's instruction ("You need to decide all the decisions by yourself"; no question brief was answered)

## Goal

The judgment the PoC exists to show. Every current Feature work item gets a complexity from the model, with the rubric facts of spec 6.2, each fact with a quote and a link; the tier is read from the complexity table. Every current Review comment written item gets a comment type from the model; whether it is substantive and its tier are read from a table. All of it is stored in `judgment`. Nothing new is shown on a screen (decision 175).

## In scope / Out of scope

**In.**
- Flyway migration V11: the judgment kinds `feature_work_complexity` and `review_comment`; the settings `evidence.complexity_tiers` and `evidence.review_comment_types`.
- Package `judgment`: two versioned prompts with strict answer schemas, the input builders with their character budgets, the two judges (one item, one chunk of comments), the jobs `feature-work-judgment` and `review-comment-judgment`, a guard so two model jobs never run at once.
- Package `evidence`: two readers that hand the current Feature work and Review comment written items to `judgment` through interfaces `judgment` defines (decision 166).
- Package `source`: readers of the texts the prompts need (work item description and priority, review comment body, diff hunk and the comment it answers), built by their first user (decision 62).
- Tests with a fake `JudgmentModel` only (decision 142).

**Out.**
- Real model runs: no LLM key on this machine (user, 22 Nov 2026: "I don't have the apis or api key. Skip that part"). The real-data checks wait for the key (section "How it will be tested").
- Showing complexity, comment types or their tiers on the web or the API: F08 opens the item page only after the blind labels are in (master plan F08, "Order that must be kept").
- The blind check, the item page, prompt tuning: F08. The July run: F09. Analytics: F10. Scoring and the weight of Very high: F11.
- Tiers of Bug fix and Documentation items (master plan section 4). Confidence of spec 9.4 (master plan section 4): the model's own confidence value of this feature is not that.
- A screen to correct a judgment (master plan section 4).
- Several keys of one provider (decision 144).

## Data and tables

### Rules copied from the sources

`poc-plan.md` 3.2, "The AI's two stages":
> **Stage 2, Feature work judgment.** One call per Feature work item with a strong model. Input: the work item texts (feature and the person's child items), priority, linked items, and the PR digests. Output: work type, complexity (Low, Medium, High, Very high), the rubric facts from spec 6.2 with a quote or link for each, a reason, and a confidence value. Lines of code are not given to the model.
> **Tier** is not asked from the model for Feature work. It is read from the complexity by the table in 6.2.
> **Review comments.** One call per PR for all human reviewer comments, each with its diff hunk. Output per comment: type, substantive or style, tier (6.4 row 2), reason. Bot comments and the author's own replies are filtered out before the call.
> Every judgment stores model name, prompt version and a hash of the input, so it can be reproduced.

`poc-plan.md` 5, proposed spec changes used here:
> 3. **Tier follows complexity (6.3, 6.4).** For Feature work the AI judges complexity only, and the tier is read from the table. Two separate AI answers could contradict each other.
> 5. **Module count in the complexity table (6.2).** "Three or more modules is High" is a size rule and conflicts with "size is not complexity" in the same section. Keep module count as one recorded fact among several, not as a deciding rule. A rename across five modules is Low.
> 7. **Work type (6.2).** A Feature work item needs one work type of its own, separate from the work types of its PRs. The tracker's own type (Bug, Feature, Code maintenance) is given to the AI as input.

Spec 6.2, complexity table:

| Complexity | When it applies | Tier |
| --- | --- | --- |
| Low | One module, no data change, a known pattern | T1 |
| Medium | One or two modules, or a schema change, or one integration | T2 |
| High | Three or more modules, or a new integration, or unclear requirements, or a production data migration | T3 |
| Very high | Work across systems, or new architecture, or client-critical work with no earlier example | T4, for later use |

Master plan section 2: "Very high counts as T3 with weight 4." So the stored tier of Very high is T3; the complexity `very_high` stays in the judgment's value for F11's weight.

Spec 6.2, the two limits: "Size is not complexity. The AI must not treat a large change as hard work. A 2,000-line generated file is Low. A 30-line fix to a hard concurrency bug can be High. Lines of code are never used as a measure." "Client-owned repositories [...] judged without code": not the case here (public code); the digests are built from the code.

Spec 6.2, complexity: "From the tickets it takes the titles, descriptions, linked items and priority. From the PRs it takes the modules touched, schema changes or data migrations, new integrations and the nature of the code change."

Spec 6.2, work type: "The AI gives each PR and work item exactly one of 13 work types: feature, enhancement, fix, maintenance, research, technical task, operational support, refactor, test, docs, infra, performance, security."

Spec 6.3: "L1 is about the person's own task. L2 is about their own feature. L3 is about a module or a team. L4 is about work across projects." The system "uses the complexity of feature work, the type of a review comment".

Spec 6.4, the rows used:

| Evidence type | T1 | T2 | T3 | T4, later |
| --- | --- | --- | --- | --- |
| Feature work | Low complexity | Medium complexity | High complexity | Very high complexity |
| Review comment written | A question or a small catch | Logic, edge case or readability | Design, security or performance | Architecture or cross-system |

Spec 6.5: Review comment written carries "PR, author reviewed, comment type, substantive or style, date". Feature work carries "Complexity, [...] work type, [...] modules changed, linked PRs, whether the AI could read the code or only its metadata".

Spec 7.2, Review effectiveness: "Substantive comments per PR reviewed, and the mix of comment types (correctness, security, performance, design, maintainability). An approval with no substantive comment counts as zero." Review findings received: "Style comments are left out."

### Facts about the real data (Compose database, checked 22 Nov 2026)

- 35 current Feature work items (F05; none from F06 yet, no digest is stored). PRs per item: 25 have 1, the largest has 6; tasks per item: at most 5.
- Work item description (`payload.description.raw`, Markdown): median 554 characters, 90th percentile 1,779, 99th 5,990, largest 7,977. Priority is `_links.priority.title` (Low, Medium, High, Immediate). Type is `_links.type.title` (Implementation, Bug, Feature, Code maintenance, Documentation, Open Point, Epic, Task). `_links.relations` is only a link to the relations endpoint; the related items themselves were not imported, so "linked items" are the parent and the items of the group (decision 172).
- 379 current Review comment written items on 64 PRs: median 2 per PR, 90th percentile 15, largest 37.
- `review_comment` payload: `databaseId`, `url`, `path`, `body`, `diffHunk`, `replyTo` (null or an object), `createdAt`, `author`, `pullRequest`, `pullRequestReview`. Diff hunk: median 758 characters, 90th percentile 3,342, 99th 8,314, largest 16,344. Body: median 170, 90th percentile 463, 99th 1,139.
- GitHub's diff hunk of an inline comment ends at the commented line, so its tail is the part that matters.

### Free-tier budget (from F06, decisions 153, 155)

Groq free tier: about 8,000 tokens per minute and 200,000 per day for `openai/gpt-oss-120b`. A call (prompt, input, answer with reasoning) must stay under about 8,000 tokens, so an input stays under about 14,000 characters. Estimate for the two weeks: 35 Feature work calls of about 7,000 tokens and about 80 review calls of about 5,000 tokens: about 650,000 tokens, three to four days of the daily limit. F09 measures it.

### Migration V11

- `judgment.kind` check: adds `feature_work_complexity` and `review_comment`. Both have an evidence item row as subject.
- Setting `evidence.complexity_tiers`, valid from 2026-Q2: `{"low": "T1", "medium": "T2", "high": "T3", "very_high": "T3"}` (spec 6.2, master plan section 2).
- Setting `evidence.review_comment_types`, valid from 2026-Q2 (decision 170):

| Type | Means | Substantive | Tier |
| --- | --- | --- | --- |
| `question` | Asks why or how, wants an explanation | yes | T1 |
| `small_catch` | A small real slip: a typo in shown text, a wrong name, a leftover debug line | yes | T1 |
| `correctness` | Logic or an edge case: the code does the wrong thing in some case | yes | T2 |
| `maintainability` | Readability, duplication, naming that hides meaning, a missing test | yes | T2 |
| `design` | The shape of the solution: where code belongs, an interface, a data model | yes | T3 |
| `security` | A security weakness | yes | T3 |
| `performance` | Speed, memory, queries | yes | T3 |
| `architecture` | Across modules or systems | yes | T3 (6.4's T4, counted as T3 until T4 is used, as Very high) |
| `style` | Formatting or a matter of taste with no effect on behaviour | no | none |
| `acknowledgement` | Thanks, approval, "done", a reply that adds nothing | no | none |

The setting's value is `{"question": {"substantive": true, "tier": "T1"}, ..., "style": {"substantive": false, "tier": null}, ...}`. The prompt names the same ten types; a test checks that the prompt's schema enum and the setting have the same names.

### Judgments written

`feature_work_complexity`, subject the Feature work item row:
- `value`: the model's answer (`workType`, `complexity`, `rubric`, `facts` with quote and source, `reason`, `confidence`) plus `judgedOnPartialInput` and `leftOutPullRequests` set by the system.
- `tier`: from `evidence.complexity_tiers` (quarter of the run day).
- `reason`: the model's reason. `facts`: one per model fact, text `<fact> ("<quote>")`, link made by the system (decision 136): the work item on OpenProject or the PR on GitHub named by the fact's source; an unknown source gets the item's source link.
- `confidence`: the model's value, 0 to 1 (decision 173).
- `model`, `prompt_version` `feature-work-complexity/v1`, `input_hash` (below), `based_on_judgment_id` null.

`review_comment`, subject the Review comment written item row:
- `value`: `{commentType, substantive, reason}`.
- `tier`: from `evidence.review_comment_types`; null for a type that is not substantive.
- `reason`: the model's reason. `facts`: one, `Comment type <type>: "<first 200 characters of the comment>"`, link the comment on GitHub.
- `model`, `prompt_version` `review-comments/v1`, `input_hash` (below).

Input hash (decision 157 form): SHA-256 of the canonical JSON of the kind, the prompt version, the input text sent and the source ids. For Feature work the ids are the item row id, the item's source record ids and the digest judgment ids. For a review comment, the call's input text and, per comment, the item row id and the comment's and PR's record ids, so each comment of one call has its own hash (unique index of decision 127). The item row id is in both: a new row of the same key is a new subject and is judged again (as F06's item tiers).

### Properties (`ei.judgment.*`, decision 45)

- `max-description-characters` 1,500: a work item description or a PR body in the Feature work input.
- `max-feature-work-characters` 14,000: the whole Feature work input.
- `max-hunk-characters` 2,000: the tail of a diff hunk.
- `max-comment-characters` 2,000: a comment body; the comment it answers is cut at 500 (`max-replied-comment-characters`).
- `max-review-characters` 12,000: the input of one review call.

## Main flows

### Order of jobs

`github-import`, `openproject-import`, `identity-build`, `pull-request-digest`, `evidence-build`, then `feature-work-judgment` and `review-comment-judgment`, one after the other. Both take no arguments: `POST /api/jobs/feature-work-judgment/runs -d '{}'`.

### 1. `feature-work-judgment`

1. Fail at the start when no provider has a key (as F06) or another model job runs (decision 174).
2. Read every current Feature work item through `judgment.FeatureWorkItems` (implemented in `evidence`): item row id, key, source URL, the item's source record ids, the feature (work item number or none), the task numbers, the PR external ids.
3. For each item, in its own transaction for the write:
   1. Read the current digest of each PR (`PullRequestDigests.currentOf`). An item with a PR without a digest waits (count `items_waiting_for_digest`), no call.
   2. Build the input (below). Compute the hash. If a `feature_work_complexity` with that hash exists: count `skipped_same_input`.
   3. Call `JudgmentModel.judge` with prompt `feature-work-complexity/v1`. Check the answer; a refused or unreadable answer is asked for once more, then the item fails (decision 135).
   4. Read the tier from `evidence.complexity_tiers`; build facts with links; write the judgment.
4. Every provider at its limit: stop, count `items_left_for_later`, succeed (decision 152). A failed item is counted, the run goes on and fails at the end naming the first 20 keys (decision 90).
5. Counts: `items_read`, `items_waiting_for_digest`, `skipped_same_input`, `judgments_written`, `judged_on_partial_input`, `model_calls`, `input_tokens`, `output_tokens`, `calls_<provider>`, `rate_limit_wait_seconds`, `items_left_for_later`, `items_failed`.

The Feature work input (decision 167), in this order, each part tagged with a source key the model cites:
- The tracker type and the rule: "This is Feature work; the tracker type decides it, the work type is yours."
- `[WI-<number>]` the feature: title, type, priority, its parent's title, description cut at `max-description-characters`. For an item without a work item: "No work item".
- `[WI-<number>]` each task of the group other than the feature: title, type, priority, description cut.
- `[PR-<number>]` each PR: title, merge day, and its digest in short form: work type, modules touched, schema or data migration, new integration, dependency changes, test kind, nature of change, judged on partial code. For a PR without a work item also its body cut at `max-description-characters`. No line counts, no file sizes, no diff.
- Over `max-feature-work-characters`: task descriptions are cut first (shortest kept), then the feature description; if the PRs alone do not fit, the last PRs (newest merge) are left out by name and `judgedOnPartialInput` is set (decision 168).

The answer (strict JSON schema, every field required):
```
workType      one of the 13 work types
complexity    low | medium | high | very_high
rubric        modulesTouched [text], dataChange none|schema_change|production_data_migration,
              integration none|existing_integration|new_integration, requirementsUnclear bool,
              knownPattern bool, acrossSystems bool, newArchitecture bool,
              clientCriticalWithoutEarlierExample bool
facts         [{ fact, quote, source }]  at least one; source is a key of the input such as WI-81224 or PR-24204
reason        text
confidence    number 0 to 1 (checked on arrival)
```

The prompt gives the complexity table of spec 6.2 with the softened module rule (proposed change 5: module count is one fact, never the deciding one; "a rename across five modules is Low"), the two limits of 6.2, the 13 work types, the L1 to L4 pattern of 6.3, and the instruction to quote only what the input shows.

### 2. `review-comment-judgment`

1. Same start checks as flow 1.
2. Read every current Review comment written item through `judgment.ReviewCommentItems` (implemented in `evidence`): item row id, key, comment record id, PR record id, PR external id. Group by PR.
3. Per PR, comments sorted by creation time then id, split into chunks that fit `max-review-characters` (decision 169). Each chunk:
   1. Input: the PR title and body cut at `max-description-characters`; per comment `[C-<databaseId>]`, the file path, the tail of the diff hunk (`max-hunk-characters`, marked "cut" when cut), the comment text (`max-comment-characters`), and for a reply the text of the comment it answers (`max-replied-comment-characters`, whoever wrote it).
   2. Per comment the hash. If every comment of the chunk has a `review_comment` judgment with its hash: skip (count `skipped_same_input` per comment).
   3. Call with prompt `review-comments/v1`. The answer `{comments: [{commentId, commentType, reason}]}` must name every comment of the chunk exactly once (checked; else asked once more, then the chunk fails, named by its PR and first comment).
   4. Read substantive and tier from `evidence.review_comment_types`; write all judgments of the chunk in one transaction (`JudgmentStore.writeAll`).
4. Limits and failures as flow 1. Counts: `items_read`, `pull_requests_read`, `chunks`, `skipped_same_input`, `judgments_written`, `model_calls`, tokens, `calls_<provider>`, `rate_limit_wait_seconds`, `items_left_for_later`, `chunks_failed`.

Bot comments and the PR author's own comments never become items (F05, decisions 102, 120), so they are never judged. The author's comment that a reviewer answers is given as context only.

### Uses (from earlier features)

- `JudgmentModel.judge(ModelRequest): ModelAnswer`; `ModelRequest(promptVersion, instructions, input, schemaName, jsonSchema)`; `ModelAnswer(answer, model, inputTokens, outputTokens)`; `ProviderChain.startRun()`, `providerNames()`, `callsPerProvider()`, `waited()`; `ModelLimitReachedException`.
- `JudgmentStore.write(NewJudgment)`, `hasModelJudgment(kind, inputHash)`, `findCurrent(kind, subject)`; `NewJudgment(kind, subject, value, tier, reason, facts, judgedBy, model, promptVersion, inputHash, basedOnJudgmentId, supersedesId)`; `JudgmentSubject.evidenceItem(id)`; `JudgmentFact(fact, link)`; `JudgedBy.MODEL`.
- `PromptLibrary.instructions(version)`, `schema(version)`; `PromptLibraryTest` pins file hashes (decision 129).
- `PullRequestDigests.currentOf(prExternalId): Optional<CurrentDigest(judgment, digest)>`; `PullRequestDigest` fields of F06 design, "The digest".
- `EvidenceItemRepository.findCurrentByKey`, `CurrentEvidenceItems`, `EvidenceItem` (`fields`, sources).
- `StoredWorkPackages.find(number)`, `facts(number)`; `StoredReviewComments`; `StoredPullRequests.find(repository, number)`, `PullRequestFacts`.
- `SettingService.getJson(key, quarter)`; `CanonicalJson.sha256Hex`; `Job`, `JobArguments`, `JobCounts`, `FailedItems.named`.

### Gives (to later features)

- Judgment kinds `feature_work_complexity` (complexity, work type, rubric, tier) and `review_comment` (type, substantive, tier) on item rows.
- F08: the item page reads both; prompt tuning adds `feature-work-complexity/v2` or `review-comments/v2`.
- F10: work type of Feature work items, comment types and substantive. F11: tiers and `very_high`.

## Edge cases

- A Feature work item whose PRs are not all digested: waits, no call; the next run judges it.
- A Feature work item without a work item (F06): the input has no `WI-` part; the PR body is given.
- A new row of an item key (a changed group or a new digest): judged again; the old row keeps its judgment.
- A withdrawn item: not current, not judged.
- The model cites a source key not in the input: the fact gets the item's source link.
- The model gives no fact, a confidence outside 0 to 1, or a complexity outside the list: refused, asked once more, then the item fails.
- A review answer that leaves out a comment, names one twice or names an unknown id: refused, asked once more, then the chunk fails.
- A single comment larger than the chunk budget cannot happen: its parts are cut to their own limits, which together stay under `max-review-characters` (checked at start-up).
- A reply whose parent comment is not stored: given without the parent.
- A PR with new comments after a judged run: its chunks change, so their comments are judged again and the new judgments supersede the old ones of the same rows.
- Every provider at its limit mid-run: stop, count, succeed.
- A second model job started while one runs: it fails at its start naming the running job.
- The setting misses a comment type the model returned: the chunk fails naming the type (a test keeps them equal).

## How it will be tested

Tests never call a real model (decision 142).

Master plan "Done when":
1. "Every Feature work item closed in the test range has one current complexity judgment with facts that each carry a quote or link." Job test with the fake model: every current item gets one judgment, tier from the table, every fact has a link. On real data, waiting for the user's key: run `pull-request-digest`, `evidence-build`, `feature-work-judgment`, then SQL: current Feature work items without a current `feature_work_complexity` = 0; facts without a link = 0.
2. "Every human reviewer comment has one current judgment." Job test with the fake model. On real data, waiting for the key: `review-comment-judgment`, then SQL: current Review comment written items without a current `review_comment` = 0.
3. "Module count alone does not decide complexity: a test case with a rename across five modules is judged Low." The prompt states the rule and this example (a test checks the prompt names it). The model's behaviour needs the real model: a fixture item (a rename across five modules, built from a real PR the next session picks) is judged with the key; waiting for the user.

Other tests:
- Input builders: the parts and their order, each cut, the budget with the left-out PRs named and `judgedOnPartialInput`, no line counts, the hunk tail, chunking, a reply with its parent.
- Answer checks: every refusal listed in "Edge cases".
- Tiers from the two settings; style and acknowledgement give no tier; very_high gives T3.
- Hashes: change with the prompt version, the input text and the item row; equal for the same input; distinct per comment of one call.
- Jobs: second run makes no call; a waiting item; limit reached stops and succeeds; a failed item fails the run at the end; the guard refuses a second model job.
- Prompt file and schema hashes pinned; the schema's comment types equal the setting's.
- The readers in `evidence` and `source` on fixture records.

## Decisions

The user asked the session to decide everything (22 Nov 2026). Decisions 163 to 176, all "F07 design, session":

- 163. Two jobs, `feature-work-judgment` and `review-comment-judgment`, no arguments, run after `evidence-build`.
- 164. Judgment kinds `feature_work_complexity` and `review_comment`, subject the item row (V11).
- 165. Stage 2 uses the same provider chain and model as Stage 1 (`openai/gpt-oss-120b` is the strongest free production model on Groq); a stronger model later is a `.env` change plus a new prompt version (decision 157).
- 166. `judgment` defines `FeatureWorkItems` and `ReviewCommentItems`; `evidence` implements them, so no package cycle.
- 167. The Feature work input: tagged work item texts, priority, parent, and the digests in short form; no lines, no diff.
- 168. Over the budget: descriptions cut first, then the newest PRs left out by name, `judgedOnPartialInput` set.
- 169. Review comments: one call per PR, split into chunks of at most 12,000 characters; the hunk's tail is sent.
- 170. Ten comment types; substantive and tier read from the setting `evidence.review_comment_types`, not asked from the model.
- 171. Very high gives tier T3; `very_high` stays in the value.
- 172. "Linked items" are the parent and the group's items; OpenProject relations are not imported.
- 173. Confidence is a number 0 to 1 from the model, stored, used by nothing in the PoC.
- 174. Two model jobs never run at once: the second fails at its start.
- 175. No screen or API shows F07's judgments before F08's blind labels.
- 176. A Feature work item waits until every PR has a digest.
