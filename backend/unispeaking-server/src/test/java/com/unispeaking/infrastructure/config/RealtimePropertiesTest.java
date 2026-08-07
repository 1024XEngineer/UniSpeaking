package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.realtime.RealtimeCredentialIssuer;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RealtimePropertiesTest {

	@Test
	void derivesBailianWebRtcSdpExchangeUrlFromWorkspaceAndModel() {
		var properties = realtimeProperties("", "workspace-123", "qwen3.5-omni-plus-realtime");
		properties.validate();

		assertEquals(
				"https://workspace-123.cn-beijing.maas.aliyuncs.com/api/v1/webrtc/realtime?model=qwen3.5-omni-plus-realtime",
				properties.getWebRtcSdpExchangeUrl());
	}

	@Test
	void issuesTemporaryBearerCredentialFromDashScope() {
		var httpClient = new RecordingHttpClient(200, """
				{"token":"temporary-token","expires_at":1893456000}
				""");
		var properties = realtimeProperties(
				"  parent-key  ",
				"",
				"",
				"https://dashscope.example.com/api/v1/tokens/",
				Duration.ofSeconds(20));
		properties.validate();

		var service = new RealtimeCredentialIssuer(
				httpClient,
				new ObjectMapper(),
				properties);
		var credential = service.issue(ProviderType.QWEN);

		assertEquals("POST", httpClient.request.method());
		assertEquals("expire_in_seconds=300", httpClient.request.uri().getQuery());
		assertEquals("Bearer parent-key", httpClient.request.headers().firstValue("Authorization").orElseThrow());
		assertEquals("temporary-token", credential.bearerToken());
		assertEquals(Instant.ofEpochSecond(1893456000), credential.expiresAt());
		assertTrue(credential.temporary());
	}

	@Test
	void derivesTemporaryBearerCredentialExpirationWhenResponseOmitsExpiresAt() {
		var httpClient = new RecordingHttpClient(200, """
				{"token":"temporary-token"}
				""");
		var properties = realtimeProperties(
				"parent-key",
				"",
				"",
				"https://dashscope.example.com/api/v1/tokens",
				Duration.ofSeconds(20));
		properties.validate();

		var service = new RealtimeCredentialIssuer(
				httpClient,
				new ObjectMapper(),
				properties);
		Instant before = Instant.now().plusSeconds(295);
		var credential = service.issue(ProviderType.QWEN);
		Instant after = Instant.now().plusSeconds(305);

		assertFalse(credential.expiresAt().isBefore(before));
		assertFalse(credential.expiresAt().isAfter(after));
	}

	@Test
	void rejectsInvalidNonSecretSettingsAtStartup() {
		var properties = realtimeProperties("", "", "", "", Duration.ZERO);

		assertThrows(IllegalStateException.class, properties::validate);
	}

	@Test
	void rejectsMalformedTemporaryKeyResponses() {
		var httpClient = new RecordingHttpClient(200, """
				{"expires_at":1893456000}
				""");
		var properties = realtimeProperties(
				"parent-key",
				"",
				"",
				"https://dashscope.example.com/api/v1/tokens",
				Duration.ofSeconds(20));
		properties.validate();

		var service = new RealtimeCredentialIssuer(
				httpClient,
				new ObjectMapper(),
				properties);
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.issue(ProviderType.QWEN));

		assertEquals("QWEN_TEMPORARY_KEY_INVALID", exception.code());
	}

	private RealtimeProperties realtimeProperties(
			String apiKey, String workspaceId, String model) {
		return realtimeProperties(
				apiKey,
				workspaceId,
				model,
				"https://dashscope.aliyuncs.com/api/v1/tokens",
				Duration.ofSeconds(20));
	}

	private RealtimeProperties realtimeProperties(
			String apiKey,
			String workspaceId,
			String model,
			Duration readTimeout) {
		return realtimeProperties(
				apiKey,
				workspaceId,
				model,
				"https://dashscope.aliyuncs.com/api/v1/tokens",
				readTimeout);
	}

	private RealtimeProperties realtimeProperties(
			String apiKey,
			String workspaceId,
			String model,
			String temporaryKeyEndpoint,
			Duration readTimeout) {
		return new RealtimeProperties(
				apiKey,
				workspaceId,
				model,
				"cn-beijing",
				temporaryKeyEndpoint,
				300,
				Duration.ofSeconds(10),
				readTimeout,
				1_048_576);
	}

	private static final class RecordingHttpClient extends HttpClient {

		private final int statusCode;
		private final String body;
		private HttpRequest request;

		private RecordingHttpClient(int statusCode, String body) {
			this.statusCode = statusCode;
			this.body = body;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler) {
			this.request = request;
			return (HttpResponse<T>) new StringHttpResponse(request, statusCode, body);
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler,
				HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
			throw new UnsupportedOperationException();
		}

		@Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
		@Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
		@Override public Redirect followRedirects() { return Redirect.NEVER; }
		@Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
		@Override public SSLContext sslContext() { return null; }
		@Override public SSLParameters sslParameters() { return new SSLParameters(); }
		@Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
		@Override public Version version() { return Version.HTTP_1_1; }
		@Override public Optional<Executor> executor() { return Optional.empty(); }
	}

	private record StringHttpResponse(HttpRequest request, int statusCode, String body)
			implements HttpResponse<String> {

		@Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() {
			return HttpHeaders.of(Map.of(), (name, value) -> true);
		}
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return request.uri(); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}
}
