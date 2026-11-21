package com.dsinnovators.ei.judgment;

/**
 * One fact a judgment rests on, with the link where it can be checked. The system makes the link,
 * never the model (decision 136).
 */
public record JudgmentFact(String fact, String link) {

    public JudgmentFact {
        if (fact == null || fact.isBlank()) {
            throw new IllegalArgumentException("A judgment fact needs text");
        }
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("The judgment fact '" + fact + "' needs a link");
        }
    }
}
