package org.example.aiintegratedspringbootservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI-compatible chat completion response from OpenRouter.
 * <p>
 * Unknown fields (id, created, model, usage, ...) are ignored to keep the
 * client tolerant to provider-specific extensions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            ChatCompletionRequest.Message message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    /**
     * Convenience: the assistant text from the first choice, or {@code null}
     * if the response had no choices (which would itself be a provider error).
     */
    public String firstChoiceContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        var msg = choices.getFirst().message();
        return msg == null ? null : msg.content();
    }
}
