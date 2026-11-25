# F08 Blind check: notes

## Differences from the plan

- Batch 1 (`cd79279`, `6da3bee`, `9f49868`): one trigger function, `blind_check_row_is_never_changed`, serves the three tables. `human_label` checks that `complexity` is null exactly when `cannot_judge` is true. The three tables were also added to `AppendOnlyCodeTest` and `SchemaTest`. `ItemJudgment` gained `id` as its first component (the sample fixes the judgment row); `CurrentEvidenceItems.ofType(EvidenceType)` is new. The hash order is SHA-256 of the item id. Too few items of a value answers 409. The CSV rows (`BlindCheckExport`, `CsvWriter`) are in `api`, because `ItemTitleAndLinks` is package-private; `review` hands over `BlindCheckItem` rows. With no sample the export is the header only. The setting is read for the quarter of today's date. `picked_at` is cut to microseconds, as the database stores it. The migration and its table tests were written together, without a separate red run.
- Batch 2 (`ae6e1b7`, `6c7c1fd`, `fdcc5c8`, and the numbers commit): the import reads the body as bytes and decodes UTF-8 itself, because Spring reads `text/csv` without a charset as ISO-8859-1. `notLabeledYet` of the import answer counts the sample items that still have no label after the import, not the empty rows of the file. A `cannot_judge` item gets no reason mark (409). `HumanLabel` does not map `supersedes_id` (a label is given once). `JudgmentsById` (package `judgment`) reads the judgment the sample fixed. The numbers are fetched by one SQL query per call (one row per sample item) and counted in `BlindCheckNumbers`; `api.BlindCheckReport` adds the titles. `ItemBlindCheck` (label and mark) is serialized as the `blindCheck` of the item detail.
- Batch 3 (`aeba0c4`, `b929747`, `1e3277e`, `fb10a3e`): `web/lib/backend.ts` gained `writeBackend` and `sendBackend`; `readBackend` now sits on `sendBackend`. The web tests run without a DOM, so "saving sends the verdict of every problem" renders the form's markup, builds the form data from the radio names and checks the request body; the PUT of the server action is tested apart with a stubbed `fetch`. The download route answers 409 (too few items), 503 (backend down) or 502 (export refused) as plain text. "Blind check" is in `SectionLinks.tsx`.
- Batch 4: the labeling guide was committed with the notes of batch 1 (`4d06bca`), the commands with the closing commit.

## Review (one round, 25 Nov 2026)

One blocking finding, fixed in `a4b5258`: `GET /api/evidence-items/{id}` still gave the top-level `tier` of a hidden item, and for Feature work the tier is the complexity. The detail now blanks it while the state is `hidden_until_labeled`; the test asserts it.

Fixed as follow-ups in `c5e2ce3`: the detail of a sampled item shows the judgment the sample fixed, not the current one (decision 329), tier included; a problem mark without a verdict answers 400, not 500; `ReasonMarkRepository.findCurrentOfItems` and `BlindCheckFixture.person()` removed; `AppendOnlyCodeTest` checks the three entities and repositories; a unique index on `human_label (blind_check_item_id) where supersedes_id is null` in V15; an unclosed quote in the label file is refused with its line; the refusal says "1 row ... is wrong"; tests for the sample refusal message, `very_high` against high, and the numbers after a new judgment.

## Follow-ups

- `review/BlindCheckReader.ofItem`: a sampled item superseded by a later `evidence-build` gets a new row id that is not in the sample, so its judgment shows unhidden under the new id. No `evidence-build` runs before the labels are in; the guide tells the user to keep profiles closed.
- `review/ReasonMark.getBlindCheckItemId()` and `getSupersedesId()` have no caller. They stay because Error Prone's `UnusedVariable` does not see the JPA reads of the fields, and a check is never switched off.
- `evidence/CurrentEvidenceItems.ofType` loads every current item and filters by type in Java. Fine for 42 items; a query by type when the data grows.
- `review/LabelImport`: the rows "not in the sample" and "in the file twice" name the problem but not what is allowed.
- `api/EvidenceItemSummaries`: the summary list `GET /api/evidence-items?ids=` and the profile pages still show the tier and complexity of sampled items before the labels (decision 348: by discipline).
- One stored reason text shows `&quot;` on the item page: the answer was stored HTML-escaped (F16 follow-up), not an F08 fault.

