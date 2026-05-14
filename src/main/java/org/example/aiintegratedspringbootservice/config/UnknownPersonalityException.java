package org.example.aiintegratedspringbootservice.config;

/**
 * Thrown when a chat request references a personality that is not configured.
 * Handled by the global exception handler into a 400 Bad Request ProblemDetail.
 */
public class UnknownPersonalityException extends RuntimeException {

    private final String requested;

    public UnknownPersonalityException(String requested) {
        super("Unknown personality: '" + requested + "'");
        this.requested = requested;
    }

    public String requested() {
        return requested;
    }
}
