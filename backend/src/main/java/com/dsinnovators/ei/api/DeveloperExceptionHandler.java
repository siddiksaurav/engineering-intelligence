package com.dsinnovators.ei.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the developer and evidence item endpoints into problem details that say what was wrong. */
@RestControllerAdvice(
        assignableTypes = {
            DeveloperController.class,
            DeveloperAnalyticsController.class,
            DeveloperDimensionScoresController.class,
            EvidenceItemController.class
        })
class DeveloperExceptionHandler {

    @ExceptionHandler(DeveloperNotFoundException.class)
    ProblemDetail unknownDeveloper(DeveloperNotFoundException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }

    @ExceptionHandler(InvalidQuarterException.class)
    ProblemDetail wrongQuarter(InvalidQuarterException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
    }

    @ExceptionHandler(InvalidLevelException.class)
    ProblemDetail wrongLevel(InvalidLevelException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
    }

    @ExceptionHandler(InvalidEvidenceItemIdsException.class)
    ProblemDetail wrongEvidenceItemIds(InvalidEvidenceItemIdsException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
    }

    @ExceptionHandler(EvidenceItemNotFoundException.class)
    ProblemDetail unknownEvidenceItem(EvidenceItemNotFoundException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }
}
