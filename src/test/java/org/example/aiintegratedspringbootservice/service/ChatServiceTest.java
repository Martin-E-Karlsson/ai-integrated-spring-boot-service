package org.example.aiintegratedspringbootservice.service;

import org.example.aiintegratedspringbootservice.client.OpenRouterClient;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionRequest;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionResponse;
import org.example.aiintegratedspringbootservice.config.PersonalityProperties;
import org.example.aiintegratedspringbootservice.config.UnknownPersonalityException;
import org.example.aiintegratedspringbootservice.memory.ChatMemoryService;
import org.example.aiintegratedspringbootservice.memory.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final String HELPER_PROMPT = "You are a helpful assistant.";
    private static final String CODER_PROMPT = "You are a senior engineer.";

    private OpenRouterClient openRouter;
    private ChatMemoryService memory;
    private PersonalityProperties personalities;
    private ChatService service;

    @BeforeEach
    void setUp() {
        openRouter = mock(OpenRouterClient.class);
        memory = new ChatMemoryService(20);
        personalities = new PersonalityProperties(Map.of(
                "helper", HELPER_PROMPT,
                "coder", CODER_PROMPT
        ));
        service = new ChatService(personalities, memory, openRouter);
    }

    private void stubReply(String text) {
        when(openRouter.complete(any())).thenReturn(new ChatCompletionResponse(List.of(
                new ChatCompletionResponse.Choice(
                        0,
                        new ChatCompletionRequest.Message("assistant", text),
                        "stop"))));
    }

    @Test
    void returnsAssistantReplyAndEchoesProvidedSessionId() {
        stubReply("Hi there");

        ChatResult result = service.chat("helper", "Hello", "sess-1");

        assertThat(result.reply()).isEqualTo("Hi there");
        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.personality()).isEqualTo("helper");
    }

    @Test
    void generatesSessionIdWhenMissing() {
        stubReply("Hi");

        ChatResult result = service.chat("helper", "Hello", null);

        assertThat(result.sessionId())
                .isNotBlank()
                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void generatesSessionIdWhenBlank() {
        stubReply("Hi");

        ChatResult result = service.chat("helper", "Hello", "   ");

        assertThat(result.sessionId()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void prependsSystemPromptAndIncludesUserMessageInRequest() {
        stubReply("ack");

        service.chat("coder", "Write a for-loop", "sess-2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(openRouter).complete(captor.capture());
        List<ChatCompletionRequest.Message> sent = captor.getValue();

        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).role()).isEqualTo("system");
        assertThat(sent.get(0).content()).isEqualTo(CODER_PROMPT);
        assertThat(sent.get(1).role()).isEqualTo("user");
        assertThat(sent.get(1).content()).isEqualTo("Write a for-loop");
    }

    @Test
    void appendsUserAndAssistantMessagesToMemory() {
        stubReply("Use a `for` loop.");

        service.chat("coder", "How do I loop?", "sess-3");

        assertThat(memory.history("sess-3"))
                .extracting(ChatMessage::role, ChatMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ChatMessage.Role.USER, "How do I loop?"),
                        org.assertj.core.groups.Tuple.tuple(ChatMessage.Role.ASSISTANT, "Use a `for` loop."));
    }

    @Test
    void secondTurnIncludesPriorHistoryInRequest() {
        // Turn 1
        stubReply("Hi");
        service.chat("helper", "Hello", "sess-4");

        // Turn 2
        stubReply("Sure thing");
        service.chat("helper", "Follow-up", "sess-4");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor =
                ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(openRouter, org.mockito.Mockito.times(2)).complete(captor.capture());
        List<ChatCompletionRequest.Message> secondCallMessages = captor.getAllValues().get(1);

        // [system, user1, assistant1, user2]
        assertThat(secondCallMessages).hasSize(4);
        assertThat(secondCallMessages.get(0).role()).isEqualTo("system");
        assertThat(secondCallMessages.get(1).role()).isEqualTo("user");
        assertThat(secondCallMessages.get(1).content()).isEqualTo("Hello");
        assertThat(secondCallMessages.get(2).role()).isEqualTo("assistant");
        assertThat(secondCallMessages.get(2).content()).isEqualTo("Hi");
        assertThat(secondCallMessages.get(3).role()).isEqualTo("user");
        assertThat(secondCallMessages.get(3).content()).isEqualTo("Follow-up");
    }

    @Test
    void unknownPersonalityThrows() {
        assertThatThrownBy(() -> service.chat("villain", "Hello", "sess-5"))
                .isInstanceOf(UnknownPersonalityException.class);

        // Memory must not be polluted by a rejected request
        assertThat(memory.history("sess-5")).isEmpty();
    }

    @Test
    void emptyUpstreamReplyThrows() {
        when(openRouter.complete(any())).thenReturn(new ChatCompletionResponse(List.of()));

        assertThatThrownBy(() -> service.chat("helper", "Hello", "sess-6"))
                .isInstanceOf(UpstreamEmptyResponseException.class);
    }
}
