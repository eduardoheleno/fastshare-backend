package dev.fastshare;

import dev.fastshare.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsConfigTest {
    @Test
    void appliesAllowedOriginsToHttpApi() {
        var config = new CorsConfig(new String[]{"https://fastshare.eduardodev.app.br"});
        var registry = new TestCorsRegistry();
        config.addCorsMappings(registry);
        var cors = registry.configurations().get("/api/**");

        assertEquals(List.of("https://fastshare.eduardodev.app.br"), cors.getAllowedOrigins());
        assertEquals(List.of("GET", "POST"), cors.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type"), cors.getAllowedHeaders());
    }

    private static class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
