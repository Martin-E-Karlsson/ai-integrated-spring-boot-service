package org.example.aiintegratedspringbootservice.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outbound chat response from {@code POST /api/v1/chat}.
 *
 * @param reply       the assistant's response text
 * @param sessionId   the session id used (may have been generated server-side)
 * @param personality the personality that was applied (echoed for clarity)
 */
@Schema(name = "ChatResponse", description = "Single-turn chat output")
public record ChatResponse(
        @Schema(description = "Assistant's reply text", example = "Polymorphism is...")
        String reply,

        @Schema(description = "Session id used; reuse this for follow-up calls",
                example = "9e7c4d2a-...")
        String sessionId,

        @Schema(description = "Personality that was applied", example = "helper")
        String personality) {
}
