package com.dsinnovators.ei.api;

import com.dsinnovators.ei.analytics.RepositoryHygiene;
import com.dsinnovators.ei.analytics.TeamAnalytics;
import com.dsinnovators.ei.analytics.WorkClassification;
import com.dsinnovators.ei.analytics.WorkComplexity;
import com.dsinnovators.ei.common.Quarter;

record TeamAnalyticsResponse(
        String quarter,
        WorkClassification.Result workClassification,
        WorkComplexity.TeamResult workComplexity,
        RepositoryHygiene.Result hygiene) {

    static TeamAnalyticsResponse of(Quarter quarter, TeamAnalytics.Result analytics) {
        return new TeamAnalyticsResponse(
                quarter.toString(), analytics.workClassification(), analytics.workComplexity(), analytics.hygiene());
    }
}
