package com.bettercontroller.logic;

/** Thrown when graph data is malformed or violates a rule or limit. The message is shown to the player. */
public class GraphValidationException extends Exception {
    private static final long serialVersionUID = 1L;

    public GraphValidationException(String message) {
        super(message);
    }
}
