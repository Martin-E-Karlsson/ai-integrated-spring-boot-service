package org.example.aiintegratedspringbootservice.memory;

/**
 * Immutable, OpenAI-compatible chat message record used both for in-memory
 * session history and as the wire format toward OpenRouter.
 * <p>
 * Kept in the {@code memory} package because the memory service is the
 * canonical owner of the type; the {@code client} package re-exposes it as
 * part of the OpenRouter request payload.
 */
public record ChatMessage(Role role, String content) {

    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public enum Role {
        USER, ASSISTANT, SYSTEM
    }
}
