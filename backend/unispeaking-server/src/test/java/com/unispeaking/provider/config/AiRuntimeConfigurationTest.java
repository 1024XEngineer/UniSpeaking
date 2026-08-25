package com.unispeaking.provider.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiRuntimeConfigurationTest {
    @Test
    void normalizesNullMapsAndResolvesCustomDefaultAndMissingRoutes() {
        AiRuntimeConfiguration empty = new AiRuntimeConfiguration(null, null, null, false);
        assertEquals(Map.of(), empty.providers());
        assertEquals(Map.of(), empty.models());
        assertEquals(List.of(), empty.route(null, AiCapability.LLM));

        Map<String, Map<AiCapability, List<String>>> routes = Map.of(
                "default", Map.of(AiCapability.LLM, List.of("default-model")),
                "premium", Map.of(AiCapability.TTS, List.of("premium-tts")));
        AiRuntimeConfiguration configuration = new AiRuntimeConfiguration(Map.of(), Map.of(), routes, true);
        assertEquals(List.of("default-model"), configuration.route(" ", AiCapability.LLM));
        assertEquals(List.of("premium-tts"), configuration.route(" premium ", AiCapability.TTS));
        assertEquals(List.of("default-model"), configuration.route("unknown", AiCapability.LLM));
        assertEquals(List.of(), configuration.route("premium", AiCapability.LLM));
        assertThrows(UnsupportedOperationException.class,
                () -> configuration.routes().put("x", Map.of()));
    }
}
