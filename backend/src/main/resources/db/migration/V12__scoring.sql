-- F11 design, "Migration V12". The two dimensions the PoC scores, the evidence types and gates of each
-- (versioned by quarter like setting, decision 202), the stored scores and the scoring settings.
create table dimension (
    id   bigint generated always as identity primary key,
    key  text not null unique,
    name text not null
);

-- The mapping of a dimension in force for a quarter is its rows with the latest valid_from_quarter
-- that is not after the quarter. A type that is not tiered is counted on the trail only.
create table dimension_evidence_type (
    id                 bigint generated always as identity primary key,
    dimension_id       bigint  not null references dimension (id),
    evidence_type      text    not null,
    valid_from_quarter text    not null check (valid_from_quarter ~ '^[0-9]{4}-Q[1-4]$'),
    tiered             boolean not null,
    from_level         text    not null check (from_level in ('L1', 'L2', 'L3')),
    constraint dimension_evidence_type_uq unique (dimension_id, evidence_type, valid_from_quarter),
    constraint dimension_evidence_type_type_ck
        check (evidence_type in ('feature_work', 'bug_fix', 'review_comment_written', 'test_change',
                                 'refactor_performance', 'security', 'infra_release', 'documentation'))
);

-- A gate that is not applied is shown on the trail as "gate unverified".
create table dimension_gate (
    id                 bigint generated always as identity primary key,
    dimension_id       bigint  not null references dimension (id),
    gate               text    not null check (gate in ('G1', 'G2', 'G3', 'G4', 'G5')),
    valid_from_quarter text    not null check (valid_from_quarter ~ '^[0-9]{4}-Q[1-4]$'),
    applied            boolean not null,
    constraint dimension_gate_uq unique (dimension_id, gate, valid_from_quarter)
);

-- Rows are never updated: a new run inserts new rows (decision 213).
create table dimension_score (
    id             bigint generated always as identity primary key,
    job_run_id     bigint      not null references job_run (id),
    person_id      bigint      not null references person (id),
    dimension_id   bigint      not null references dimension (id),
    quarter        text        not null check (quarter ~ '^[0-9]{4}-Q[1-4]$'),
    selected_level text        not null check (selected_level in ('L1', 'L2', 'L3')),
    working_tier   text check (working_tier in ('T1', 'T2', 'T3')),
    score          int         not null check (score in (0, 1, 2)),
    gates          jsonb       not null,
    trail          jsonb       not null,
    calculated_at  timestamptz not null,
    constraint dimension_score_uq unique (job_run_id, person_id, dimension_id, selected_level)
);
create index dimension_score_person_idx on dimension_score (person_id, quarter);

insert into dimension (key, name) values
    ('coding_and_code_quality', 'Coding and code quality'),
    ('engineering_standards_and_system_health', 'Engineering standards and system health');

-- Spec 8.3. Review comments are read from L2 (the L1 statement does not ask for code reviews).
insert into dimension_evidence_type (dimension_id, evidence_type, valid_from_quarter, tiered, from_level)
select dimension.id, mapping.evidence_type, '2026-Q2', mapping.tiered, mapping.from_level
from dimension
         join (values ('coding_and_code_quality', 'feature_work', true, 'L1'),
                      ('coding_and_code_quality', 'review_comment_written', true, 'L2'),
                      ('engineering_standards_and_system_health', 'test_change', true, 'L1'),
                      ('engineering_standards_and_system_health', 'bug_fix', false, 'L1'),
                      ('engineering_standards_and_system_health', 'refactor_performance', true, 'L1'),
                      ('engineering_standards_and_system_health', 'security', true, 'L1'),
                      ('engineering_standards_and_system_health', 'infra_release', true, 'L1'))
    as mapping (dimension_key, evidence_type, tiered, from_level) on mapping.dimension_key = dimension.key;

-- G4 is never calculated in the PoC (decision 210).
insert into dimension_gate (dimension_id, gate, valid_from_quarter, applied)
select dimension.id, gates.gate, '2026-Q2', gates.applied
from dimension
         cross join (values ('G1', true), ('G2', true), ('G4', false)) as gates (gate, applied)
where dimension.key = 'engineering_standards_and_system_health';

insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.presence_threshold',
    '2026-Q2',
    '{"numerator": 1, "denominator": 3}',
    'The share of a frequent evidence type that must sit at a tier or higher for presence at that tier, as an exact fraction (spec 9.3, 14). Read by F11.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.feature_work_weights',
    '2026-Q2',
    '{"T1": 1, "T2": 2, "T3": 4}',
    'The weight of a Feature work item by its tier (spec 9.3, 14). Read by F11.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.frequent_evidence_types',
    '2026-Q2',
    '["feature_work", "review_comment_written", "test_change"]',
    'The evidence types with a presence share; every other type has presence with one item (spec 9.3). Read by F11.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.t3_items_needed_at_l3',
    '2026-Q2',
    '{"engineering_standards_and_system_health": 2}',
    'The number of T3 items a dimension needs for a T3 working tier at level L3, by dimension key (spec 8.3, 14). Read by F11.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.gate_limits',
    '2026-Q2',
    '{"G1": {"passWithinPoints": 10, "farMissBeyondPoints": 25}, "G2": {"passWithinPoints": 10, "farMissBeyondPoints": 25}}',
    'Per gate, the points a person may be below the repository share and still pass, and the points beyond which it is a far miss (spec 7.3, 14). Read by F11.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.gate_minimum_developers',
    '2026-Q2',
    '3',
    'The fewest developers a gate group needs; with fewer, the gate is unverified (spec 7.3). Read by F11.'
);
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.gate_minimum_pull_requests_per_developer',
    '2026-Q2',
    '1',
    'The fewest pull requests of a gate''s kind a developer needs to count in the gate group (decision 224). Read by F11.'
);
