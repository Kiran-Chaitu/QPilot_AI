package com.testforge.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 auto-configures a Jackson 3 ({@code tools.jackson.databind.ObjectMapper}) bean
 * for HTTP message conversion. Our AI/JSON parsing code targets the classic Jackson 2 API
 * ({@code com.fasterxml.jackson.databind}, still pulled in transitively by jjwt/swagger-parser),
 * so we register our own bean here rather than depending on Spring's internal Jackson 3 instance.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
