# F08 Blind check: notes

## Differences from the plan

- Batch 1 (`cd79279`, `6da3bee`, `9f49868`): one trigger function, `blind_check_row_is_never_changed`, serves the three tables. `human_label` checks that `complexity` is null exactly when `cannot_judge` is true. The three tables were also added to `AppendOnlyCodeTest` and `SchemaTest`. `ItemJudgment` gained `id` as its first component (the sample fixes the judgment row); `CurrentEvidenceItems.ofType(EvidenceType)` is new. The hash order is SHA-256 of the item id. Too few items of a value answers 409. The CSV rows (`BlindCheckExport`, `CsvWriter`) are in `api`, because `ItemTitleAndLinks` is package-private; `review` hands over `BlindCheckItem` rows. With no sample the export is the header only. The setting is read for the quarter of today's date. `picked_at` is cut to microseconds, as the database stores it. The migration and its table tests were written together, without a separate red run.
