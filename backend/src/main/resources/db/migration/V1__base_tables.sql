-- F01 base tables. See docs/features/F01-repository-skeleton/design.md section 6.

create table data_source (
    id       bigint generated always as identity primary key,
    key      text not null unique,
    name     text not null,
    base_url text not null
);

insert into data_source (key, name, base_url) values
    ('github', 'GitHub', 'https://github.com'),
    ('openproject', 'OpenProject', 'https://community.openproject.org');

-- Never updated, never deleted. One row per version of a record.
create table raw_record (
    id             bigint generated always as identity primary key,
    data_source_id bigint not null references data_source (id),
    record_kind    text not null,
    external_id    text not null,
    version        int not null,
    url            text not null,
    content_hash   text not null,
    payload        jsonb not null,
    fetched_at     timestamptz not null,
    constraint raw_record_version_uq unique (data_source_id, record_kind, external_id, version)
);

-- A value is in force from valid_from_quarter until a row with a later quarter replaces it.
create table setting (
    id                 bigint generated always as identity primary key,
    key                text not null,
    valid_from_quarter text not null check (valid_from_quarter ~ '^[0-9]{4}-Q[1-4]$'),
    value              jsonb not null,
    description        text not null,
    constraint setting_key_quarter_uq unique (key, valid_from_quarter)
);

create table person (
    id           bigint generated always as identity primary key,
    display_name text not null,
    level        text check (level in ('L1', 'L2', 'L3')),
    created_at   timestamptz not null default now()
);

create table person_identity (
    id             bigint generated always as identity primary key,
    person_id      bigint not null references person (id),
    data_source_id bigint not null references data_source (id),
    external_id    text not null,
    username       text,
    constraint person_identity_external_uq unique (data_source_id, external_id)
);

create table project (
    id             bigint generated always as identity primary key,
    data_source_id bigint not null references data_source (id),
    external_id    text not null,
    identifier     text not null,
    name           text not null,
    url            text not null,
    constraint project_external_uq unique (data_source_id, external_id)
);
