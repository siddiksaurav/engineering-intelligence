package com.dsinnovators.ei.api;

import com.dsinnovators.ei.judgment.ExchangeRequestAlreadyAnsweredException;
import com.dsinnovators.ei.judgment.ExchangeRequestNotFoundException;
import com.dsinnovators.ei.judgment.InvalidExchangeAnswerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the model exchange endpoints into problem details that say what was wrong. */
@RestControllerAdvice(assignableTypes = ModelExchangeController.class)
class ModelExchangeExceptionHandler {

    @ExceptionHandler({InvalidExchangeLimitException.class, InvalidExchangeAnswerException.class})
    ProblemDetail wrongInput(RuntimeException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
    }

    @ExceptionHandler(ExchangeRequestNotFoundException.class)
    ProblemDetail unknownRequest(ExchangeRequestNotFoundException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }

    @ExceptionHandler(ExchangeRequestAlreadyAnsweredException.class)
    ProblemDetail answeredAlready(ExchangeRequestAlreadyAnsweredException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, refused.getMessage());
    }
}
