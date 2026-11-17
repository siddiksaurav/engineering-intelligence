-- raw_record is never updated and never deleted. The database enforces it, so the rule does not
-- rest on the Java code alone. TRUNCATE is not affected (the tests use it to start from empty tables).

create function raw_record_is_never_changed() returns trigger
    language plpgsql
as
$$
begin
    raise exception 'raw_record is never updated or deleted';
end
$$;

create trigger raw_record_no_update_or_delete
    before update or delete
    on raw_record
    for each row
execute function raw_record_is_never_changed();
