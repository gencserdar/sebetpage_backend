package com.serdar.gateway.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FrontendUrlConfig {

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;

    @PostConstruct
    void validate() {
        requireHttpBaseUrl(frontendBaseUrl, "frontend.base-url");
    }

    static void requireHttpBaseUrl(String url, String name) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalStateException(name + " must start with http:// or https://");
        }
        if (trimmed.endsWith("/")) {
            throw new IllegalStateException(name + " must not end with a trailing slash");
        }
    }
}
