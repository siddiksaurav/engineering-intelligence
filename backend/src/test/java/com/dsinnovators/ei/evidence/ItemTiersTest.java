package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.judgment.JudgedBy;
import com.dsinnovators.ei.judgment.Judgment;
import com.dsinnovators.ei.judgment.JudgmentKind;
import com.dsinnovators.ei.judgment.JudgmentStore;
import com.dsinnovators.ei.judgment.JudgmentSubject;
import com.dsinnovators.ei.source.RawRecordStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

class ItemTiersTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ItemTiers itemTiers;

    @Autowired
    private TestChangeBuilder testChangeBuilder;

    @Autowired
    private WorkTypeItemBuilder workTypeItemBuilder;

    @Autowired
    private LinkedPullRequests linkedPullRequestsReader;

    @Autowired
    private EvidenceItemStore store;

    @Autowired
    private EvidenceItemRepository items;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    @Autowired
    private JudgmentStore judgments;

    private StoredRecordsFixture records;
    private StoredDigestsFixture digests;

    @BeforeEach
    void storeTheDeveloper() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        digests = new StoredDigestsFixture(jdbc, judgments);
        records.person(1, "ada");
        records.project(5);
    }

    /** Stores the item of the pull request with the digest, as the job does, and returns what the builder made. */
    private BuiltItem storedTestChange(ObjectNode answer) {
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.diff(1, "spec/list_spec.rb");
        digests.digest(1, answer);
        return storeFirst(
                testChangeBuilder.build(linkedPullRequestsReader.read(QUARTER).pullRequests(), QUARTER));
    }

    private BuiltItem storedWorkTypeItem(ObjectNode answer) {
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.diff(1, "app/list.rb");
        digests.digest(1, answer);
        return storeFirst(
                workTypeItemBuilder.build(linkedPullRequestsReader.read(QUARTER).pullRequests(), QUARTER));
    }

    private BuiltItem storeFirst(BuildResult result) {
        BuiltItem item = result.items().getFirst();
        store.write(item);
        return item;
    }

    private Judgment currentTierOf(BuiltItem item) {
        long rowId = items.findCurrentByKey(item.key()).orElseThrow().getId();
        return judgments
                .findCurrent(JudgmentKind.ITEM_TIER, JudgmentSubject.evidenceItem(rowId))
                .orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({"unit,T1", "integration,T2", "regression,T2", "ci_gate,T3"})
    void aTestChangeTakesTheTierOfItsTestKindFromTheSetting(String testKind, String tier) {
        BuiltItem item = storedTestChange(StoredDigestsFixture.answer("fix", testKind));

        assertTrue(itemTiers.write(item, QUARTER));

        Judgment judgment = currentTierOf(item);
        assertEquals(tier, judgment.getTier());
        assertTrue(judgment.getReason().contains(testKind), judgment.getReason());
        assertEquals(JudgmentKind.ITEM_TIER, judgment.getKind());
        assertEquals(testKind, judgment.getValue().path("testKind").asString());
    }

    @Test
    void theTierJudgmentCopiesTheFactsTheModelAndThePromptVersionOfTheDigest() {
        BuiltItem item = storedTestChange(StoredDigestsFixture.answer("fix", "unit"));
        Judgment digest = item.digests().getFirst().judgment();

        itemTiers.write(item, QUARTER);

        Judgment judgment = currentTierOf(item);
        assertEquals(digest.getId(), judgment.getBasedOnJudgmentId());
        assertEquals(digest.getFacts(), judgment.getFacts());
        assertEquals(StoredDigestsFixture.MODEL, judgment.getModel());
        assertEquals(StoredDigestsFixture.PROMPT_VERSION, judgment.getPromptVersion());
        assertEquals(JudgedBy.MODEL, judgment.getJudgedBy());
        assertEquals(64, judgment.getInputHash().length());
    }

    @Test
    void aTestKindOfNoneGivesT1WithAReasonThatTheModelSawNoTestKind() {
        BuiltItem item = storedTestChange(StoredDigestsFixture.answer("fix", "none"));

        itemTiers.write(item, QUARTER);

        Judgment judgment = currentTierOf(item);
        assertEquals("T1", judgment.getTier());
        assertTrue(judgment.getReason().contains("no test kind"), judgment.getReason());
    }

    @Test
    void theTierOfATestKindOfNoneComesFromTheSettingToo() {
        jdbc.update(
                "update setting set value = jsonb_set(value, '{none}', '\"T2\"') where key = 'evidence.test_kind_tiers'");
        BuiltItem item = storedTestChange(StoredDigestsFixture.answer("fix", "none"));

        itemTiers.write(item, QUARTER);

        assertEquals("T2", currentTierOf(item).getTier());
    }

    @Test
    void aRefactorItemTakesTheTierAndTheReasonOfTheDigest() {
        BuiltItem item = storedWorkTypeItem(StoredDigestsFixture.withTier(
                StoredDigestsFixture.answer("refactor", "none"), "T3", "A planned debt item"));

        itemTiers.write(item, QUARTER);

        Judgment judgment = currentTierOf(item);
        assertEquals("T3", judgment.getTier());
        assertEquals("A planned debt item", judgment.getReason());
        assertEquals("refactor", judgment.getValue().path("workType").asString());
    }

    @Test
    void aSecondCallForTheSameItemRowWritesNoSecondJudgment() {
        BuiltItem item = storedTestChange(StoredDigestsFixture.answer("fix", "unit"));
        assertTrue(itemTiers.write(item, QUARTER));

        assertFalse(itemTiers.write(item, QUARTER));

        assertEquals(1, jdbc.queryForObject("select count(*) from judgment where kind = 'item_tier'", Integer.class));
    }

    @Test
    void anItemOfATypeWithNoTierIsRefused() {
        BuiltItem item = storedWorkTypeItem(StoredDigestsFixture.answer("docs", "none"));

        assertThrows(IllegalArgumentException.class, () -> itemTiers.write(item, QUARTER));
    }

    @Test
    void onlyTheTypesOfTheDesignGiveATier() {
        assertEquals(
                List.of(
                        EvidenceType.TEST_CHANGE,
                        EvidenceType.REFACTOR_PERFORMANCE,
                        EvidenceType.SECURITY,
                        EvidenceType.INFRA_RELEASE),
                java.util.Arrays.stream(EvidenceType.values())
                        .filter(ItemTiers::givesTier)
                        .toList());
    }
}
