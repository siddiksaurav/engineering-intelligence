package com.dsinnovators.ei.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the job endpoints into problem details that say what was wrong. */
@RestControllerAdvice(assignableTypes = JobController.class)
class JobExceptionHandler {

    @ExceptionHandler(UnknownJobException.class)
    ProblemDetail unknownJob(UnknownJobException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }

    @ExceptionHandler(JobRunNotFoundException.class)
    ProblemDetail unknownRun(JobRunNotFoundException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, refused.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArguments(IllegalArgumentException refused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    ProblemDetail alreadyRunning(JobAlreadyRunningException refused) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, refused.getMessage());
        problem.setProperty("runningRunId", refused.runningRunId());
        return problem;
    }
}
