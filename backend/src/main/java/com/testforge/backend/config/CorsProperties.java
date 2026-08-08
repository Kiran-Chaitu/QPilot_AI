package com.testforge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Comma-separated allowed origin <em>patterns</em>, e.g. {@code http://localhost:[*]}.
     *
     * <p>Interpreted by Spring's {@code setAllowedOriginPatterns}, so {@code [*]} is a port wildcard.
     * Defaults to loopback on any port, which covers Vite's dev (5173) and preview (4173) servers without
     * opening the API to arbitrary hosts — important because these origins are used together with
     * credentialed requests.
     */
    private String allowedOrigins = "http://localhost:[*],http://127.0.0.1:[*]";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
