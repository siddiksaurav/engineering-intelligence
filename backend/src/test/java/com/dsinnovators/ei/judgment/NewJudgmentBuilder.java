package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Builds a valid model judgment of a pull request digest; a test changes the one part it is about. */
final class NewJudgmentBuilder {

    static final JudgmentFact FACT = new JudgmentFact("Adds a spec", "https://github.com/opf/openproject/pull/1/files");

    private JudgmentKind kind = JudgmentKind.PULL_REQUEST_DIGEST;
    private JudgmentSubject subject;
    private JsonNode value = CanonicalJson.parse("{\"workType\":\"fix\"}");
    private String tier;
    private String reason = "Fixes a defect";
    private List<JudgmentFact> facts = List.of(FACT);
    private String model = "openai/gpt-oss-120b";
    private String promptVersion = "pull-request-digest/v1";
    private String inputHash = "hash";
    private Long basedOnJudgmentId;
    private Long supersedesId;

    private NewJudgmentBuilder(JudgmentSubject subject) {
        this.subject = subject;
    }

    static NewJudgmentBuilder aDigestOf(JudgmentSubject pullRequestRecord) {
        return new NewJudgmentBuilder(pullRequestRecord);
    }

    static NewJudgmentBuilder anItemTierOf(JudgmentSubject evidenceItem) {
        return new NewJudgmentBuilder(evidenceItem).kind(JudgmentKind.ITEM_TIER).tier("T2");
    }

    NewJudgmentBuilder kind(JudgmentKind kind) {
        this.kind = kind;
        return this;
    }

    NewJudgmentBuilder tier(String tier) {
        this.tier = tier;
        return this;
    }

    NewJudgmentBuilder reason(String reason) {
        this.reason = reason;
        return this;
    }

    NewJudgmentBuilder facts(List<JudgmentFact> facts) {
        this.facts = facts;
        return this;
    }

    NewJudgmentBuilder model(String model) {
        this.model = model;
        return this;
    }

    NewJudgmentBuilder promptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
        return this;
    }

    NewJudgmentBuilder inputHash(String inputHash) {
        this.inputHash = inputHash;
        return this;
    }

    NewJudgmentBuilder basedOn(long judgmentId) {
        this.basedOnJudgmentId = judgmentId;
        return this;
    }

    NewJudgmentBuilder supersedes(long judgmentId) {
        this.supersedesId = judgmentId;
        return this;
    }

    NewJudgment build() {
        return new NewJudgment(
                kind,
                subject,
                value,
                tier,
                reason,
                facts,
                JudgedBy.MODEL,
                model,
                promptVersion,
                inputHash,
                basedOnJudgmentId,
                supersedesId);
    }
}
