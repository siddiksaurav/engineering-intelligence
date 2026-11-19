package com.dsinnovators.ei.source.openproject;

/** OpenProject answered {@code 404}: what was asked for does not exist, or this reader may not see it. */
public class OpenProjectNotFoundException extends OpenProjectApiException {

    private static final long serialVersionUID = 1L;

    OpenProjectNotFoundException(String message) {
        super(message);
    }
}
