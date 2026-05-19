package org.example.aiintegratedspringbootservice.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionRequest;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionResponse;
import org.example.aiintegratedspringbootservice.config.OpenRouterProperties;
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
 *   <li>Resilience4j {@link Retry} &mdash; exponential backoff on transient errors
 *       (HTTP 429, 503, or network I/O failures);</li>
 *   <li>Resilience4j {@link CircuitBreaker} &mdash; fails fast once the failure
 *       rate over the sliding window exceeds the configured threshold.</li>
 * </ul>
 * Decorations are applied as {@code Retry(CircuitBreaker(supplier))} so each
 * retry attempt is recorded as a discrete circuit-breaker event; once the
 * breaker opens, subsequent calls short-circuit with
 * {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterClient {

    private final RestClient openRouterRestClient;
    private final OpenRouterProperties props;
    private final Retry openRouterRetry;
    private final CircuitBreaker openRouterCircuitBreaker;

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
        Supplier<ChatCompletionResponse> withBreaker = CircuitBreaker.decorateSupplier(openRouterCircuitBreaker, base);
        Supplier<ChatCompletionResponse> withRetry = Retry.decorateSupplier(openRouterRetry, withBreaker);
        return withRetry.get();
    }

    private ChatCompletionResponse doCall(ChatCompletionRequest request) {
        log.debug("Calling OpenRouter /chat/completions for model={}, messages={}",
                request.model(), request.messages().size());
        return openRouterRestClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
    }

    /**
     * A failure is retryable iff it is a transient network problem
     * (read/connect failure) or one of the upstream's documented
     * "please back off" status codes &mdash; 429 (rate limited) and 503
     * (service unavailable). Everything else (auth errors, 4xx, model
     * errors) is permanent for the current request and is propagated.
     */
    public static boolean isRetryable(Throwable t) {
        if (t instanceof RestClientResponseException rcre) {
            int status = rcre.getStatusCode().value();
            return status == 429 || status == 503;
        }
        return t instanceof ResourceAccessException;
    }
}
