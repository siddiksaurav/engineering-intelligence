package com.dsinnovators.ei.source.openproject;

/** OpenProject answered {@code 400}: it did not accept the request, for example a filter value it cannot read. */
public class OpenProjectBadRequestException extends OpenProjectApiException {

    private static final long serialVersionUID = 1L;

    OpenProjectBadRequestException(String message) {
        super(message);
    }
}
