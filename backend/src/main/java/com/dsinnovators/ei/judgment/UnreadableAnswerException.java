package com.dsinnovators.ei.judgment;

/**
 * A provider answered, but the answer cannot be read: it is not JSON, it was cut off at the token
 * limit or it holds no message. Like an answer that breaks the schema, it is a bad answer that is
 * asked for once more (decision 135). The message never holds a key.
 */
class UnreadableAnswerException extends ModelCallException {

    private static final long serialVersionUID = 1L;

    UnreadableAnswerException(String message) {
        super(message);
    }

    UnreadableAnswerException(String message, Throwable cause) {
        super(message, cause);
    }
}
