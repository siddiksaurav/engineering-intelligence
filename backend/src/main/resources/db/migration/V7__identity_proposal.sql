-- The OpenProject user proposed for a person, with the evidence it rests on (F04 design). Derived
-- data that the identity-build job rewrites; a decision (confirmed, rejected) is kept.
create table identity_proposal (
    id                    bigint generated always as identity primary key,
    person_id             bigint not null references person (id),
    openproject_user_id   bigint not null,
    openproject_user_name text not null,
    supporting_items      int not null,
    status_change_items   int not null,
    person_items          int not null,
    evidence              jsonb not null,
    state                 text not null check (state in ('proposed', 'confirmed', 'rejected')),
    proposed_at           timestamptz not null default now(),
    decided_at            timestamptz,
    constraint identity_proposal_person_user_uq unique (person_id, openproject_user_id)
);

-- At most one current proposal per person: a rejected one no longer counts (decision 95).
create unique index identity_proposal_one_current_per_person_idx
    on identity_proposal (person_id) where state in ('proposed', 'confirmed');

-- Decision 98: the user confirms or rejects from psql.
create function confirm_identity_proposal(proposal_id bigint) returns void
language plpgsql as $$
declare
    current_state text;
begin
    select state into current_state from identity_proposal where id = proposal_id for update;
    if not found then
        raise exception 'Identity proposal % does not exist', proposal_id;
    end if;
    if current_state <> 'proposed' then
        raise exception 'Identity proposal % is %, only a proposed one can be confirmed', proposal_id, current_state;
    end if;
    update identity_proposal set state = 'confirmed', decided_at = now() where id = proposal_id;
end;
$$;

create function reject_identity_proposal(proposal_id bigint) returns void
language plpgsql as $$
declare
    current_state text;
    rejected_person_id bigint;
    rejected_user_id bigint;
begin
    select state, person_id, openproject_user_id
        into current_state, rejected_person_id, rejected_user_id
        from identity_proposal where id = proposal_id for update;
    if not found then
        raise exception 'Identity proposal % does not exist', proposal_id;
    end if;
    if current_state = 'rejected' then
        raise exception 'Identity proposal % is rejected already', proposal_id;
    end if;
    update identity_proposal set state = 'rejected', decided_at = now() where id = proposal_id;
    delete from person_identity
        where person_id = rejected_person_id
          and data_source_id = (select id from data_source where key = 'openproject')
          and external_id = rejected_user_id::text;
end;
$$;
