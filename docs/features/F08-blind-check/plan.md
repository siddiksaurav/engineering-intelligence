# F08 Blind check: plan

Spec: `docs/features/F08-blind-check/design.md` (approved 25 Nov 2026).

The `review` package holds the sample, the labels, the marks and the numbers. The controllers are in `api`, as every controller; titles and links come from `api.ItemTitleAndLinks`.

## Batch 1: tasks 1 to 3 (tables, sample, export)

**Task 1. Migration V15.** The tables `blind_check_item`, `human_label`, `reason_mark` with their checks and append-only triggers; the settings `blind_check.items_per_complexity` and `blind_check.targets`.
- Files: `backend/src/main/resources/db/migration/V15__blind_check.sql`, `architecture/AppendOnlyTablesTest.java`, `DatabaseTest.java` (truncate line).
- **Test first:** `AppendOnlyTablesTest.humanLabelHasATriggerBeforeEveryUpdateAndDelete` (and the same for the two other tables): each table refuses update and delete.
- Commit: `feat(F08): tables of the blind check`

**Task 2. Pick the sample.** `POST /api/blind-check/sample`: per complexity the number of the setting, in hash order, the judgment fixed, stored once; a second call returns the stored sample; too few items of a value is refused with the value and the count.
- Files: `review/BlindCheckSample.java` and what it needs (entity, repository, settings reader), `api/BlindCheckController.java`, its exception handling, tests.
- **Test first:** `BlindCheckSampleTest.picksTheSameNumberOfItemsOfEachComplexityAndKeepsThemOnASecondCall`.
- Commit: `feat(F08): balanced sample of Feature work items`

**Task 3. Export.** `GET /api/blind-check/sample.csv`: the seven columns in position order, RFC 4180, no value of any judgment.
- Files: `review/` (the rows of the sample), `api/BlindCheckController.java`, a CSV writer, tests.
- **Test first:** `BlindCheckExportTest.theFileHoldsNoWordOfAnyJudgment`: items judged low, medium and high are exported; no cell holds a complexity, a depth, a reason or a model name, and the header is exactly the seven columns.
- Commit: `feat(F08): CSV export without the AI's answer`

## Batch 2: tasks 4 to 7 (import, both answers, mark, numbers)

**Task 4. Import.** `POST /api/blind-check/labels` (`text/csv`): the rules of design flow 4 (all or nothing, every wrong row named, empty means not labeled yet, a label is given once, `cannot_judge`, `,` or `;`, byte order mark, case and spaces).
- Files: `review/LabelImport.java`, a CSV reader, `HumanLabel` entity and repository, `api/BlindCheckController.java`, tests.
- **Test first:** `LabelImportTest.oneWrongRowStoresNothingAndEveryWrongRowIsNamed`.
- Commit: `feat(F08): import of the blind labels`

**Task 5. Both answers on the item.** `GET /api/evidence-items/{id}` gains `blindCheck`; the judgment of a sampled item without a label is `hidden_until_labeled`.
- Files: `api/EvidenceItemDetailResponse.java`, `api/EvidenceItemSummaries.java`, `api/ItemJudgmentResponse.java`, a reader in `review/`, tests.
- **Test first:** `EvidenceItemControllerTest.theJudgmentOfASampledItemIsHiddenUntilItsLabelIsImported`.
- Commit: `feat(F08): the label beside the AI judgment, hidden until labeled`

**Task 6. Reason mark.** `PUT /api/evidence-items/{id}/reason-mark`: stores the verdict per item and per problem; 404 outside the sample, 409 without a label, 400 when the problem list does not match the fixed judgment; a second mark supersedes.
- Files: `review/ReasonMarks.java`, entity and repository, `api/` controller and request record, tests.
- **Test first:** `ReasonMarksTest.aMarkNeedsALabelAndASecondMarkSupersedesTheFirst`.
- Commit: `feat(F08): mark of the AI's reason`

**Task 7. The numbers.** `GET /api/blind-check`: the shape of design flow 7; shares null when nothing counts.
- Files: `review/BlindCheckNumbers.java` (SQL), `api/BlindCheckController.java`, response records, tests.
- **Test first:** `BlindCheckNumbersTest.exactMatchWithinOneStepAndReasonRightAreCountedOverTheRightItems`.
- Commit: `feat(F08): numbers of the blind check`

## Batch 3: tasks 8 to 11 (web)

**Task 8. Item page shows both answers.** "Your label" beside "AI judgment" with "same", "one step apart" or "two or more steps apart"; the hidden state says "hidden until your label is imported".
- Files: `web/lib/items.ts`, `web/lib/blindCheck.ts`, `web/app/items/[itemId]/page.tsx`, a `YourLabel.tsx`, `JudgmentView.tsx`, tests.
- **Test first:** `page.test.tsx` "a labeled item shows your label beside the AI judgment".
- Commit: `feat(F08): item page shows the blind label`

**Task 9. Reason mark form.** On a labeled item: right or wrong, a verdict per problem, a note, Save, through a server action; the stored mark is shown filled in.
- Files: `web/app/items/[itemId]/ReasonMarkForm.tsx`, `actions.ts`, `web/lib/blindCheck.ts`, tests.
- **Test first:** `ReasonMarkForm.test.tsx` "saving sends the verdict of every problem".
- Commit: `feat(F08): reason mark form on the item page`

**Task 10. Page `/blind-check`.** Figures, the three numbers against the targets, the label against AI table, problem verdicts, thin evidence, disagreements and "Reasons to mark" with links; a link in the navigation.
- Files: `web/app/blind-check/page.tsx`, its module CSS, `web/lib/blindCheck.ts`, the navigation, tests.
- **Test first:** `blind-check/page.test.tsx` "every number on the page equals the API value".
- Commit: `feat(F08): blind check page`

**Task 11. Download and upload.** A route handler passes the CSV through (picking the sample first when none exists); an upload form with a server action shows the counts or the wrong rows.
- Files: `web/app/blind-check/sample.csv/route.ts`, `UploadLabels.tsx`, `actions.ts`, tests.
- **Test first:** `UploadLabels.test.tsx` "a refused file lists every wrong row".
- Commit: `feat(F08): download and upload of the label file`

## Batch 4: task 12 (documents, by the session)

**Task 12.** `docs/blind-check-labeling-guide.md`; the F08 commands in `CLAUDE.md`.
- Commit: `docs(F08): labeling guide, commands`

## Verify

`cd backend && ./mvnw verify`; `cd web && npm run format:check && npm run lint && npm run typecheck && npm test && npm run build`. Then the checks of "How it will be tested" in the design, on `ei_latest`.
