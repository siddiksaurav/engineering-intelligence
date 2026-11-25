package com.dsinnovators.ei.judgment;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one judgment row by its id, current or not, for a feature that fixed the judgment it measures. */
@Service
public class JudgmentsById {

    private final JudgmentRepository judgments;

    JudgmentsById(JudgmentRepository judgments) {
        this.judgments = judgments;
    }

    @Transactional(readOnly = true)
    public Optional<ItemJudgment> find(long judgmentId) {
        return judgments.findById(judgmentId).map(ItemJudgment::of);
    }
}
