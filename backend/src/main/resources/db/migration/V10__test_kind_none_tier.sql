-- A Test change whose test kind the model could not name counts as T1 (decision 147). The tier is a
-- setting like the other test kinds; V9 had left it in code.
update setting
set value = jsonb_set(value, '{none}', '"T1"')
where key = 'evidence.test_kind_tiers' and valid_from_quarter = '2026-Q2';
