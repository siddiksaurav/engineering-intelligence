package com.dsinnovators.ei.review;

import org.springframework.jdbc.core.JdbcTemplate;

/** Stores Feature work item rows and their Stage 2 judgments as {@code evidence-build} and {@code feature-work-judgment} do. */
public final class BlindCheckFixture {

    private final JdbcTemplate jdbc;
    private final long person;
    private int itemsStored;

    public BlindCheckFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.person = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
    }

    /** A current Feature work item judged with the complexity, as the model answered it; returns the item row id. */
    public long judgedItem(String complexity) {
        int feature = itemsStored + 1;
        long itemId = item("""
                {"feature":{"number":%1$d,"title":"Feature %1$d","type":"Feature","url":"https://op.test/wp/%1$d"},
                 "tasks":[],
                 "pullRequests":[{"externalId":"%1$d","number":%2$d,"title":"Pull request %2$d","url":"https://gh.test/pull/%2$d","mergedAt":"2026-07-01T10:00:00Z"}],
                 "doneAt":"2026-07-03T10:00:00Z"}
                """.formatted(feature, 100 + feature));
        judgment(itemId, complexity, null);
        return itemId;
    }

    /** A current Feature work item row without a work item and without a judgment; returns the item row id. */
    public long item(String fields) {
        itemsStored++;
        return jdbc.queryForObject(
                """
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url, fields,
                                           content_hash, withdrawn, built_at)
                values (?, 'feature_work', ?, '2026-07-03', 'https://source.test/' || ?, ?::jsonb, 'hash', false, now())
                returning id
                """, Long.class, "feature_work:" + itemsStored + ":" + person, person, itemsStored, fields);
    }

    /** A judgment of the item; a second one for the same item supersedes the given one. Returns the judgment row id. */
    public long judgment(long itemId, String complexity, Long supersedes) {
        return jdbc.queryForObject(
                """
                insert into judgment (kind, evidence_item_id, value, tier, reason, facts, judged_by, model,
                                      prompt_version, input_hash, supersedes_id, judged_at)
                values ('feature_work_complexity', ?, ?::jsonb, 'T1', ?,
                        '[{"fact":"Adds a cache","link":"https://gh.test/pull/101"}]'::jsonb,
                        'model', 'gpt-test', 'feature-work-complexity/v2', 'hash' || gen_random_uuid(), ?,
                        '2026-11-01T10:00:00Z')
                returning id
                """,
                Long.class,
                itemId,
                "{\"complexity\":\"" + complexity + "\",\"complexityByTable\":\"" + complexity + "\"}",
                "Reason for " + complexity + " item " + itemId,
                supersedes);
    }

    public long person() {
        return person;
    }
}
