package com.dsinnovators.ei.judgment;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way to write a judgment. A new judgment of a subject and kind supersedes the current one;
 * the old row stays. A model never judges the same input twice (decision 127): the store refuses it,
 * and so does a unique index. One transaction per call.
 */
@Service
public class JudgmentStore {

    private final JudgmentRepository judgments;
    private final Clock clock;

    JudgmentStore(JudgmentRepository judgments, Clock clock) {
        this.judgments = judgments;
        this.clock = clock;
    }

    /**
     * Writes the judgment and makes it supersede the current judgment of its subject and kind.
     *
     * @throws IllegalStateException when a model already judged this input hash for this kind
     */
    @Transactional
    public Judgment write(NewJudgment judgment) {
        if (judgment.judgedBy() == JudgedBy.MODEL && hasModelJudgment(judgment.kind(), judgment.inputHash())) {
            throw new IllegalStateException(
                    "A model already judged the input " + judgment.inputHash() + " for " + judgment.kind());
        }
        Long supersedesId = findCurrent(judgment.kind(), judgment.subject())
                .map(Judgment::getId)
                .orElse(null);
        return judgments.save(new Judgment(judgment, supersedesId, clock.instant()));
    }

    /** True when a model judged this input for this kind: the input is not judged again. */
    @Transactional(readOnly = true)
    public boolean hasModelJudgment(JudgmentKind kind, String inputHash) {
        return judgments.existsByKindAndInputHashAndJudgedBy(kind, inputHash, JudgedBy.MODEL);
    }

    /** The judgment of the subject and kind that no other judgment supersedes. */
    @Transactional(readOnly = true)
    public Optional<Judgment> findCurrent(JudgmentKind kind, JudgmentSubject subject) {
        return switch (subject.type()) {
            case PULL_REQUEST_RECORD -> judgments.findCurrentOfPullRequestRecord(kind, subject.id());
            case EVIDENCE_ITEM -> judgments.findCurrentOfEvidenceItem(kind, subject.id());
        };
    }
}
