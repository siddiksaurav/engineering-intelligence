package com.dsinnovators.ei.scoring;

/** Why a gate is unverified. */
public enum UnverifiedReason {
    /** The group has fewer developers than the setting asks for (decision 224). */
    TOO_FEW_DEVELOPERS,
    /** The person has no pull request of the gate's kind (decision 209). */
    NO_PULL_REQUEST_OF_THE_KIND,
    /** The repository has no pull request of the gate's kind. */
    NO_REPOSITORY_PULL_REQUESTS,
    /** The gate is on the dimension but never calculated in the PoC (decision 210). */
    NOT_CALCULATED
}
