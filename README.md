# AI-Integrated Spring Boot Service

Middleware service that bridges an end user and an LLM. Maps a personality choice to a system prompt, retains conversation history per session, and forwards the call to OpenRouter via Spring `RestClient` with retry/circuit-breaker resilience.

## Stack

Java 26, Spring Boot 4.0.6, Spring Cloud 2025.1.1, Resilience4j, springdoc-openapi, WireMock (test).

## Run locally

Set your OpenRouter API key (get one at https://openrouter.ai/keys):

```bash
export OPENROUTER_API_KEY=sk-or-v1-...
./mvnw spring-boot:run
```

Read .env.example to see how the API key should be implemented. 

Then use one of the following options to test the API with requests:

- POST `http://localhost:8080/api/v1/chat`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

Sample request:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"personality":"coder","message":"How do I write a for-loop in Java?"}'
```

The response includes a `sessionId`. Reuse it on follow-up calls to continue the conversation.

Example requests can also be made through IntelliJ IDEA Ultimate's built-in HTTP Client.
Go to http/chat.http and run each request using the green ► next to it once the application is running.

## Test

```bash
./mvnw verify
```

Test layers:

- `*ServiceTest` / `*PropertiesTest` — unit tests (no Spring context)
- `OpenRouterClientTest` — client + WireMock (no Spring context)
- `ChatControllerTest` — `@WebMvcTest` web slice
- `ChatIntegrationTest` — full `@SpringBootTest` with WireMock backing OpenRouter
