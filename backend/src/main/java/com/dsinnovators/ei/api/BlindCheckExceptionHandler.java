package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.InsufficientItemsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the blind check endpoints into problem details that say what was wrong. */
@RestControllerAdvice(assignableTypes = BlindCheckController.class)
class BlindCheckExceptionHandler {

    @ExceptionHandler(InsufficientItemsException.class)
    ProblemDetail tooFewItems(InsufficientItemsException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, refused.getMessage());
    }
}
