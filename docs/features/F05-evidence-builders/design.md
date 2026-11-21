# F05 Evidence builders without AI, and the evidence list page: design

State: draft

## Goal

The first visible result. A job turns the stored GitHub and OpenProject records into evidence items of three types, built by rule alone: Feature work, Bug fix and Review comment written. A web page lists each developer's items with links to GitHub and OpenProject.

## In scope / Out of scope

**In.**
- Flyway migration V8: `evidence_item`, `evidence_item_source`, `evidence_item_link`, with the append-only trigger on `evidence_item` (decision 52).
- Package `evidence`: the three builders, the item store (append-only, supersede on change), the job `evidence-build`.
- Readers of stored review comments in `source.github` (decisions 62, 89: built by their first user).
- Package `api`: `GET /api/developers` and `GET /api/developers/{personId}/evidence-items`.
- `web/`: a developer list page and a developer page with their items.
- Enabling the four architecture tests disabled "for F05" (`AppendOnlyTablesTest`, `AppendOnlyCodeTest` x2, `SpecNumbersTest` for `evidence`).

**Out.**
- Any AI call, `judgment`, work type, complexity, tier: F06 and F07.
- Test change, Refactor and performance, Security, Infra and release, Documentation, Feature work "no work item", Bug fix for a PR with no work item: F06.
- The item page, AI answers on the list, human labels: F08.
- Analytics, scoring, profile: F10 to F12. A quarter filter on the page: F12.
- Everything in master plan section 4.

## Data and tables

### Rules copied from the sources

`poc-plan.md` 3.2, building table (the rows F05 builds; the person on every item is the PR author):

| Evidence item | Built from | Tier |
| --- | --- | --- |
| Feature work | For each merged PR, follow its work item up to the nearest ancestor of type Feature (or the item itself if there is none). One item = one PR author + one such feature + all that author's PRs and child items under it. Work items of type Bug are left out of the group | From complexity |
| Bug fix | A work item of type Bug with the PRs that fixed it, even when it sits under a Feature. Also a PR with no work item whose work type is fix | None. Counted only |
| Refactor and performance item, Security item, Infra and release item | A PR with that work type whose work item is not under a Feature (for example type "Code maintenance", or no work item). A PR under a Feature stays in that Feature work group | From spec 6.4 |

- "A bug is never Feature work. The tracker type decides this, not the AI." (`poc-plan.md` 3.2)
- "A Feature work item counts in the quarter when the feature reaches a done status (merged, tested, closed; kept as a setting)." (`poc-plan.md` 3.2)
- Review comments: "Bot comments and the author's own replies are filtered out before the call." (`poc-plan.md` 3.2); "Reviews and inline review comments are available with file path and diff hunk. Many comments come from the Copilot review bot, and many are the author's own replies. Both must be separated from human reviewer comments." (`poc-plan.md` 1)

Spec 6.1, common fields of every item: Person, Project, Date, Source link, Related items ("A PR links to its task. A task links to its feature. A bug links to a task or a feature"), Evidence type, Tier (in `judgment` here, decision of `poc-plan.md` 3.4), Marker (optional, "Lead highlight" or "self-added").

Spec 6.2, Feature work: "The unit of delivery is one person's part of one feature. The system groups all tasks under a feature that are assigned to the same developer, together with their PRs. This group is one evidence item of the type 'Feature work'. A task with no parent feature is a feature work item by itself. There are no roles such as primary or supporting. [...] A reviewer is credited separately, through Review comment items." Complexity is set "once, when the feature closes"; "The item counts in the quarter in which the feature closes."

Spec 6.5, fields (the three rows):

| Evidence type | Fields | Analytics it feeds |
| --- | --- | --- |
| Feature work | Complexity, the tasks in the group, work type, dates (started, done, released), estimate, sprint moves with reason, linked bugs and reopens with severity and found-in stage, modules changed, linked PRs, whether the AI could read the code or only its metadata | Work classification, Completed work, Work complexity, Delivery outcome, Issues on own work, Planning |
| Review comment written | PR, author reviewed, comment type, substantive or style, date | Review participation, Review effectiveness. Read from the PR author's side: Review findings received |
| Bug fix | Bug item, severity, found-in stage, time to resolve, root cause recorded or not, linked safeguard, modules touched | Fixes and root cause. Also Issues on own work |