## Verification (25 Nov 2026)

Full suites: backend `./mvnw verify` 1,106 tests, 0 failures, 0 skipped (after the review fixes). Web: format check, lint, type check, 308 tests in 30 files, build green (the web did not change after that run).

Real data: a throwaway copy of `ei_latest` (`ei_f08_check`, dropped afterwards), a backend from the built jar on port 8082, the built web on port 3217. `ei_latest` itself was not touched: it has no sample yet and is still at V14. The labels and marks of the check were made up by a script; they are not the user's and are gone with the copy. The check ran once before the review fixes and the parts the fixes touch ran again after them.

1. **The exported CSV contains no AI answer in any column: passed.** The sample is 10 low, 10 medium, 10 high (SQL on `blind_check_item` joined to `judgment`); the positions of each value are spread over 1 to 30 (high: 4, 6, 9, 13, 21, 23, 25, 28, 29, 30). The file has the seven columns and 30 rows, with `complexity` and `note` empty in all. A script searched the rows for 220 texts of the 30 fixed judgments (reason, model, prompt version, every problem name and depth reason, both flag reasons): 0 found. The words low, medium, high, routine, answer exists, no ready answer, sonnet and feature-work-complexity appear in no cell. A second export after the fixes is byte for byte the same file.
2. **The item page of a labeled item shows both answers: passed.** Before the import, `/api/evidence-items/9` answers `tier: null` and `judgment.state: hidden_until_labeled`, and `/items/3` reads "AI judgment: hidden until your label is imported. Your label: not labeled yet". After it, `/items/9` shows "Complexity: Low" with the problems, "Your label: Low, same", the note, and the form "Mark the AI's reason". Web test "a labeled item shows your label beside the AI judgment".
3. **Each number equals a count made with separate SQL: passed on made-up labels; to be repeated on the user's.** With 27 labels (3 `cannot_judge`) and 19 marks, `GET /api/blind-check` and hand-written SQL gave the same: sampled 30, labeled 27, cannot judge 3, marked 19, exact 7 of 24, within one step 21 of 24, table exact 7 of 24, reason right 17 of 19, thin evidence 6 items with 3 of 6 and 5 of 5, 17 disagreements, 5 reasons to mark, the four verdict counts (21, 11, 11, 10) and all 11 pairs of the label against AI table. After the fixes: 7, 21, 7 of 24 again.
4. **The feasibility report is written and the user has read it: waiting for the user's labels** (decision 353).

Other checks, all on the copy: a file with two wrong rows stored nothing and named both (line 4 "huge", line 6 item 99999); a `;` separated file with a byte order mark and mixed case was read; a second import of the same file labeled nothing more; a changed label was refused ("already labeled low"); a mark on an item without a label or with `cannot_judge` answered 409, outside the sample 404, with a wrong problem list or no verdict 400; a second mark superseded the first (20 rows, 19 current); `/blind-check` showed every number of the API and the download answered `text/csv` as an attachment.

## What a later feature should know

- To run the check: backend with `POSTGRES_DB=ei_latest` (it migrates to V15), web with `BACKEND_URL` to it, then `localhost:3000/blind-check`. The first download picks the sample, once.
- Tables: `blind_check_item` (sample, fixed `judgment_id`, `position`), `human_label`, `reason_mark` (current = no row supersedes it). All append-only.
- Endpoints: `POST /api/blind-check/sample`, `GET /api/blind-check/sample.csv`, `POST /api/blind-check/labels` (`text/csv`), `PUT /api/evidence-items/{id}/reason-mark`, `GET /api/blind-check`. The detail of a sampled item shows the fixed judgment.
- The report session: read `GET /api/blind-check`, repeat the SQL counts of check 3 on `ei_latest` (the queries are in the session log of 25 Nov; they join `blind_check_item`, its `judgment` and `human_label`, and take the `reason_mark` rows nothing supersedes), then write `docs/feasibility-report.md` after design flow 8.
- A tuning round (decision 350) adds `feature-work-complexity/v3`; the 30 fixed judgments and their numbers do not move. The 12 items outside the sample have no labels.
