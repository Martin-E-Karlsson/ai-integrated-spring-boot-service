package org.example.aiintegratedspringbootservice.service;

/**
 * Thrown when OpenRouter answered with a 2xx but the body had no usable
 * assistant content (no choices, or first choice missing message text).
 * Mapped to 502 Bad Gateway by the global exception handler.
 */
public class UpstreamEmptyResponseException extends RuntimeException {
    public UpstreamEmptyResponseException(String message) {
        super(message);
    }
}
