-- PoC only: the requests of the model jobs that Claude Code answers instead of an HTTP provider, when
-- ei.judgment.mode is exchange. Not part of the evaluation: the judgments are still stored by the
-- jobs in judgment. A refused answer is dropped and its request waits again with the reason.
create table model_exchange (
    request_hash   text primary key,
    prompt_version text        not null,
    schema_name    text        not null,
    instructions   text        not null,
    input          text        not null,
    json_schema    jsonb       not null,
    status         text        not null check (status in ('waiting', 'answered')),
    answer         jsonb,
    answered_by    text,
    refusal_reason text,
    asked_at       timestamptz not null,
    answered_at    timestamptz,
    constraint model_exchange_answered_ck
        check ((status = 'answered') = (answer is not null and answered_by is not null and answered_at is not null))
);

create index model_exchange_waiting_idx on model_exchange (prompt_version, asked_at) where status = 'waiting';
