package com.dsinnovators.ei.api;

import com.dsinnovators.ei.analytics.DeveloperAnalytics;
import com.dsinnovators.ei.analytics.Distribution;
import com.dsinnovators.ei.analytics.RepositoryHygiene;
import com.dsinnovators.ei.analytics.ReviewFindingsReceived;
import com.dsinnovators.ei.analytics.ReviewParticipation;
import com.dsinnovators.ei.analytics.TestingContribution;
import com.dsinnovators.ei.analytics.WorkComplexity;
import com.dsinnovators.ei.analytics.WorkLevel;
import com.dsinnovators.ei.common.Quarter;

record DeveloperAnalyticsResponse(
        PersonResponse person,
        String quarter,
        Distribution workClassification,
        WorkComplexity.Result workComplexity,
        WorkLevel.Result workLevel,
        ReviewParticipation.Result reviewParticipation,
        ReviewFindingsReceived.Result reviewFindingsReceived,
        TestingContribution.Result testingContribution,
        RepositoryHygiene.Result hygiene) {

    static DeveloperAnalyticsResponse of(PersonResponse person, Quarter quarter, DeveloperAnalytics.Result analytics) {
        return new DeveloperAnalyticsResponse(
                person,
                quarter.toString(),
                analytics.workClassification(),
                analytics.workComplexity(),
                analytics.workLevel(),
                analytics.reviewParticipation(),
                analytics.reviewFindingsReceived(),
                analytics.testingContribution(),
                analytics.hygiene());
    }
}
