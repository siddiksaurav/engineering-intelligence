package com.dsinnovators.ei.source.openproject;

/** Some work packages of an OpenProject import could not be fetched or stored; the others were. */
public class OpenProjectImportFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    OpenProjectImportFailedException(String message) {
        super(message);
    }
}
