package com.dsinnovators.ei.judgment;

/** Where the model jobs get their answers from. */
public enum JudgmentMode {
    /** The configured HTTP providers, in order (decision 146). The default. */
    PROVIDERS,
    /** The model exchange, answered by Claude Code; PoC only, for when every free provider is at its limit. */
    EXCHANGE
}
