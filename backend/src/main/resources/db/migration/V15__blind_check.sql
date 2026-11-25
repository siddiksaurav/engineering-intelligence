-- F08 design, "New tables". The sample of the blind check, the user's labels and the user's marks of the AI's
-- reasons. All three are append-only, as judgment is: a correction is a new row that supersedes the old one.
create table blind_check_item (
    id               bigint generated always as identity primary key,
    evidence_item_id bigint      not null unique references evidence_item (id),
    judgment_id      bigint      not null references judgment (id),
    position         int         not null unique check (position >= 1),
    picked_at        timestamptz not null
);

create table human_label (
    id                  bigint generated always as identity primary key,
    blind_check_item_id bigint      not null references blind_check_item (id),
    complexity          text check (complexity in ('low', 'medium', 'high', 'very_high')),
    cannot_judge        boolean     not null,
    note                text,
    labeled_at          timestamptz not null,
    supersedes_id       bigint unique references human_label (id),
    constraint human_label_complexity_or_cannot_judge_ck check ((complexity is null) = cannot_judge)
);
create index human_label_item_idx on human_label (blind_check_item_id);
-- A label is given once (decision 333), so an item has at most one label that supersedes nothing.
create unique index human_label_one_first_label_per_item_idx on human_label (blind_check_item_id) where supersedes_id is null;

create table reason_mark (
    id                  bigint generated always as identity primary key,
    blind_check_item_id bigint      not null references blind_check_item (id),
    reason_right        boolean     not null,
    problem_marks       jsonb       not null check (jsonb_typeof(problem_marks) = 'array'),
    note                text,
    marked_at           timestamptz not null,
    supersedes_id       bigint unique references reason_mark (id)
);
create index reason_mark_item_idx on reason_mark (blind_check_item_id);

create function blind_check_row_is_never_changed() returns trigger
    language plpgsql
as
$$
begin
    raise exception '% is never updated or deleted', tg_table_name;
end
$$;

create trigger blind_check_item_no_update_or_delete
    before update or delete
    on blind_check_item
    for each row
execute function blind_check_row_is_never_changed();

create trigger human_label_no_update_or_delete
    before update or delete
    on human_label
    for each row
execute function blind_check_row_is_never_changed();

create trigger reason_mark_no_update_or_delete
    before update or delete
    on reason_mark
    for each row
execute function blind_check_row_is_never_changed();

insert into setting (key, valid_from_quarter, value, description) values (
    'blind_check.items_per_complexity',
    '2026-Q2',
    '10',
    'How many Feature work items of each of the complexities low, medium and high the blind check picks (decision 345). Read by F08.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'blind_check.targets',
    '2026-Q2',
    '{"exactMatch": 0.70, "withinOneStep": 0.95, "reasonRight": 0.90}',
    'The shares the blind check measures the AI''s complexity judgment against (decision 349; poc-plan.md section 4). With 30 items they are an indication, not proof. Read by F08.'
);
