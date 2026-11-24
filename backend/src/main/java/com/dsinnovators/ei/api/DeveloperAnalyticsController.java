package com.dsinnovators.ei.api;

import com.dsinnovators.ei.analytics.DeveloperAnalytics;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shows the five analytics and the hygiene number of a developer for one quarter (decisions 187, 200). */
@RestController
@RequestMapping("/api/developers")
class DeveloperAnalyticsController {

    private final PersonDirectory persons;
    private final DeveloperAnalytics analytics;

    DeveloperAnalyticsController(PersonDirectory persons, DeveloperAnalytics analytics) {
        this.persons = persons;
        this.analytics = analytics;
    }

    @GetMapping("/{personId}/analytics")
    DeveloperAnalyticsResponse analytics(
            @PathVariable long personId, @RequestParam(name = "quarter", required = false) String quarterText) {
        PersonSummary person = persons.find(personId).orElseThrow(() -> new DeveloperNotFoundException(personId));
        Quarter quarter = QuarterParameter.parse(quarterText);
        return DeveloperAnalyticsResponse.of(
                new PersonResponse(person.personId(), person.displayName()), quarter, analytics.of(personId, quarter));
    }
}
