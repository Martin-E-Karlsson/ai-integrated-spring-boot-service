package org.example.aiintegratedspringbootservice;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.aiintegratedspringbootservice.api.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test: the full Spring context is loaded, OpenRouter is stubbed
 * by WireMock, and we hit the controller via MockMvc to verify the entire
 * pipeline (validation, personality lookup, memory, RestClient call,
 * exception handling, ProblemDetail formatting).
 *
 * Only the two genuinely-cross-layer scenarios live here:
 * <ul>
 *   <li>full success across two turns proving memory survives the round-trip,</li>
 *   <li>persistent upstream failure proving the ProblemDetail mapping reaches the caller.</li>
 * </ul>
 * Retry-on-transient and unknown-personality are already covered by
 * {@code OpenRouterClientTest} and {@code ChatControllerTest} respectively.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatIntegrationTest {

    @RegisterExtension
    static WireMockExtension openRouter = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("openrouter.base-url", () -> openRouter.baseUrl());
        registry.add("openrouter.api-key", () -> "test-key");
        registry.add("openrouter.retry.wait-duration", () -> "1ms");
        registry.add("openrouter.retry.backoff-multiplier", () -> "1.0");
        registry.add("openrouter.circuit-breaker.failure-rate-threshold", () -> "100");
        registry.add("openrouter.circuit-breaker.minimum-number-of-calls", () -> "10000");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper mapper;

    private static final String SUCCESS_BODY_1 = """
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hi!"},"finish_reason":"stop"}]}
            """;
    private static final String SUCCESS_BODY_2 = """
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Yes, of course."},"finish_reason":"stop"}]}
            """;

    @Test
    void fullSuccessFlowAndMemoryAcrossTurns() throws Exception {
        openRouter.stubFor(WireMock.post(WireMock.urlEqualTo("/chat/completions"))
                .inScenario("memory")
                .whenScenarioStateIs("Started")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY_1))
                .willSetStateTo("after-1"));

        MvcResult first = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new ChatRequest("helper", "Hello", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi!"))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.personality").value("helper"))
                .andReturn();

        String sessionId = mapper.readTree(first.getResponse().getContentAsString())
                .get("sessionId").asText();

        openRouter.stubFor(WireMock.post(WireMock.urlEqualTo("/chat/completions"))
                .inScenario("memory")
                .whenScenarioStateIs("after-1")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY_2)));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new ChatRequest("helper", "Was that polite?", sessionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Yes, of course."))
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        openRouter.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/chat/completions")));
        var events = openRouter.getAllServeEvents();
        String secondCallBody = events.get(0).getRequest().getBodyAsString();
        assertThat(secondCallBody)
                .contains("\"Was that polite?\"")
                .contains("\"Hello\"")
                .contains("\"Hi!\"");
    }

    @Test
    void upstreamPermanentlyDownReturns503() throws Exception {
        openRouter.stubFor(WireMock.post(WireMock.urlEqualTo("/chat/completions"))
                .willReturn(WireMock.aResponse().withStatus(503)));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new ChatRequest("helper", "Hi", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Upstream LLM error"));
    }
}
