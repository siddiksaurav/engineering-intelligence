# F05 Evidence builders without AI, and the evidence list page: notes

## Waiting for a later feature

- `evidence_item_link` is created by V8 and stays empty in F05 (decision 124). The first feature that reads related items fills it.

## Differences from the plan

- Task 1: an index on `evidence_item.person_id` was added for the per-person reads. `SchemaTest` lists the three new tables. The migration and its test were written together, so the table test was not seen failing first (`SchemaTest` failed first). An extra test refuses an unknown `evidence_type`.
- Task 2: `EvidenceItem` holds `personId` and `projectId` as plain values, not entity references; sources are an eager `@ElementCollection` of the embeddable `EvidenceItemSource`. Finders: `findCurrentByKey`, `findLatestRowOfKey` (withdrawn or not; the store needs it to supersede a withdrawn row), `findAllCurrent`, `findCurrentOfPerson`.
- Task 3: a withdrawn row copies type, person, date, source URL and fields of the row it ends and has no sources; its hash covers the key, the withdrawn flag and the hash of the row it ends. `withdraw` of a key with no current item throws `IllegalStateException` naming the key.
