package com.letchat.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void allowsBothLocalDevelopmentOrigins() {
        CorsConfiguration configuration = new CorsConfig()
                .corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/account/checkCode"));

        assertTrue(configuration.getAllowedOriginPatterns().contains("http://localhost:*"));
        assertTrue(configuration.getAllowedOriginPatterns().contains("http://127.0.0.1:*"));
    }
}
