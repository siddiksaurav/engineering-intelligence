package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.judgment.JudgedBy;
import com.dsinnovators.ei.judgment.Judgment;
import com.dsinnovators.ei.judgment.JudgmentKind;
import com.dsinnovators.ei.judgment.JudgmentStore;
import com.dsinnovators.ei.judgment.JudgmentSubject;
import com.dsinnovators.ei.judgment.NewJudgment;
import com.dsinnovators.ei.judgment.PullRequestDigest;
import com.dsinnovators.ei.judgment.PullRequestDigests.CurrentDigest;
import com.dsinnovators.ei.setting.SettingService;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes the {@code item_tier} judgment of the items that have a tier by their type (decision 154):
 * Test change, Refactor and performance, Security and Infra and release. The tier is read from the
 * item's digest, with no new call to a model: a Test change takes the tier of its test kind from the
 * setting {@code evidence.test_kind_tiers} (decision 147), the others the tier the digest gave to its
 * work type. Reason, facts, model and prompt version are the digest's; the judgment names it.
 */
@Component
class ItemTiers {

    private static final String TEST_KIND_TIERS_SETTING = "evidence.test_kind_tiers";
    private static final Set<EvidenceType> TIERED_TYPES = Set.of(
            EvidenceType.TEST_CHANGE,
            EvidenceType.REFACTOR_PERFORMANCE,
            EvidenceType.SECURITY,
            EvidenceType.INFRA_RELEASE);

    /** What the digest says about the tier of one item. */
    private record Tier(String tier, String reason, JsonNode value) {}

    private final EvidenceItemRepository items;
    private final JudgmentStore judgments;
    private final SettingService settings;

    ItemTiers(EvidenceItemRepository items, JudgmentStore judgments, SettingService settings) {
        this.items = items;
        this.judgments = judgments;
        this.settings = settings;
    }

    /** True for the evidence types whose items get a tier judgment. */
    static boolean givesTier(EvidenceType type) {
        return TIERED_TYPES.contains(type);
    }

    /**
     * Writes the tier judgment on the current row of the item, unless that row has one. It is safe
     * to call for an item that did not change: nothing is written then.
     *
     * @param quarter the quarter whose settings apply
     * @return true when a judgment was written
     * @throws IllegalArgumentException when the type of the item has no tier
     * @throws IllegalStateException when the item has no current row or the digest gives no tier
     */
    boolean write(BuiltItem item, Quarter quarter) {
        if (!givesTier(item.type())) {
            throw new IllegalArgumentException(
                    "An item of the type " + item.type().databaseName() + " has no tier: " + item.key());
        }
        long rowId = items.findCurrentByKey(item.key())
                .orElseThrow(() -> new IllegalStateException("No current evidence item for key " + item.key()))
                .getId();
        JudgmentSubject subject = JudgmentSubject.evidenceItem(rowId);
        if (judgments.findCurrent(JudgmentKind.ITEM_TIER, subject).isPresent()) {
            return false;
        }
        CurrentDigest current = item.digests().getFirst();
        Tier tier = tierOf(item, current.digest(), quarter);
        Judgment digest = current.judgment();
        judgments.write(new NewJudgment(
                JudgmentKind.ITEM_TIER,
                subject,
                tier.value(),
                tier.tier(),
                tier.reason(),
                digest.getFacts(),
                null,
                JudgedBy.MODEL,
                digest.getModel(),
                digest.getPromptVersion(),
                inputHash(digest.getId(), rowId, item),
                digest.getId(),
                null));
        return true;
    }

    private Tier tierOf(BuiltItem item, PullRequestDigest digest, Quarter quarter) {
        if (item.type() == EvidenceType.TEST_CHANGE) {
            return testChangeTier(digest, quarter);
        }
        String tier = digest.workTypeTier()
                .tier()
                .orElseThrow(() -> new IllegalStateException("The digest of " + item.key() + " gives no tier for the "
                        + digest.workType().wireName() + " work type"));
        return new Tier(tier, digest.workTypeTier().reason(), workTypeFacts(item.type(), digest));
    }

    private static JsonNode workTypeFacts(EvidenceType type, PullRequestDigest digest) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        switch (type) {
            case REFACTOR_PERFORMANCE -> value.put("workType", digest.workType().wireName());
            case SECURITY -> value.put("kind", digest.securityKind().wireName());
            default ->
                value.put("kind", digest.infraKind().wireName())
                        .put("scope", digest.infraScope().wireName());
        }
        return value;
    }

    private Tier testChangeTier(PullRequestDigest digest, Quarter quarter) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("testKind", digest.testKind().wireName());
        String testKind = digest.testKind().wireName();
        JsonNode tier = settings.getJson(TEST_KIND_TIERS_SETTING, quarter).path(testKind);
        if (!tier.isString()) {
            throw new IllegalStateException(
                    "The setting " + TEST_KIND_TIERS_SETTING + " has no tier for the test kind " + testKind);
        }
        return new Tier(
                tier.asString(),
                testKindReason(digest.testKind(), tier.asString()) + " (" + TEST_KIND_TIERS_SETTING + "): "
                        + digest.reason(),
                value);
    }

    private static String testKindReason(PullRequestDigest.TestKind testKind, String tier) {
        return testKind == PullRequestDigest.TestKind.NONE
                ? "The model saw no test kind in this pull request, so the Test change gives " + tier
                : "The test kind " + testKind.wireName() + " gives " + tier;
    }

    private static String inputHash(long digestId, long itemRowId, BuiltItem item) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("kind", JudgmentKind.ITEM_TIER.databaseName());
        input.put("digestJudgmentId", digestId);
        input.put("evidenceItemId", itemRowId);
        input.put("contentHash", item.contentHash());
        return CanonicalJson.sha256Hex(input);
    }
}
