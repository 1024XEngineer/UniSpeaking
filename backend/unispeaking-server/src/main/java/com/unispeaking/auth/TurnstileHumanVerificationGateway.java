package com.unispeaking.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "unispeaking.auth.captcha.provider", havingValue = "turnstile")
public final class TurnstileHumanVerificationGateway implements HumanVerificationGateway {

    private static final URI VERIFY_ENDPOINT = URI.create(
            "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    private final HttpClient client;
    private final JsonMapper objectMapper;
    private final String secretKey;

    public TurnstileHumanVerificationGateway(
            JsonMapper objectMapper,
            @Value("${unispeaking.auth.captcha.turnstile-secret-key:}") String secretKey) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
    }

    @Override
    public boolean verify(String token) {
        if (secretKey.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        try {
            var form = "secret=" + encode(secretKey) + "&response=" + encode(token);
            var request = HttpRequest.newBuilder(VERIFY_ENDPOINT)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return false;
            }
            var result = objectMapper.readValue(response.body(), Map.class);
            return Boolean.TRUE.equals(result.get("success"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
