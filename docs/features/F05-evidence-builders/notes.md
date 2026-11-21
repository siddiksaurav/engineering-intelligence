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
- Task 7b (decision 125): `StoredWorkPackages.firstDoneAt(number, quarter)` checks every change against the done statuses of the given quarter; the job passes the run day's quarter. The builders take the quarter and the pull requests `LinkedPullRequests.read(quarter)` returned (`Read`: linked pull requests, pull requests read, bot pull requests skipped), so the job reads them once and counts the bots once. `LinkedPullRequests` no longer holds a `Clock`.
- Task 8: `BuildResult` gained `botRecordsSkipped` (the review comment builder counts the bot comments). `ItemFields.pullRequest` made for one pull request.
- Task 9: `EvidenceItemStore.withdraw` returns the type of the item it ended, for the per-type count. A failed item is not withdrawn: a failed write keeps its key, and a builder failure names a key with the login, which no stored item has, so the failure records carry no separate key.
- Task 10: `CurrentEvidenceItems.ofPerson(id)` and `countsPerPerson()`; `PersonDirectory.all()` and `find(id)` with public record `PersonSummary`; `api` holds `DeveloperController`, `DeveloperExceptionHandler`, `DeveloperNotFoundException` (404 naming the id). Titles and links come from `fields`; a malformed row fails loudly. Link labels "Work item #N on OpenProject", "Pull request #N on GitHub", "Comment on GitHub".
- Task 11: `fetchDevelopers` and `fetchEvidenceItems` return `ok`, `down` or `not-found`; any non-404 error, failed connection, bad JSON, timeout or missing `BACKEND_URL` is `down` and logged.
- Task 12: new `app/globals.css`; accents blue (Feature work), orange (Bug fix), green (Review comment written). A review comment group shows the PR title once; each comment shows path, date, quarter and link. Tests of tasks 10 to 12 were written first but not run against stubs.

## Verification

Full suites on 22 Nov 2026 (before the review): backend `./mvnw verify` 489 tests, 0 failures, 8 skipped (disabled architecture tests of F06, F10, F11), build green. Web: format, lint, type check, 48 tests, build all green.

Real data, Compose database (F02's 215 PRs of 1 to 14 Jul 2026, `openproject-import` run 3, `identity-build` run 6):
- `evidence-build` run 7: `items_created` 476 (`feature_work_created` 35, `bug_fix_created` 62, `review_comment_written_created` 379), `pull_requests_read` 215, `bot_records_skipped` 144, `items_failed` 0. 4 seconds.
- **Building twice gives the same items and no duplicates: passed.** Run 8: `items_unchanged` 476, nothing written; `evidence_item` 476 rows, 476 keys. Job test `EvidenceBuildJobTest.aSecondRunWritesNoRow`.
- **No Bug in a Feature work group: passed.** SQL: Feature work sources of role `work_package` whose type is Bug: 0; tasks of type Bug in `fields.tasks`: 0. Tests in `FeatureWorkBuilderTest`.
- **Pages:** `/developers` and `/developers/17` (mrmir) render the real items; at 375px no element is wider than the screen; three sections present.

**Waiting for the user: check these 20 Feature work groups** (person, Feature, first done day, PRs on github.com/opf/openproject/pull/N, work items on community.openproject.org/wp/N):
1. shiroginne, F76145 Add "as parent" badge to referencing wiki pages, 2026-07-24, PR 23849, items 76145
2. thykel, F74362 Adapt BCF Export and Import for semantic identifiers, 2026-08-04, PR 23694, items 74362
3. brunopagno, F76166 Make target_versions the canonical data source, 2026-08-18, PRs 23946 24093 24142 24150, items 76170 76174 76177 76179 76180
4. mrmir, F76296 Fetch agenda items by WorkPackage ID via the API, 2026-07-28, PR 24060, items 76296
5. oliverguenther, F76862 Improve status filtering for user administration, 2026-07-16, PR 24120, items 76862
6. mrmir, F71922 Caption to recurring meeting forms, 2026-07-07, PR 24096, items 71922
7. ihordubas99, F68818 Work package link when pasting a URL into BlockNote, 2026-07-15, PR 24255, items 68818
8. akabiru, F76166 Make target_versions the canonical data source, 2026-08-18, PRs 23965 24079 24146 24161 24209, items 76172 76176 77218
9. toy, F76097 "Add new work package" in backlog context menu, 2026-07-15, PR 23403, items 76097
10. klaustopher, F76619 Wizard-like creation flow for type variants, 2026-07-24, PR 24231, items 76619
11. ulferts, F76741 Sprint sharing in EE basic, 2026-07-03, PR 24074, items 76741
12. HDinger, F76748 Overview widget for project phases and gates, 2026-07-21, PR 24175, items 76748
13. klaustopher, F76565 Switching the active type variant with a preview, 2026-10-23, PR 24192, items 77226
14. mrmir, F76542 Administrators define and manage global type variants, 2026-08-04, PRs 24067 24102 24127, items 76577 76580 76582
15. klaustopher, F74094 Build User Timeline View for Resource Management, 2026-07-02, PR 24026, items 74094
16. toy, F76642 Additional calculation operators for calculated fields, 2026-07-27, PR 24246, items 76642
17. HDinger, F77184 Replace outdated danger zone when creating a backup, 2026-07-17, PR 24200, items 77184
18. dfriquet, F76545 Reuse a type's PDF and subject configuration, 2026-08-05, PRs 24153 24225, items 76545 76631 76632 76635
19. klaustopher, F75536 Allocate real users to generic requests, 2026-07-01, PR 23912, items 75536
20. ihordubas99, F76013 Preview for tiny work package links in documents, 2026-07-22, PR 24255, items 76013

Notes on the list: PR 24255 (a dependency bump) joins two Features (decision 116); group 13 is dated in Q4 (first done 23 Oct 2026), so it does not count in 2026-Q3.
