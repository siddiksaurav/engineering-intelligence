package com.dsinnovators.ei.judgment;

import java.util.List;

/**
 * The table of spec 6.2, Move 3: the complexity that the depths of the problems and three flags give.
 * It is information only (decision 323): the model decides the complexity, and the system stores this
 * value next to it as {@code complexityByTable}.
 */
final class ComplexityTable {

    private ComplexityTable() {}

    /**
     * @param problemsAffectEachOther the model's one answer for the whole item
     * @param mistakesStayHidden true for a design whose mistakes stay hidden across the system
     * @param reachesAcrossProjects true when the problems reach across projects or the organization
     */
    static FeatureWorkAnswer.Complexity complexityOf(
            List<FeatureWorkAnswer.Depth> depths,
            boolean problemsAffectEachOther,
            boolean mistakesStayHidden,
            boolean reachesAcrossProjects) {
        if (reachesAcrossProjects) {
            return FeatureWorkAnswer.Complexity.VERY_HIGH;
        }
        boolean noReadyAnswer = depths.contains(FeatureWorkAnswer.Depth.NO_READY_ANSWER);
        if (noReadyAnswer || mistakesStayHidden || (problemsAffectEachOther && hasSeveralWithAnAnswer(depths))) {
            return FeatureWorkAnswer.Complexity.HIGH;
        }
        if (depths.contains(FeatureWorkAnswer.Depth.ANSWER_EXISTS)) {
            return FeatureWorkAnswer.Complexity.MEDIUM;
        }
        return FeatureWorkAnswer.Complexity.LOW;
    }

    /** "Several" is more than one (decision 313). */
    private static boolean hasSeveralWithAnAnswer(List<FeatureWorkAnswer.Depth> depths) {
        return depths.stream()
                .filter(depth -> depth == FeatureWorkAnswer.Depth.ANSWER_EXISTS)
                .skip(1)
                .findAny()
                .isPresent();
    }
}
