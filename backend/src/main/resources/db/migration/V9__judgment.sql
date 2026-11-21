-- F06 design, "Table judgment". Never updated, never deleted. A correction or a new judgment of the
-- same subject is a new row that supersedes the old one (decision 127).
create table judgment (
    id                   bigint generated always as identity primary key,
    kind                 text        not null check (kind in ('pull_request_digest', 'item_tier')),
    raw_record_id        bigint references raw_record (id),
    evidence_item_id     bigint references evidence_item (id),
    value                jsonb       not null,
    tier                 text check (tier in ('T1', 'T2', 'T3', 'T4')),
    reason               text        not null check (btrim(reason) <> ''),
    facts                jsonb       not null check (jsonb_typeof(facts) = 'array' and jsonb_array_length(facts) >= 1),
    confidence           numeric,
    judged_by            text        not null check (judged_by in ('model', 'person')),
    model                text,
    prompt_version       text,
    input_hash           text        not null,
    based_on_judgment_id bigint references judgment (id),
    supersedes_id        bigint unique references judgment (id),
    judged_at            timestamptz not null,
    constraint judgment_one_subject_ck check ((raw_record_id is null) <> (evidence_item_id is null)),
    constraint judgment_model_named_ck
        check (judged_by <> 'model' or (model is not null and prompt_version is not null))
);

-- The same input is never judged twice by a model, in the database as well as in code.
create unique index judgment_model_input_uq on judgment (kind, input_hash) where judged_by = 'model';
create index judgment_raw_record_idx on judgment (raw_record_id);
create index judgment_evidence_item_idx on judgment (evidence_item_id);

create function judgment_is_never_changed() returns trigger
    language plpgsql
as
$$
begin
    raise exception 'judgment is never updated or deleted';
end
$$;

create trigger judgment_no_update_or_delete
    before update or delete
    on judgment
    for each row
execute function judgment_is_never_changed();

alter table evidence_item drop constraint evidence_item_evidence_type_check;
alter table evidence_item add constraint evidence_item_evidence_type_check
    check (evidence_type in ('feature_work', 'bug_fix', 'review_comment_written', 'test_change',
                             'refactor_performance', 'security', 'infra_release', 'documentation'));

alter table evidence_item_source drop constraint evidence_item_source_role_check;
alter table evidence_item_source add constraint evidence_item_source_role_check
    check (role in ('pull_request', 'pull_request_diff', 'work_package', 'review_comment'));

-- Checked against the real file names of opf/openproject on 22 Nov 2026.
insert into setting (key, valid_from_quarter, value, description) values (
    'judgment.diff_excluded_paths',
    '2026-Q2',
    '["Gemfile.lock", "package-lock.json", "yarn.lock", "*.lock", "config/locales/crowdin/", "modules/*/config/locales/crowdin/", "vendor/", "frontend/vendor/", "*.min.js", "*.min.css", "*.map", "db/structure.sql"]',
    'Path patterns of files whose patch is not sent to the model: lock files, machine translations, vendored, minified and generated files. The file is still named in the file list. Read by F06.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'evidence.test_paths',
    '2026-Q2',
    '["spec/", "test/", "tests/", "*_spec.rb", "*.spec.ts", "*.test.ts", "modules/*/spec/"]',
    'Path patterns that make a changed file a test file. A pull request with one is a Test change (decision 148). Read by F06.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'evidence.test_kind_tiers',
    '2026-Q2',
    '{"unit": "T1", "integration": "T2", "regression": "T2", "ci_gate": "T3"}',
    'The tier of a Test change by its test kind (decision 147). Read by F06.'
);
