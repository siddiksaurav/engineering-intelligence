package com.dsinnovators.ei.api;

import com.dsinnovators.ei.analytics.TeamAnalytics;
import com.dsinnovators.ei.common.Quarter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shows the analytics of the whole team for one quarter, for the overview (decision 300). */
@RestController
@RequestMapping("/api/team")
class TeamAnalyticsController {

    private final TeamAnalytics analytics;

    TeamAnalyticsController(TeamAnalytics analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/analytics")
    TeamAnalyticsResponse analytics(@RequestParam(name = "quarter", required = false) String quarterText) {
        Quarter quarter = QuarterParameter.parse(quarterText);
        return TeamAnalyticsResponse.of(quarter, analytics.of(quarter));
    }
}