PoC difference: assignee is not used (cleared at hand-over, 21% filled, `poc-plan.md` 1). "Assigned to the same developer" is read as "linked from the developer's PRs". Severity and found-in stage do not exist in the data. Complexity, work type and modules come from F06/F07 judgments, not these fields.

### Facts about the real data (Compose database, checked 21 Nov 2026 by SQL)

The database holds F02's 215 PRs merged 1 to 14 Jul 2026 and `openproject-import` run 3 (decision 91).

- 133 of 215 PRs have at least one resolved work item link; 82 have none. Links are found both ways: every linked PR has a link found in its body or title and the same link from OpenProject's side.
- 115 linked PRs link one work item, 18 link more (2 to 9). Examples: `#24153` (dfriquet) links three Features 76543, 76544, 76545 and two Implementations; `#24025` (judithroth, "Update op-blocknote-extensions") links five Bugs and a Code maintenance item; `#23218` (myabc, "Migrate Backlogs to Pragmatic DnD") links Implementations under Feature 76076, three Bugs, an Open Point and two Implementations with no Feature.
- Link targets by type and Feature ancestor: Implementation under a Feature 34, Feature 30, Bug not under a Feature 57, Bug under a Feature 15, Code maintenance not under a Feature 16 (under one: 1), Documentation 13, Implementation with no Feature 3, Task 1, Milestone 1, Open Point 1.
- 32 Features are reached from linked PRs; 31 of them changed into a done status in Jul to Sep 2026. 35 (author, Feature) groups, 34 of them on a done Feature. 61 Bugs are linked; 59 changed into a done status in Q3.
- Done is reached more than once: 59 work items have two or more changes into a done status in Q3 (for example "tested" then "closed"). 8 of 46 stored Features and 14 of 116 Bugs first reached a done status before 1 Jul 2026.
- Review comments (inline, latest version): 735; 101 by bots, 255 by the PR author, 379 by 20 human reviewers. 73 of the 379 are replies inside a thread (`replyTo` set). 120 of the 379 were written before 1 Jul 2026, on PRs merged in July.
- Reviews by humans on others' PRs: 349; 139 carry a summary text (94 of them approvals). Examples: "Nice!", "The fix itself works, however I found a few issues that require attention."
- No stacked PRs ("1/5") in these two weeks. No human comment on a bot's PR.

### Tables (migration V8)

