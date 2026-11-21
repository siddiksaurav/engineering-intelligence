-- F07 design, "Migration V11". Stage 2 judges the Feature work items and the Review comment written
-- items; both judgments are about an evidence item row.
alter table judgment drop constraint judgment_kind_check;
alter table judgment add constraint judgment_kind_check
    check (kind in ('pull_request_digest', 'item_tier', 'feature_work_complexity', 'review_comment'));

-- Very high counts as T3 until T4 is used (master plan section 2, decision 171).
insert into setting (key, valid_from_quarter, value, description) values (
    'evidence.complexity_tiers',
    '2026-Q2',
    '{"low": "T1", "medium": "T2", "high": "T3", "very_high": "T3"}',
    'The tier of a Feature work item by its complexity (spec 6.2). Read by F07.'
);
-- A comment type that is not substantive has no tier (decision 170). Architecture is T4 in spec 6.4,
-- counted as T3 until T4 is used.
insert into setting (key, valid_from_quarter, value, description) values (
    'evidence.review_comment_types',
    '2026-Q2',
    '{"question": {"substantive": true, "tier": "T1"},
      "small_catch": {"substantive": true, "tier": "T1"},
      "correctness": {"substantive": true, "tier": "T2"},
      "maintainability": {"substantive": true, "tier": "T2"},
      "design": {"substantive": true, "tier": "T3"},
      "security": {"substantive": true, "tier": "T3"},
      "performance": {"substantive": true, "tier": "T3"},
      "architecture": {"substantive": true, "tier": "T3"},
      "style": {"substantive": false, "tier": null},
      "acknowledgement": {"substantive": false, "tier": null}}',
    'Whether a Review comment written is substantive and its tier, by the comment type the model gave (spec 6.4, 7.2). Read by F07.'
);
