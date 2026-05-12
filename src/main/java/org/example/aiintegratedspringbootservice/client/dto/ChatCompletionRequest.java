package org.example.aiintegratedspringbootservice.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OpenAI-compatible chat completion request, as accepted by OpenRouter's
 * {@code /chat/completions} endpoint.
 * <p>
 * Only the fields we actually use are modelled. Additional optional fields
 * (temperature, top_p, max_tokens, ...) can be added without breaking
 * existing callers because we accept defaults from the model side.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<Message> messages) {

    /**
     * One message in the messages array. {@code role} is one of
     * {@code system}, {@code user}, {@code assistant} (lowercase on the wire).
     */
    public record Message(String role, String content) {
    }
}
