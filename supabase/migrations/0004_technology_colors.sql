-- Technologies default to a single flat gray (#64748b, the column default)
-- unless an admin manually recolors them. In practice almost none get
-- recolored, so the "By technology" distribution chart — a signature
-- dataviz surface — renders as a solid gray bar instead of using the app's
-- chart palette (see DESIGN.md's No-Gray-Only Rule). This backfills every
-- still-default-gray row with a color from that same palette (lib/palette.ts
-- mirrors this list so future inserts stay in sync), round-robining by name
-- order rather than hashing so the result is evenly spread instead of
-- clustering by chance onto one or two hues.
with palette(color) as (
  values ('#0085cb'), ('#00b1aa'), ('#efa810'), ('#ec3d5f'),
         ('#5b748e'), ('#8451c9'), ('#89c240')
),
ranked as (
  select id, (row_number() over (order by name) - 1) % 7 as idx
  from technologies
  where color = '#64748b'
),
indexed as (
  select color, row_number() over () - 1 as idx from palette
)
update technologies t
set color = indexed.color
from ranked
join indexed on indexed.idx = ranked.idx
where t.id = ranked.id;
