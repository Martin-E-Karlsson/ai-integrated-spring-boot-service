package org.example.aiintegratedspringbootservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi metadata for the auto-generated OpenAPI 3 document.
 * The UI is served at {@code /swagger-ui.html} and the JSON at
 * {@code /v3/api-docs} (see {@code springdoc.*} in application.yaml).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Integrated Spring Boot Service")
                        .description("Middleware that bridges end users and an LLM via OpenRouter. "
                                + "Choose a personality, optionally maintain a session, and chat.")
                        .version("v1")
                        .contact(new Contact().name("Martin Karlsson")));
    }
}
