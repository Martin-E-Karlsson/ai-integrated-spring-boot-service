package org.example.aiintegratedspringbootservice.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Binds the {@code chat.personalities.*} section of {@code application.yaml}
 * into a name -> system-prompt map.
 * <p>
 * Externalising the prompts (and the set of supported personalities) into
 * config keeps text out of compiled code and lets ops add or tweak a
 * personality without a redeploy.
 */
@Validated
@ConfigurationProperties(prefix = "chat")
public record PersonalityProperties(@NotEmpty Map<String, String> personalities) {

    /**
     * Look up the system prompt for the given personality name (case-insensitive).
     *
     * @throws UnknownPersonalityException if no such personality is configured
     */
    public String systemPromptFor(String name) {
        if (name == null) {
            throw new UnknownPersonalityException("null");
        }
        // Tolerate case differences in the request without expanding the config surface.
        for (var entry : personalities.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        throw new UnknownPersonalityException(name);
    }
}
