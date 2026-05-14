package org.example.aiintegratedspringbootservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verifies the Spring application context starts.
 * Uses TestPropertySource to satisfy required OpenRouter properties during
 * context load so the test doesn't depend on the host environment.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "openrouter.api-key=test-key",
        "openrouter.base-url=http://localhost:0"
})
class AiIntegratedSpringBootServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
