# AI-Integrated Spring Boot Service

Middleware service (Laboration 1) that bridges an end user and an LLM. Maps a personality choice to a system prompt, retains conversation history per session, and forwards the call to OpenRouter via Spring `RestClient` with retry/circuit-breaker resilience.

See [`DECISIONS.md`](DECISIONS.md) for the architecture log and rationale for each design choice.

## Stack

Java 26, Spring Boot 4.0.6, Spring Cloud 2025.1.1, Resilience4j, springdoc-openapi, WireMock (test).

## Run locally

Set your OpenRouter API key (get one at https://openrouter.ai/keys):

```bash
export OPENROUTER_API_KEY=sk-or-v1-...
./mvnw spring-boot:run
```

Then:

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

## Test

```bash
./mvnw verify
```

Test layers:

- `*ServiceTest` / `*PropertiesTest` — unit tests (no Spring context)
- `OpenRouterClientTest` — client + WireMock (no Spring context)
- `ChatControllerTest` — `@WebMvcTest` web slice
- `ChatIntegrationTest` — full `@SpringBootTest` with WireMock backing OpenRouter