`evidence_item`, never updated or deleted (row trigger, decision 52):

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint identity | |
| `item_key` | text not null | Stable name of the item across rebuilds: `feature_work:<featureNumber>:<personId>`, `bug_fix:<bugNumber>:<personId>`, `review_comment_written:<commentDatabaseId>` |
| `evidence_type` | text not null, check | `feature_work`, `bug_fix`, `review_comment_written` |
| `person_id` | bigint not null → `person` | The PR author (Review comment written: the comment's author) |
| `project_id` | bigint → `project`, nullable | OpenProject project of the Feature or Bug; null for Review comment written |
| `occurred_on` | date not null | The item's date; its quarter is `Quarter.of(occurred_on)` |
| `source_url` | text not null | Feature work: the Feature on OpenProject; Bug fix: the Bug; Review comment written: the comment on GitHub |
| `fields` | jsonb not null | Type-specific fields, below |
| `content_hash` | text not null | SHA-256 of the canonical JSON of everything above plus the sources and links |
| `withdrawn` | boolean not null | true on a row that ends an item that no longer follows from the records |
| `supersedes_id` | bigint unique → `evidence_item`, nullable | The row this one replaces |
| `built_at` | timestamptz not null | |

The current item of a key is its row that no other row supersedes, unless that row is withdrawn. Index on `item_key`.

`evidence_item_source`: `evidence_item_id` → `evidence_item`, `raw_record_id` → `raw_record` (the exact version used), `role` check (`pull_request`, `work_package`, `review_comment`). Primary key on the three. Written once with its item.

`evidence_item_link`: `evidence_item_id` → `evidence_item`, `related_item_key` text, `relation` text check. The target is named by key, so a link stays right when the target gets a new row. Content per question Q13.

`fields` JSONB per type (camelCase, as the API returns them):
- Feature work: `feature` {`number`, `title`, `type`, `url`}, `tasks` [{`number`, `title`, `type`, `url`}] (the work items of the group, the Feature itself included when a PR links it), `pullRequests` [{`externalId`, `number`, `title`, `url`, `mergedAt`}], `doneAt`.
- Bug fix: `bug` {`number`, `title`, `url`, `priority`}, `underFeature` (number or null), `pullRequests` [...], `doneAt`.
- Review comment written: `pullRequest` {`externalId`, `number`, `title`, `url`, `mergedAt`}, `pullRequestAuthorPersonId`, `comment` {`databaseId`, `url`, `path`, `createdAt`, `isReply`}.

No new `setting` rows. `openproject.done_statuses` (F03) and `github.bot_logins` (F02) are read.

## Main flows

Started by `POST /api/jobs/evidence-build/runs` with `{}`, after `identity-build`. Builds from every stored record; no window argument (decision 100).

1. **Bots and persons.** A PR author or comment author is a bot by `BotAccounts.isBot(login, typename, Quarter.of(today))`; bots give no item. A human is mapped by `PersonDirectory.personOfGitHubAccount(databaseId)`; a human with no person fails that item (run `identity-build` first).
2. **Links.** For each stored merged PR (`StoredPullRequests`), its work items are the distinct `work_package_id` of `StoredWorkPackages.linksOf(prExternalId)` with state `resolved`, any `found_in`. A `not_visible` link counts as no link.
3. **Feature work.** For each linked work item that is not a Bug: `StoredWorkPackages.nearestFeature(number)`. When a Feature is found, the PR and the work item join the group (Feature, PR author). Groups with no Feature ancestor: question Q4. PRs linking several Features: Q5. Extra child items: Q7. The group is built only when the Feature has reached a done status (Q2); its date is set by Q1.
4. **Bug fix.** For each linked work item of type Bug: one item per (Bug, PR author) with that author's PRs linking the Bug. `underFeature` from `nearestFeature`. A PR that links both a Bug and a non-Bug item: Q6. Date: Q3.
5. **Review comment written.** For each stored `review_comment` whose author is human and not the PR's author: one item. Replies: Q9. Review summaries: Q8. Date: Q10.
6. **Store.** For each item built: compute its hash. No current row for the key: insert. Current row with the same hash: nothing. Different hash: insert a new row with `supersedes_id`. A current key not built in this run: insert a withdrawn row superseding it (Q12). Sources and links are written with their row.
7. **Counts.** `items_created`, `items_superseded`, `items_withdrawn`, `items_unchanged`, each also per type (`feature_work_created` ...), `pull_requests_read`, `bot_records_skipped`. One item per transaction; a failed item is counted (`items_failed`), the run goes on and fails at the end naming the first 20 keys (`FailedItems`, decision 90).

**Done status.** A work item's done changes are its `StoredWorkPackages.statusChanges(number)` whose status is in `openproject.done_statuses` read for the quarter of that change (UTC day).

**Uses.** `StoredPullRequests.mergedBetween`/`find` (a new `all(repository)` reader may be added), `StoredWorkPackages.find`, `linksOf`, `nearestFeature`, `statusChanges`; `PersonDirectory.personOfGitHubAccount(long)`; `BotAccounts.isBot(String, String, Quarter)`; `SettingService.getTextList`; `CanonicalJson.sha256Hex(JsonNode)`; `Job`, `JobArguments`, `JobCounts`, `FailedItems`; tables `raw_record`, `pull_request_work_item`, `person`, `project`, `setting`.

**Gives.**
- Tables `evidence_item`, `evidence_item_source`, `evidence_item_link`; job `evidence-build`.
- `evidence.CurrentEvidenceItems` (name to be fixed in the plan): the current items of a person, of a type, by key.
- The rule for an item's date and quarter (Q1, Q3, Q10).
- `GET /api/developers` → `[{personId, displayName, counts: {featureWork, bugFix, reviewCommentWritten}}]`; `GET /api/developers/{personId}/evidence-items` → `{person, items: [{id, type, date, quarter, title, sourceUrl, links: [{label, url}], fields}]}`. 404 for an unknown person.
- Web: `/developers` and `/developers/[personId]`, which F08 extends.
- For F06: a PR in a Feature work group is never a Refactor, Security or Infra item; the link and Feature rules above (`nearestFeature` empty means "not under a Feature").

## Edge cases

- PR with only `not_visible` links: no work item; F05 builds nothing for it (F06 and hygiene see it).
- Work item whose parent chain hits an unstored item: `nearestFeature` is empty, so "no Feature ancestor".
- A Feature reopened after done: date stays the chosen done day (Q1); new PRs supersede the item with a larger group.
- Bug fixed by PRs of two authors: two Bug fix items, one each (decision 103).
- A PR author's login changed: the person is found by `databaseId`, so the item is unchanged.
- A source record gets a new version: the hash includes the raw record ids, so the item gets a new row pointing at the new version.
- A run fails halfway: items already written stay; the next run continues by hash comparison (no duplicates).
- Second run with nothing changed: every item `unchanged`, no row written.
- Empty database: the run succeeds with zero counts.
- Developer with no items: listed with zeros; the page says "No evidence items".
- Backend down: the web pages show "backend down", as the home page does.

## How it will be tested

From the master plan "Done when":
1. **20 Feature work groups have the right person, PRs and feature.** After `evidence-build` on the Compose database, the session lists 20 groups (person, Feature with link, PRs with links) in `notes.md`; the user checks them against GitHub and OpenProject. Waiting for the user.
2. **No item whose tracker type is Bug sits in a Feature work group.** Test with a Bug under a Feature; and SQL on real data: count of Feature work sources of role `work_package` whose payload type is Bug = 0.
3. **Building twice gives the same items and no duplicates.** Job test: second run all `unchanged`, row count equal; on real data, a second run writes 0 rows.

Further tests:
- Builder unit tests per rule and per answer of the question brief (multi-Feature PR, Bug and Feature on one PR, Bug under Feature, no Feature ancestor, bot author, own reply, reply, done date, open Feature).
- Store tests: insert, unchanged, supersede, withdraw; the trigger refuses update and delete.
- Architecture tests enabled: `AppendOnlyTablesTest.evidenceItemHasATriggerBeforeEveryUpdateAndDelete`, `AppendOnlyCodeTest.theEvidenceItemEntityIsImmutable`, `noRepositoryOfEvidenceItemCanUpdateOrDelete`, `SpecNumbersTest.theEvidencePackageHoldsNoNumberConstant`.
- API tests (MockMvc): list, one person, unknown person 404, withdrawn and superseded rows not returned.
- Web tests (Vitest): the API client and the two pages render items and links; "backend down".
- Manual: open `/developers` and one developer page on real data; screenshot in `notes.md`.

## Open points

### Business questions

Q1. Which day dates a Feature work item, and so its quarter?
A. The first day the Feature changed into a done status (QA passed). Counts once.
B. The last such day (for example "closed"). Later; moves if reopened.
C. The merge day of the person's last PR in the group. Ignores the tracker.
Recommended: A. Spec "counts when the feature closes"; one quarter only, though 59 items reach done twice.

Q2. A Feature that has not reached a done status yet (1 of 32 now):
A. Not built until it is done.
B. Built now, shown as "open" with no quarter.
Recommended: A. The spec judges a feature when it closes; a later run picks it up.

Q3. Which day dates a Bug fix item?
A. Same rule as Q1: the first day the Bug reached done; not built before.
B. The merge day of that person's last PR for the Bug; built at once.
Recommended: A. One rule for both tracker types; 59 of 61 Bugs are done in Q3.

Q4. A PR whose work item is not a Bug and has no Feature above it (Code maintenance 16, Documentation 13, Implementation 3, other 3):
A. F05 builds nothing for it; F06 decides from the PR's work type (Refactor, Security, Infra item, or Feature work by itself).
B. F05 builds Feature work now; F06 replaces some with another type.
C. F05 builds Feature work marked "provisional" until F06.
Recommended: A. No item is built only to be withdrawn a day later.

Q5. A PR that links several Features (4 PRs, e.g. #24153 links three):
A. It joins the group of each Feature.
B. It joins only the Feature named first in the title, then the body.
Recommended: A. Each Feature's group stays complete; few PRs affected.

Q6. A PR that links a Bug and a non-Bug item (8 PRs, e.g. #23218):
A. It counts in both: the Bug fix item and the Feature work group.
B. Bug fix only.
C. Feature work only.
Recommended: A. Each work item keeps the PR that did it; the Bug itself stays out of the group.

Q7. Which "child items" are in a person's Feature work group?
A. Only the work items their PRs link to.
B. Also items under the Feature whose "Developers" names their OpenProject user.
Recommended: A. Rests on the PR author only; "Developers" is a proposed match (F04).

Q8. Does a review's summary text count as a Review comment written?
A. No: inline comments only (379).
B. Yes: also summaries with text (139, many "Nice!").
C. B plus the PR conversation comments.
Recommended: A. F07 judges each comment with its diff hunk; a summary has none.

Q9. A reviewer's reply inside a thread (73 of 379):
A. Counts as its own item.
B. Not counted; only comments that start a thread.
Recommended: A. Replies often carry the finding; F07 marks a "thanks" as style.

Q10. Which day dates a Review comment written item?
A. The day the comment was written (120 of 379 fall in June, before Q3).
B. The merge day of the PR.
Recommended: B. The window is chosen by merge day, so all its comments count in it.

Q11. The list page:
A. A developer list (name, count per type), then one page per developer with three sections; review comments grouped per PR; each item shows date and quarter; no quarter filter yet.
B. One page: a table of all items with a developer filter.
Recommended: A. Reads well on a phone; F08 and F12 build on per-developer pages.

### Technical decisions I need your direction on

Q12. When a rebuild finds an item changed (a new PR in a group):
A. A stable key per item; a changed item gets a new row that supersedes the old; an item that no longer follows gets a "withdrawn" row.
B. A new row for every item on every run.
C. Stable key, new row on change, but a vanished item stays current.
Recommended: A. Append-only, no duplicates, judgments stay on the row they judged.

Q13. The related-items table (spec 6.1):
A. Fill one link now: a Bug fix whose Bug sits under a Feature links to that Feature's Feature work items.
B. Create the table, leave it empty until a feature reads it.
Recommended: A. Spec 6.1 names this link; cheap now; the item page (F08) can show it.

### Decisions I made myself

D100 to D111 in `docs/decisions.md`:
- D100. Job `evidence-build`, no arguments, builds from all stored records, after `identity-build`; a failed item is counted and the run fails at the end.
- D101. Tables and columns as in "Data and tables"; no `marker` column (validated additional work is out of scope).
- D102. Bots give no item (`BotAccounts`, run day's quarter); a human author with no person fails that item.
- D103. Bug fix: one item per Bug and PR author.
- D104. A link counts when `resolved`, wherever found; `not_visible` is no link.
- D105. Project: the Feature's or Bug's OpenProject project; none for review comments.
- D106. Source link: Feature or Bug on OpenProject, comment on GitHub; sources keep the exact raw record versions, and a new version of a source gives a new row.
- D107. Done statuses are read with the setting in force in the quarter of each status change.
- D108. Readers of stored review comments live in `source.github`.
- D109. API `GET /api/developers` and `GET /api/developers/{personId}/evidence-items` in `api`, current items only, 404 for an unknown person.
- D110. Web: CSS Modules, no new dependency, server-side fetch like the home page, bold sky-blue colours.
- D111. A human's comment on a bot's PR counts for the reviewer (none in the data now).
