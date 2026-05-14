package org.example.aiintegratedspringbootservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.example.aiintegratedspringbootservice.client.OpenRouterClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the {@link RestClient}, {@link Retry} and {@link CircuitBreaker}
 * used by {@link OpenRouterClient}, and enables our own configuration
 * property classes.
 * <p>
 * Connect timeout is fixed at 5s (network handshake should never take that
 * long under healthy conditions); read timeout comes from
 * {@code openrouter.request-timeout} since model generation latency varies
 * with the chosen model.
 */
@Configuration
@EnableConfigurationProperties({OpenRouterProperties.class, PersonalityProperties.class})
public class OpenRouterClientConfig {

    @Bean
    public RestClient openRouterRestClient(RestClient.Builder builder, OpenRouterProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.requestTimeout());

        RestClient.Builder b = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (props.httpReferer() != null && !props.httpReferer().isBlank()) {
            b = b.defaultHeader("HTTP-Referer", props.httpReferer());
        }
        if (props.appTitle() != null && !props.appTitle().isBlank()) {
            b = b.defaultHeader("X-Title", props.appTitle());
        }
        return b.build();
    }

    /**
     * Resilience4j {@link Retry} for outbound OpenRouter calls.
     * Retries on 429, 503 and IO failures with exponential backoff.
     */
    @Bean
    public Retry openRouterRetry(OpenRouterProperties props) {
        OpenRouterProperties.Retry rp = props.retry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(rp.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        rp.waitDuration(), rp.backoffMultiplier()))
                .retryOnException(OpenRouterClient::isRetryable)
                .build();
        return Retry.of("openrouter", config);
    }

    /**
     * Resilience4j {@link CircuitBreaker} for outbound OpenRouter calls.
     * Opens after the configured failure rate is reached and fails fast for
     * the configured wait-duration before allowing limited probe calls.
     */
    @Bean
    public CircuitBreaker openRouterCircuitBreaker(OpenRouterProperties props) {
        OpenRouterProperties.CircuitBreaker cbp = props.circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbp.failureRateThreshold())
                .slidingWindowSize(cbp.slidingWindowSize())
                .minimumNumberOfCalls(cbp.minimumNumberOfCalls())
                .waitDurationInOpenState(cbp.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cbp.permittedNumberOfCallsInHalfOpenState())
                .build();
        return CircuitBreaker.of("openrouter", config);
    }
}
