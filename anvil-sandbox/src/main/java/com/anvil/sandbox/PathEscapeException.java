package com.anvil.sandbox;

/** Thrown when a path escapes the workspace root. */
public class PathEscapeException extends RuntimeException {

    public PathEscapeException(String message) {
        super(message);
    }
}
