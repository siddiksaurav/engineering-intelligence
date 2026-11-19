-- Resolved links between stored pull requests and work items (F03 design). Derived data, not a
-- source copy: the rows of one pull request are replaced when the job runs for it again.
create table pull_request_work_item (
    id                       bigint generated always as identity primary key,
    pull_request_external_id text not null,
    work_package_id          bigint,
    reference                text not null,
    found_in                 text not null check (found_in in ('pull_request_body', 'pull_request_title', 'openproject')),
    state                    text not null check (state in ('resolved', 'not_visible')),
    linked_at                timestamptz not null default now(),
    constraint pull_request_work_item_state_work_package_ck
        check ((state = 'not_visible') = (work_package_id is null)),
    constraint pull_request_work_item_link_uq unique (pull_request_external_id, found_in, reference)
);

create index pull_request_work_item_work_package_idx on pull_request_work_item (work_package_id);

-- Decision 67: a Feature is done once QA has passed or later.
insert into setting (key, valid_from_quarter, value, description) values (
    'openproject.done_statuses',
    '2026-Q2',
    '["tested", "deployed", "resolved", "released", "closed"]',
    'OpenProject statuses that mean a work item is done. A Feature that changes into one of them within a quarter is done in that quarter. Read by F03 and F05.'
);
