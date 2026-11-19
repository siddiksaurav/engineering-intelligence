-- jsonb cannot store the character U+0000. The store writes it as the six characters \u0000 and
-- marks the row, so the change is visible and the original can be restored (F02 design 5.4).
-- Adding a column updates no row, so the trigger of V2 is not involved.

alter table raw_record
    add column nul_escaped boolean not null default false;
