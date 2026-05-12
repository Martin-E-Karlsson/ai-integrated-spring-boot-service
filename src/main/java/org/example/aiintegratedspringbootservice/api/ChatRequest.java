package org.example.aiintegratedspringbootservice.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound chat request body for {@code POST /api/v1/chat}.
 *
 * @param personality required — selects the system prompt (e.g. helper, pirate, coder)
 * @param message     required — the user's question
 * @param sessionId   optional — used to maintain conversation history; if absent,
 *                    the server generates one and echoes it in the response
 */
@Schema(name = "ChatRequest", description = "Single-turn chat input")
public record ChatRequest(
        @NotBlank
        @Size(max = 64)
        @Schema(description = "Personality preset to apply", example = "helper")
        String personality,

        @NotBlank
        @Size(max = 8000)
        @Schema(description = "End user's message to the LLM", example = "What is polymorphism?")
        String message,

        @Size(max = 128)
        @Schema(description = "Conversation/session identifier; omit on first call to receive a generated one",
                example = "user-123-abc")
        String sessionId) {
}
