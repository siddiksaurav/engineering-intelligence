package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * What scoring did with the items of one tiered evidence type of a dimension.
 *
 * @param counted the judged items that enter the presence sums
 * @param notJudgedYet items left out because they have no judgment yet
 * @param notSubstantive review comments left out because they are not substantive
 * @param notReadAtLevel items left out because the type is not read at the selected level
 * @param presence presence per tier; empty when the type is not read at the level
 */
public record EvidenceTypeTrail(
        String evidenceType,
        PresenceRule rule,
        List<TrailItem> counted,
        List<TrailItem> notJudgedYet,
        List<TrailItem> notSubstantive,
        List<TrailItem> notReadAtLevel,
        List<TierPresence> presence) {}
