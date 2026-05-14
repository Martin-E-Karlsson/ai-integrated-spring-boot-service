package org.example.aiintegratedspringbootservice.api;

import tools.jackson.databind.json.JsonMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.example.aiintegratedspringbootservice.config.UnknownPersonalityException;
import org.example.aiintegratedspringbootservice.exception.GlobalExceptionHandler;
import org.example.aiintegratedspringbootservice.service.ChatResult;
import org.example.aiintegratedspringbootservice.service.ChatService;
import org.example.aiintegratedspringbootservice.service.UpstreamEmptyResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice tests for {@link ChatController}.
 *
 * Uses {@link MockitoBean} (Spring 6.2+) for service stubbing — the older
 * {@code @MockBean} is removed in Spring Boot 4. The {@link GlobalExceptionHandler}
 * is explicitly imported because {@code @WebMvcTest} only loads controllers
 * by default, not {@code @ControllerAdvice} beans.
 */
@WebMvcTest(ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Spring Boot 4 auto-configures a Jackson 3 {@link JsonMapper}, not the
     * legacy Jackson 2 {@code com.fasterxml.jackson.databind.ObjectMapper}.
     */
    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ChatService chatService;

    @Test
    void happyPath_returns200AndBody() throws Exception {
        when(chatService.chat(eq("helper"), eq("Hello"), eq("s1")))
                .thenReturn(new ChatResult("Hi!", "s1", "helper"));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("helper", "Hello", "s1"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reply").value("Hi!"))
                .andExpect(jsonPath("$.sessionId").value("s1"))
                .andExpect(jsonPath("$.personality").value("helper"));
    }

    @Test
    void missingPersonality_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Hello"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personality": "helper", "message": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPersonality_returns400WithProblemDetail() throws Exception {
        when(chatService.chat(any(), any(), any()))
                .thenThrow(new UnknownPersonalityException("villain"));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("villain", "Hello", null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unknown personality"))
                .andExpect(jsonPath("$.requestedPersonality").value("villain"));
    }

    @Test
    void emptyUpstream_returns502() throws Exception {
        when(chatService.chat(any(), any(), any()))
                .thenThrow(new UpstreamEmptyResponseException("nothing came back"));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("helper", "Hello", null))))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Empty upstream response"));
    }

    @Test
    void circuitOpen_returns503() throws Exception {
        CircuitBreaker cb = CircuitBreaker.of("t",
                CircuitBreakerConfig.custom()
                        .minimumNumberOfCalls(1)
                        .slidingWindowSize(1)
                        .failureRateThreshold(1.0f).build());
        cb.transitionToOpenState();
        when(chatService.chat(any(), any(), any()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("helper", "Hello", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Service degraded"));
    }
}
