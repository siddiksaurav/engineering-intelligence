-- The switch of spec 7.3 and decision 315: a gate that is calculated but not scored is shown on the profile and
-- never changes a dimension score. G2 starts switched off; setting it to true in a later row scores it.
insert into setting (key, valid_from_quarter, value, description) values (
    'scoring.gate_scored',
    '2026-Q2',
    '{"G1": true, "G2": false}',
    'Per gate with limits, whether its result may lower a dimension score (spec 7.3, decision 315). A gate that is not scored is shown and noted "not scored yet". Read by F11.'
);
