package org.example.aiintegratedspringbootservice.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.example.aiintegratedspringbootservice.config.UnknownPersonalityException;
import org.example.aiintegratedspringbootservice.service.UpstreamEmptyResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Translates exceptions thrown by the chat pipeline into RFC 7807
 * ProblemDetail responses ({@code application/problem+json}).
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so we inherit Spring's
 * default ProblemDetail handlers for built-in web exceptions
 * ({@link org.springframework.web.bind.MethodArgumentNotValidException},
 * {@code HttpMessageNotReadableException}, ...). Our custom
 * {@link ExceptionHandler}s below handle the failure modes specific to
 * this service; the inherited handlers cover validation and binding errors.
 * Because Spring picks the most specific {@code @ExceptionHandler} match,
 * our catch-all {@link #handleUnexpected(Exception)} only fires for
 * genuinely unhandled exceptions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Unknown personality requested -&gt; 400. */
    @ExceptionHandler(UnknownPersonalityException.class)
    public ProblemDetail handleUnknownPersonality(UnknownPersonalityException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Unknown personality");
        pd.setType(URI.create("https://example.org/problems/unknown-personality"));
        pd.setProperty("requestedPersonality", ex.requested());
        return pd;
    }

    /** Upstream answered 2xx but with empty/unusable body -&gt; 502. */
    @ExceptionHandler(UpstreamEmptyResponseException.class)
    public ProblemDetail handleEmptyUpstream(UpstreamEmptyResponseException ex) {
        log.warn("Upstream returned empty content: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Upstream LLM returned no usable content.");
        pd.setTitle("Empty upstream response");
        pd.setType(URI.create("https://example.org/problems/empty-upstream"));
        return pd;
    }

    /** Upstream returned a non-2xx status that wasn't retried/recovered -&gt; propagate. */
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleUpstreamHttpError(RestClientResponseException ex) {
        HttpStatusCode upstreamStatus = ex.getStatusCode();
        HttpStatus mapped = mapUpstreamStatus(upstreamStatus);
        log.warn("Upstream HTTP error {} -> responding {}", upstreamStatus, mapped);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(mapped,
                "Upstream LLM responded with HTTP " + upstreamStatus.value() + ".");
        pd.setTitle("Upstream LLM error");
        pd.setType(URI.create("https://example.org/problems/upstream-http-error"));
        pd.setProperty("upstreamStatus", upstreamStatus.value());
        return pd;
    }

    /** Connection refused / timeout / DNS error talking to upstream -&gt; 503. */
    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail handleUpstreamUnreachable(ResourceAccessException ex) {
        log.warn("Upstream unreachable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream LLM is currently unreachable.");
        pd.setTitle("Upstream unreachable");
        pd.setType(URI.create("https://example.org/problems/upstream-unreachable"));
        return pd;
    }

    /** Circuit breaker is open -&gt; short-circuit to 503. */
    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit breaker open: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream LLM is temporarily disabled due to repeated failures. Please retry later.");
        pd.setTitle("Service degraded");
        pd.setType(URI.create("https://example.org/problems/circuit-open"));
        return pd;
    }

    /** Anything we did not anticipate -&gt; 500. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception in chat pipeline", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        pd.setTitle("Internal error");
        pd.setType(URI.create("https://example.org/problems/internal-error"));
        return pd;
    }

    /**
     * Map upstream HTTP status to the status we return to our caller.
     * Rules:
     * <ul>
     *   <li>5xx upstream -&gt; {@code 503 Service Unavailable} (we are a proxy that depends on it),</li>
     *   <li>{@code 429} upstream -&gt; {@code 429 Too Many Requests},</li>
     *   <li>{@code 401/403} upstream -&gt; {@code 502 Bad Gateway} (our config error, not the client's),</li>
     *   <li>any other 4xx upstream -&gt; {@code 502 Bad Gateway}.</li>
     * </ul>
     */
    private static HttpStatus mapUpstreamStatus(HttpStatusCode upstream) {
        int s = upstream.value();
        if (s == 429) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (s >= 500) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
