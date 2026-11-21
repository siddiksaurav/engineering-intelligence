# F05 Evidence builders without AI, and the evidence list page: notes

## Waiting for a later feature

- `evidence_item_link` is created by V8 and stays empty in F05 (decision 124). The first feature that reads related items fills it.

## Differences from the plan

- Task 1: an index on `evidence_item.person_id` was added for the per-person reads. `SchemaTest` lists the three new tables. The migration and its test were written together, so the table test was not seen failing first (`SchemaTest` failed first). An extra test refuses an unknown `evidence_type`.
- Task 2: `EvidenceItem` holds `personId` and `projectId` as plain values, not entity references; sources are an eager `@ElementCollection` of the embeddable `EvidenceItemSource`. Finders: `findCurrentByKey`, `findLatestRowOfKey` (withdrawn or not; the store needs it to supersede a withdrawn row), `findAllCurrent`, `findCurrentOfPerson`.
- Task 3: a withdrawn row copies type, person, date, source URL and fields of the row it ends and has no sources; its hash covers the key, the withdrawn flag and the hash of the row it ends. `withdraw` of a key with no current item throws `IllegalStateException` naming the key.
- Task 4: typed readers `GitHubAuthor`, `PullRequestFacts.of(RawRecord)`, `ReviewCommentFacts.of(RawRecord)` keep the JSON paths in `source.github`. Tasks 4 and 5 tests failed first by compile error.
- Task 5: `WorkPackageFacts` comes from `StoredWorkPackages.facts(number)`; `projectId` is the `project` table id (null when not stored), `priority` null when absent. Added `WorkPackageType.nameOf`, `isBug`, `ProjectRepository.findId`. `firstDoneAt` read the setting per change quarter (decision 107), which throws for history before 2026-Q2: replaced by decision 125 and fixed in batch 3.
- Task 6: builders return `BuildResult(items, failures)`; a failure text names the item key with the login (`feature_work:<feature>:<login>: no person ...`). A deleted GitHub account (no author) counts as a human with no person (`unknown author`). Sources: PRs, then the Feature, then the tasks. Shared `LinkedPullRequests`, `LinkedPullRequest`, `WorkItemLookup` (per-build cache), `ItemFields`.
- Task 7: a Bug fix item's sources are the author's PRs and the Bug; `fields.underFeature` is not backed by a source, so a change of the Feature alone does not change the item.
