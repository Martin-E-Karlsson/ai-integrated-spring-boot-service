package org.example.aiintegratedspringbootservice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.aiintegratedspringbootservice.service.ChatResult;
import org.example.aiintegratedspringbootservice.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point: a middleware that converts a user request into an
 * LLM call (via {@link ChatService}) and returns the assistant's reply.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Chat", description = "Conversational endpoint over OpenRouter")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send a chat message",
            description = "Maps a personality to a system prompt, replays prior history for the "
                    + "session, and forwards the conversation to OpenRouter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful reply"),
            @ApiResponse(responseCode = "400", description = "Validation error or unknown personality",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "502", description = "Upstream LLM returned an unusable response",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Upstream LLM unavailable",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ChatResult result = chatService.chat(
                request.personality(), request.message(), request.sessionId());
        return new ChatResponse(result.reply(), result.sessionId(), result.personality());
    }
}
