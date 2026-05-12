package org.example.aiintegratedspringbootservice.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionRequest;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionResponse;
import org.example.aiintegratedspringbootservice.config.OpenRouterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Synchronous client for OpenRouter's chat-completions API.
 * <p>
 * Wraps a {@link RestClient} with:
 * <ul>
 *   <li>Resilience4j {@link Retry} — exponential backoff on transient errors
 *       (HTTP 429, 503, or network I/O failures);</li>
 *   <li>Resilience4j {@link CircuitBreaker} — fails fast once the failure
 *       rate over the sliding window exceeds the configured threshold.</li>
 * </ul>
 * Decorations are applied as
 * {@code Retry(CircuitBreaker(supplier))} — the breaker is the innermost
 * wrapper, so each retry attempt is recorded as a discrete event. Once the
 * breaker opens subsequent calls short-circuit with
 * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException}.
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private final RestClient restClient;
    private final OpenRouterProperties props;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public OpenRouterClient(RestClient openRouterRestClient, OpenRouterProperties props) {
        this(openRouterRestClient, props, buildRetry(props.retry()), buildCircuitBreaker(props.circuitBreaker()));
    }

    /**
     * Package-private constructor used by tests that want to inject custom
     * Retry / CircuitBreaker instances (e.g. tighter timing for fast tests).
     */
    OpenRouterClient(RestClient restClient,
                     OpenRouterProperties props,
                     Retry retry,
                     CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.props = props;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Send a chat completion request to OpenRouter using the configured
     * model and the given message history. Returns the parsed response.
     *
     * @throws RestClientResponseException on non-retryable HTTP errors
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException
     *         when the circuit breaker is open
     */
    public ChatCompletionResponse complete(List<ChatCompletionRequest.Message> messages) {
        ChatCompletionRequest request = new ChatCompletionRequest(props.model(), messages);
        Supplier<ChatCompletionResponse> base = () -> doCall(request);
        Supplier<ChatCompletionResponse> withBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, base);
        Supplier<ChatCompletionResponse> withRetry = Retry.decorateSupplier(retry, withBreaker);
        return withRetry.get();
    }

    private ChatCompletionResponse doCall(ChatCompletionRequest request) {
        log.debug("Calling OpenRouter /chat/completions for model={}, messages={}",
                request.model(), request.messages().size());
        return restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
    }

    /* ---- factories ----------------------------------------------------- */

    static Retry buildRetry(OpenRouterProperties.Retry rp) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(rp.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        rp.waitDuration(), rp.backoffMultiplier()))
                .retryOnException(OpenRouterClient::isRetryable)
                .build();
        return Retry.of("openrouter", config);
    }

    static CircuitBreaker buildCircuitBreaker(OpenRouterProperties.CircuitBreaker cbp) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbp.failureRateThreshold())
                .slidingWindowSize(cbp.slidingWindowSize())
                .minimumNumberOfCalls(cbp.minimumNumberOfCalls())
                .waitDurationInOpenState(cbp.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cbp.permittedNumberOfCallsInHalfOpenState())
                .build();
        return CircuitBreaker.of("openrouter", config);
    }

    /**
     * A failure is retryable iff it is a transient network problem
     * (read/connect failure) or one of the upstream's documented
     * "please back off" status codes — 429 (rate limited) and 503
     * (service unavailable). Everything else (auth errors, 4xx, model
     * errors) is permanent for the current request and is propagated.
     */
    static boolean isRetryable(Throwable t) {
        if (t instanceof RestClientResponseException rcre) {
            int status = rcre.getStatusCode().value();
            return status == 429 || status == 503;
        }
        return t instanceof ResourceAccessException;
    }
}
