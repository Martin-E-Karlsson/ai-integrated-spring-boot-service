package org.example.aiintegratedspringbootservice.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionRequest;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionResponse;
import org.example.aiintegratedspringbootservice.config.OpenRouterProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenRouterClient} against a WireMock-stubbed OpenRouter.
 * Verifies: happy path, retry-on-transient (429 and 503),
 * no-retry-on-permanent, and retry exhaustion behaviour.
 *
 * Retry timings are forced to {@code 1ms} via test-only Retry/CircuitBreaker
 * instances so the suite stays sub-second.
 */
class OpenRouterClientTest {

    @RegisterExtension
    static WireMockExtension openRouter = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OpenRouterClient newClient(int maxAttempts) {
        OpenRouterProperties props = new OpenRouterProperties(
                "test-key",
                openRouter.baseUrl(),
                "openai/gpt-4o-mini",
                Duration.ofSeconds(5),
                null,
                null,
                new OpenRouterProperties.Retry(maxAttempts, Duration.ofMillis(1), 2.0),
                new OpenRouterProperties.CircuitBreaker(
                        100f, 100, 100, Duration.ofSeconds(30), 3));

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        RestClient restClient = RestClient.builder()
                .baseUrl(openRouter.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(http))
                .defaultHeader("Authorization", "Bearer test-key")
                .defaultHeader("Content-Type", "application/json")
                .build();

        Retry retry = Retry.of("test-retry", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.of(Duration.ofMillis(1)))
                .retryOnException(OpenRouterClient::isRetryable)
                .build());

        CircuitBreaker breaker = CircuitBreaker.of("test-breaker", CircuitBreakerConfig.custom()
                .failureRateThreshold(100.0f)
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .build());

        return new OpenRouterClient(restClient, props, retry, breaker);
    }

    private static final String SUCCESS_BODY = """
            {
              "id": "abc",
              "model": "openai/gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": { "role": "assistant", "content": "Hello!" },
                  "finish_reason": "stop"
                }
              ]
            }
            """;

    @Test
    void returnsParsedResponseOnSuccess() {
        openRouter.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        ChatCompletionResponse response = newClient(3).complete(List.of(
                new ChatCompletionRequest.Message("user", "Hi")));

        assertThat(response.firstChoiceContent()).isEqualTo("Hello!");
        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().getFirst().finishReason()).isEqualTo("stop");
    }

    @Test
    void sendsCorrectBodyAndAuthorizationHeader() {
        openRouter.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        newClient(3).complete(List.of(
                new ChatCompletionRequest.Message("system", "Be a pirate."),
                new ChatCompletionRequest.Message("user", "Where's the loot?")));

        openRouter.verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(equalToJson("""
                        {
                          "model": "openai/gpt-4o-mini",
                          "messages": [
                            {"role": "system", "content": "Be a pirate."},
                            {"role": "user", "content": "Where's the loot?"}
                          ]
                        }
                        """)));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void retriesOnTransientStatusAndEventuallySucceeds(int transientStatus) {
        openRouter.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(transientStatus))
                .willSetStateTo("recovered"));
        openRouter.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        ChatCompletionResponse response = newClient(4).complete(List.of(
                new ChatCompletionRequest.Message("user", "Hi")));

        assertThat(response.firstChoiceContent()).isEqualTo("Hello!");
        openRouter.verify(2, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    void doesNotRetryOnPermanent4xx() {
        openRouter.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"bad key\"}")));

        assertThatThrownBy(() -> newClient(4).complete(List.of(
                new ChatCompletionRequest.Message("user", "Hi"))))
                .isInstanceOf(RestClientResponseException.class);

        openRouter.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    void exhaustsRetryBudgetWhenServerStaysDown() {
        openRouter.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> newClient(3).complete(List.of(
                new ChatCompletionRequest.Message("user", "Hi"))))
                .isInstanceOf(RestClientResponseException.class);

        openRouter.verify(3, postRequestedFor(urlEqualTo("/chat/completions")));
    }
}
