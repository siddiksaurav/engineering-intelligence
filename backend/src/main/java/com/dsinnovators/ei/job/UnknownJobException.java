package com.dsinnovators.ei.job;

import java.util.List;

/** A job was asked for by a name that no job has. */
public class UnknownJobException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnknownJobException(String name, List<String> knownNames) {
        super("No job named '" + name + "'. Known jobs: " + knownNames);
    }
}
