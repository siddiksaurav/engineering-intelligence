package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.InvalidProblemMarksException;
import com.dsinnovators.ei.review.ItemNotInSampleException;
import com.dsinnovators.ei.review.ReasonMarkNotAllowedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the reason mark endpoint into problem details that say what was wrong. */
@RestControllerAdvice(assignableTypes = ReasonMarkController.class)
class ReasonMarkExceptionHandler {

    @ExceptionHandler(ItemNotInSampleException.class)
    ProblemDetail outsideTheSample(ItemNotInSampleException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }

    @ExceptionHandler(ReasonMarkNotAllowedException.class)
    ProblemDetail notAllowed(ReasonMarkNotAllowedException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, refused.getMessage());
    }

    @ExceptionHandler({InvalidProblemMarksException.class, InvalidReasonMarkRequestException.class})
    ProblemDetail wrongMark(RuntimeException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
    }
}
