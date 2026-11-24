package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
import com.dsinnovators.ei.scoring.DimensionScoreReader;
import com.dsinnovators.ei.scoring.Level;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shows the two dimension scores of a developer for one quarter at the selected level (decisions 217, 229). */
@RestController
@RequestMapping("/api/developers")
class DeveloperDimensionScoresController {

    private final PersonDirectory persons;
    private final DimensionScoreReader scores;

    DeveloperDimensionScoresController(PersonDirectory persons, DimensionScoreReader scores) {
        this.persons = persons;
        this.scores = scores;
    }

    @GetMapping("/{personId}/dimension-scores")
    DimensionScoresResponse dimensionScores(
            @PathVariable long personId,
            @RequestParam(name = "quarter", required = false) String quarterText,
            @RequestParam(name = "level", required = false) String levelText) {
        PersonSummary person = persons.find(personId).orElseThrow(() -> new DeveloperNotFoundException(personId));
        Quarter quarter = QuarterParameter.parse(quarterText);
        Level level = parseLevel(levelText);
        return DimensionScoresResponse.of(
                new PersonResponse(person.personId(), person.displayName()),
                quarter,
                level,
                scores.of(personId, quarter, level));
    }

    private static Level parseLevel(String levelText) {
        if (levelText == null) {
            throw new InvalidLevelException("The parameter level is required: L1, L2 or L3");
        }
        try {
            return Level.valueOf(levelText);
        } catch (IllegalArgumentException unknown) {
            throw new InvalidLevelException("The parameter level '" + levelText + "' is not L1, L2 or L3");
        }
    }
}
