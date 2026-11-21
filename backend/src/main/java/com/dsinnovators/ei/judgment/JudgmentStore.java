package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.source.github.GitHubRecordKind;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way to write a judgment. A new judgment supersedes the one it names, else the current
 * judgment of its subject and kind; the old row stays. A model never judges the same input twice (decision 127): the store refuses it,
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
        Long supersedesId = judgment.supersedesId() != null
                ? judgment.supersedesId()
                : findCurrent(judgment.kind(), judgment.subject())
                        .map(Judgment::getId)
                        .orElse(null);
        return judgments.save(new Judgment(judgment, supersedesId, clock.instant()));
    }

    /**
     * Writes every judgment in one transaction, as {@link #write} does for one: all are stored or none.
     *
     * @throws IllegalStateException when a model already judged the input hash of one of them
     */
    @Transactional
    public List<Judgment> writeAll(List<NewJudgment> newJudgments) {
        return newJudgments.stream().map(this::write).toList();
    }

    /** True when a model judged this input for this kind: the input is not judged again. */
    @Transactional(readOnly = true)
    public boolean hasModelJudgment(JudgmentKind kind, String inputHash) {
        return judgments.existsByKindAndInputHashAndJudgedBy(kind, inputHash, JudgedBy.MODEL);
    }

    /**
     * The judgment of the pull request with this external id, such as {@code opf/openproject#24204},
     * that no other judgment supersedes, whichever record version it was made on.
     */
    @Transactional(readOnly = true)
    public Optional<Judgment> findCurrentOfPullRequest(JudgmentKind kind, String pullRequestExternalId) {
        return judgments.findCurrentOfPullRequest(
                kind, GitHubRecordKind.PULL_REQUEST.recordKind(), pullRequestExternalId);
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
