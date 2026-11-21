package com.dsinnovators.ei.judgment;

/**
 * What a judgment is about: the exact version of a pull request record, or the exact row of an
 * evidence item. Exactly one id is set.
 */
public record JudgmentSubject(Type type, long id) {

    /** The two kinds of subject, one column each in {@code judgment}. */
    enum Type {
        PULL_REQUEST_RECORD,
        EVIDENCE_ITEM
    }

    public static JudgmentSubject pullRequestRecord(long rawRecordId) {
        return new JudgmentSubject(Type.PULL_REQUEST_RECORD, rawRecordId);
    }

    public static JudgmentSubject evidenceItem(long evidenceItemId) {
        return new JudgmentSubject(Type.EVIDENCE_ITEM, evidenceItemId);
    }
}
