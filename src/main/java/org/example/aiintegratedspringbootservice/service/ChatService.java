package org.example.aiintegratedspringbootservice.service;

import lombok.RequiredArgsConstructor;
import org.example.aiintegratedspringbootservice.client.OpenRouterClient;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionRequest;
import org.example.aiintegratedspringbootservice.client.dto.ChatCompletionResponse;
import org.example.aiintegratedspringbootservice.config.PersonalityProperties;
import org.example.aiintegratedspringbootservice.memory.ChatMemoryService;
import org.example.aiintegratedspringbootservice.memory.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates a single chat turn:
 * <ol>
 *   <li>resolves the personality -&gt; system prompt,</li>
 *   <li>loads prior history for the session (if any),</li>
 *   <li>appends the new user message to history,</li>
 *   <li>calls OpenRouter with [system, ...history],</li>
 *   <li>appends the assistant reply to history,</li>
 *   <li>returns a {@link ChatResult} echoing the session id.</li>
 * </ol>
 * If the caller omitted {@code sessionId} we generate one (see D-013) so
 * memory always has somewhere to live and the caller can continue the
 * conversation on subsequent calls.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final PersonalityProperties personalities;
    private final ChatMemoryService memory;
    private final OpenRouterClient openRouter;

    public ChatResult chat(String personality, String userMessage, String sessionId) {
        String systemPrompt = personalities.systemPromptFor(personality);
        String effectiveSessionId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;

        memory.append(effectiveSessionId, ChatMessage.user(userMessage));

        List<ChatCompletionRequest.Message> wireMessages = buildWireMessages(systemPrompt, effectiveSessionId);
        ChatCompletionResponse response = openRouter.complete(wireMessages);

        String reply = response.firstChoiceContent();
        if (reply == null || reply.isBlank()) {
            throw new UpstreamEmptyResponseException(
                    "OpenRouter returned no assistant content for session=" + effectiveSessionId);
        }

        memory.append(effectiveSessionId, ChatMessage.assistant(reply));
        return new ChatResult(reply, effectiveSessionId, personality);
    }

    /**
     * Combine the system prompt with the stored history for the session.
     * The new user message is already appended to history before this call,
     * so it appears as the last item of {@code messages}.
     */
    private List<ChatCompletionRequest.Message> buildWireMessages(String systemPrompt, String sessionId) {
        List<ChatMessage> history = memory.history(sessionId);
        List<ChatCompletionRequest.Message> out = new ArrayList<>(history.size() + 1);
        out.add(new ChatCompletionRequest.Message("system", systemPrompt));
        for (ChatMessage m : history) {
            out.add(new ChatCompletionRequest.Message(m.role().name().toLowerCase(Locale.ROOT), m.content()));
        }
        return out;
    }
}
