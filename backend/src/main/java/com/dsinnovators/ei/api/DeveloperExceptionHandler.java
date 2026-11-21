package com.dsinnovators.ei.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the developer endpoints into problem details that say what was wrong. */
@RestControllerAdvice(assignableTypes = DeveloperController.class)
class DeveloperExceptionHandler {

    @ExceptionHandler(DeveloperNotFoundException.class)
    ProblemDetail unknownDeveloper(DeveloperNotFoundException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }
}
