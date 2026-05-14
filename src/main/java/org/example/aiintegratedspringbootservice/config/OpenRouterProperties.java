package org.example.aiintegratedspringbootservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binds the {@code openrouter.*} section of {@code application.yaml}.
 * <p>
 * Holding everything OpenRouter-related (auth, endpoint, model, resilience
 * tuning) under one prefix keeps the operational surface obvious and avoids
 * spreading related knobs across multiple property classes.
 */
@Validated
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String model,
        @NotNull Duration requestTimeout,
        String httpReferer,
        String appTitle,
        @NotNull Retry retry,
        @NotNull CircuitBreaker circuitBreaker) {

    public record Retry(
            @Positive int maxAttempts,
            @NotNull Duration waitDuration,
            @Positive double backoffMultiplier) {
    }

    public record CircuitBreaker(
            @Positive float failureRateThreshold,
            @Positive int slidingWindowSize,
            @Positive int minimumNumberOfCalls,
            @NotNull Duration waitDurationInOpenState,
            @Positive int permittedNumberOfCallsInHalfOpenState) {
    }
}
