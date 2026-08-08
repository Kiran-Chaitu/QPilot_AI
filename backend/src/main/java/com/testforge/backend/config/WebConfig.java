package com.testforge.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Web-layer infrastructure beans.
 *
 * <p>CORS is deliberately NOT configured here. It used to be declared twice — once via
 * {@code WebMvcConfigurer#addCorsMappings} and once via Spring Security's
 * {@code CorsConfigurationSource} — which meant the Security filter chain's copy always won for
 * {@code /api/**} while the MVC copy silently diverged. {@link SecurityConfig} is now the single
 * source of truth, driven by {@code app.cors.allowed-origins}.
 */
@Configuration
public class WebConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
