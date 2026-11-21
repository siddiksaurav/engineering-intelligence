-- F05 evidence items. See docs/features/F05-evidence-builders/design.md, "Tables (migration V8)".

-- Never updated, never deleted. A change is a new row that supersedes the old one (decision 123).
create table evidence_item (
    id            bigint generated always as identity primary key,
    item_key      text not null,
    evidence_type text not null check (evidence_type in ('feature_work', 'bug_fix', 'review_comment_written')),
    person_id     bigint not null references person (id),
    project_id    bigint references project (id),
    occurred_on   date not null,
    source_url    text not null,
    fields        jsonb not null,
    content_hash  text not null,
    withdrawn     boolean not null,
    supersedes_id bigint unique references evidence_item (id),
    built_at      timestamptz not null
);

create index evidence_item_key_idx on evidence_item (item_key);
create index evidence_item_person_idx on evidence_item (person_id);

create table evidence_item_source (
    evidence_item_id bigint not null references evidence_item (id),
    raw_record_id    bigint not null references raw_record (id),
    role             text not null check (role in ('pull_request', 'work_package', 'review_comment')),
    primary key (evidence_item_id, raw_record_id, role)
);

-- Created and left empty in F05 (decision 124). The target is named by key, so a link stays right
-- when the target gets a new row.
create table evidence_item_link (
    evidence_item_id bigint not null references evidence_item (id),
    related_item_key text not null,
    relation         text not null
);

create function evidence_item_is_never_changed() returns trigger
    language plpgsql
as
$$
begin
    raise exception 'evidence_item is never updated or deleted';
end
$$;

create trigger evidence_item_no_update_or_delete
    before update or delete
    on evidence_item
    for each row
execute function evidence_item_is_never_changed();
