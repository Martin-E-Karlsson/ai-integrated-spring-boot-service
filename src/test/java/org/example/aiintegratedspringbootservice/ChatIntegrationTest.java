package org.example.aiintegratedspringbootservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.aiintegratedspringbootservice.api.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test: the full Spring context is loaded, OpenRouter is stubbed
 * by WireMock, and we hit the controller via MockMvc to verify the entire
 * pipeline (validation, personality lookup, memory, RestClient call, retry,
 * exception handling, ProblemDetail formatting).
 *
 * Static imports for {@code post}, {@code aResponse}, etc. are intentionally
 * NOT used because {@code WireMock.post} and {@code MockMvcRequestBuilders.post}
 * collide; we qualify both classes explicitly.
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
        // Speed up retry to keep the test fast.
        registry.add("openrouter.retry.wait-duration", () -> "1ms");
        registry.add("openrouter.retry.backoff-multiplier", () -> "1.0");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    private static final String SUCCESS_BODY_1 = """
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hi!"},"finish_reason":"stop"}]}
            """;
    private static final String SUCCESS_BODY_2 = """
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Yes, of course."},"finish_reason":"stop"}]}
            """;

    @Test
    void fullSuccessFlowAndMemoryAcrossTurns() throws Exception {
        // First turn — initial state of the WireMock scenario
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

        // Second turn — reuse the generated sessionId
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

        // Verify both calls hit OpenRouter and the second carried full history.
        openRouter.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/chat/completions")));
        var events = openRouter.getAllServeEvents();
        // getAllServeEvents() returns newest-first, so index 0 is the second call.
        String secondCallBody = events.get(0).getRequest().getBodyAsString();
        assertThat(secondCallBody)
                .contains("\"Was that polite?\"")
                .contains("\"Hello\"")
                .contains("\"Hi!\"");
    }

    @Test
    void retryThenSuccessTransparentToCaller() throws Exception {
        openRouter.stubFor(WireMock.post(WireMock.urlEqualTo("/chat/completions"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("after-fail"));
        openRouter.stubFor(WireMock.post(WireMock.urlEqualTo("/chat/completions"))
                .inScenario("retry")
                .whenScenarioStateIs("after-fail")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY_1)));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new ChatRequest("helper", "Hello", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi!"));

        openRouter.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/chat/completions")));
    }

    @Test
    void unknownPersonalityReturns400ProblemDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new ChatRequest("villain", "Hi", null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unknown personality"))
                .andExpect(jsonPath("$.requestedPersonality").value("villain"));
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
