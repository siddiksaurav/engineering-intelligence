-- One row per run of a job (F02 design 6.1). Unlike the evidence tables, a row is updated while
-- its run goes on: status, counts and finish time. It is an operational log, not evidence.
create table job_run (
    id            bigint generated always as identity primary key,
    job_name      text not null,
    arguments     jsonb not null,
    started_by    text not null,
    status        text not null check (status in ('running', 'succeeded', 'failed', 'interrupted')),
    started_at    timestamptz not null,
    finished_at   timestamptz,
    counts        jsonb not null,
    error_message text,
    constraint job_run_finished_when_not_running check ((status = 'running') = (finished_at is null))
);

-- One running run per job. Two imports of one source at once would race on record versions.
create unique index job_run_one_running_per_job on job_run (job_name) where status = 'running';
