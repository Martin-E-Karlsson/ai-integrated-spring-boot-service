package org.example.aiintegratedspringbootservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the {@link RestClient} used to call OpenRouter and enables our
 * own configuration property classes.
 * <p>
 * Connect timeout is fixed at 5s (network handshake should never take that
 * long under healthy conditions); read timeout comes from
 * {@code openrouter.request-timeout} since model generation latency varies
 * with the chosen model.
 */
@Configuration
@EnableConfigurationProperties({OpenRouterProperties.class, PersonalityProperties.class})
public class OpenRouterClientConfig {

    @Bean
    public RestClient openRouterRestClient(RestClient.Builder builder, OpenRouterProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.requestTimeout());

        RestClient.Builder b = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (props.httpReferer() != null && !props.httpReferer().isBlank()) {
            b = b.defaultHeader("HTTP-Referer", props.httpReferer());
        }
        if (props.appTitle() != null && !props.appTitle().isBlank()) {
            b = b.defaultHeader("X-Title", props.appTitle());
        }
        return b.build();
    }
}
