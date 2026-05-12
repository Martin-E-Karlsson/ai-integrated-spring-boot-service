package org.example.aiintegratedspringbootservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PersonalityProperties}, including
 * Spring property-binding behaviour via {@link ApplicationContextRunner}.
 */
class PersonalityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnablePropsConfig.class);

    @EnableConfigurationProperties(PersonalityProperties.class)
    static class EnablePropsConfig {}

    @Test
    void bindsPersonalitiesFromYamlStyleKeys() {
        runner.withPropertyValues(
                "chat.personalities.helper=Be helpful.",
                "chat.personalities.pirate=Be a pirate."
        ).run(context -> {
            PersonalityProperties props = context.getBean(PersonalityProperties.class);
            assertThat(props.personalities())
                    .containsEntry("helper", "Be helpful.")
                    .containsEntry("pirate", "Be a pirate.");
        });
    }

    @Test
    void systemPromptForReturnsConfiguredPrompt() {
        var props = new PersonalityProperties(Map.of(
                "helper", "Be helpful.",
                "coder", "Write code."
        ));

        assertThat(props.systemPromptFor("helper")).isEqualTo("Be helpful.");
    }

    @Test
    void systemPromptForIsCaseInsensitive() {
        var props = new PersonalityProperties(Map.of("Pirate", "Arrr."));

        assertThat(props.systemPromptFor("pirate")).isEqualTo("Arrr.");
        assertThat(props.systemPromptFor("PIRATE")).isEqualTo("Arrr.");
    }

    @Test
    void systemPromptForThrowsOnUnknownPersonality() {
        var props = new PersonalityProperties(Map.of("helper", "Be helpful."));

        assertThatThrownBy(() -> props.systemPromptFor("villain"))
                .isInstanceOf(UnknownPersonalityException.class)
                .hasMessageContaining("villain");
    }

    @Test
    void systemPromptForThrowsOnNullName() {
        var props = new PersonalityProperties(Map.of("helper", "Be helpful."));

        assertThatThrownBy(() -> props.systemPromptFor(null))
                .isInstanceOf(UnknownPersonalityException.class);
    }
}
