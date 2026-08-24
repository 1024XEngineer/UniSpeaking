package com.unispeaking.infrastructure.ai.qiniu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.config.QiniuRealtimeProperties;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QiniuRealtimeProviderTest {

	@Test
	void createsControlSessionExchangesJsonSdpAndStopsWithThePermanentKey()
			throws IOException, InterruptedException {
		RecordingHttpClient client = new RecordingHttpClient(
				response(200, profiles()),
				response(201, """
						{"session_id":"rti-session-1","trace_id":"trace-1",
						 "client_endpoint":{"url":"/v1/realtime/sessions/rti-session-1/rtc",
						 "access_token":"short-token","expires_at_ms":1786500000000}}
						"""),
				response(200, "{\"type\":\"answer\",\"sdp\":\"answer-sdp\"}"),
				response(200, "{}"));
		QiniuRealtimeProvider provider = provider(client, "server-api-key");

		var result = provider.connect(command(), null);
		provider.stopSession(result.providerSessionId(), null, "client_completed");

		assertEquals("rti-session-1", result.providerSessionId());
		assertEquals(ProviderType.QINIU, result.providerType());
		assertEquals("qwen3.5-omni-plus-realtime", result.modelId());
		assertEquals("Tina", result.voiceId());
		assertEquals("trace-1", result.traceId());
		assertEquals("answer-sdp", result.answerSdp());
		assertEquals(Instant.ofEpochMilli(1786500000000L), result.credentialExpiresAt());

		assertEquals(List.of(
				"/rtic/v1/realtime/profiles",
				"/rtic/v1/realtime/sessions",
				"/v1/realtime/sessions/rti-session-1/rtc",
				"/rtic/v1/realtime/sessions/rti-session-1/stop"),
				client.requests.stream().map(request -> request.uri().getPath()).toList());
		assertEquals("Bearer server-api-key", authorization(client.requests.get(0)));
		assertEquals("Bearer server-api-key", authorization(client.requests.get(1)));
		assertEquals("Bearer short-token", authorization(client.requests.get(2)));
		assertEquals("Bearer server-api-key", authorization(client.requests.get(3)));
		String createBody = readBody(client.requests.get(1));
		assertTrue(createBody.contains("\"app_id\":\"unispeaking_001\""), createBody);
		assertTrue(createBody.contains("\"voice_profile\":\"Tina\""), createBody);
		assertFalse(createBody.contains("server-api-key"), createBody);
		assertFalse(createBody.contains("short-token"), createBody);
		assertTrue(readBody(client.requests.get(2)).contains("\"sdp\":\"offer-sdp\""));
	}

	@Test
	void stopsTheCreatedSessionWhenSdpExchangeFails() {
		RecordingHttpClient client = new RecordingHttpClient(
				response(200, profiles()),
				response(201, """
						{"session_id":"rti-session-2","trace_id":"trace-2",
						 "client_endpoint":{"url":"/v1/realtime/sessions/rti-session-2/rtc",
						 "access_token":"short-token"}}
						"""),
				response(502, "{\"code\":\"upstream_failed\",\"message\":\"unavailable\"}"),
				response(200, "{}"));
		QiniuRealtimeProvider provider = provider(client, "server-api-key");

		BusinessException failure = assertThrows(
				BusinessException.class,
				() -> provider.connect(command(), null));

		assertEquals("QINIU_UPSTREAM_FAILED", failure.code());
		assertEquals("/rtic/v1/realtime/sessions/rti-session-2/stop",
				client.requests.getLast().uri().getPath());
	}

	@Test
	void mapsLegacyVoiceAndReturnsTheResolvedQiniuVoice() throws Exception {
		RecordingHttpClient client = successfulConnectionClient("rti-session-voice");
		QiniuRealtimeProvider provider = provider(client, "server-api-key");

		var result = provider.connect(command("Harvey"), null);

		assertEquals("Ethan", result.voiceId());
		assertTrue(readBody(client.requests.get(1)).contains("\"voice_profile\":\"Ethan\""));
	}

	@Test
	void mapsArthurVoiceToEthan() throws Exception {
		RecordingHttpClient client = successfulConnectionClient("rti-session-arthur");
		QiniuRealtimeProvider provider = provider(client, "server-api-key");

		var result = provider.connect(command("Dolce"), null);

		assertEquals("Ethan", result.voiceId());
		assertTrue(readBody(client.requests.get(1)).contains("\"voice_profile\":\"Ethan\""));
	}

	@Test
	void acceptsAProfileNativeVoiceWithoutMapping() throws Exception {
		RecordingHttpClient client = successfulConnectionClient("rti-session-native-voice");
		QiniuRealtimeProvider provider = provider(client, "server-api-key");

		var result = provider.connect(command("Serena"), null);

		assertEquals("Serena", result.voiceId());
		assertTrue(readBody(client.requests.get(1)).contains("\"voice_profile\":\"Serena\""));
	}

	@Test
	void rejectsMissingCredentialsAndUnavailableProfilesBeforeCreatingASession() {
		RecordingHttpClient missingKeyClient = new RecordingHttpClient();
		BusinessException missingKey = assertThrows(
				BusinessException.class,
				() -> provider(missingKeyClient, "").connect(command(), null));
		assertEquals("QINIU_CREDENTIAL_MISSING", missingKey.code());
		assertTrue(missingKeyClient.requests.isEmpty());

		RecordingHttpClient unavailableClient = new RecordingHttpClient(
				response(200, "{\"profiles\":[]}"));
		BusinessException unavailable = assertThrows(
				BusinessException.class,
				() -> provider(unavailableClient, "server-api-key").connect(command(), null));
		assertEquals("QINIU_PROFILE_UNAVAILABLE", unavailable.code());
		assertEquals(1, unavailableClient.requests.size());

		RecordingHttpClient unsupportedVoiceClient = new RecordingHttpClient(
				response(200, profiles()));
		BusinessException unsupportedVoice = assertThrows(
				BusinessException.class,
				() -> provider(unsupportedVoiceClient, "server-api-key")
						.connect(command("LegacyVoice"), null));
		assertEquals("QINIU_PROFILE_UNAVAILABLE", unsupportedVoice.code());
		assertEquals(1, unsupportedVoiceClient.requests.size());
	}

	@Test
	void rejectsContextlessExchangeAndInvalidConnectCommandsBeforeHttp() {
		QiniuRealtimeProvider provider = provider(new RecordingHttpClient(), "server-api-key");

		assertEquals("QINIU_REALTIME_CONTEXT_REQUIRED", assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						"qwen3.5-omni-plus-realtime", "offer-sdp", "token")).getMessage() != null
						? "QINIU_REALTIME_CONTEXT_REQUIRED" : "");
		assertEquals("INVALID_REALTIME_COMMAND", assertThrows(
				BusinessException.class,
				() -> provider.connect(null, null)).code());
		assertEquals("INVALID_SDP", assertThrows(
				BusinessException.class,
				() -> provider.connect(commandWithOffer(" "), null)).code());
		assertEquals("QINIU_REALTIME_MODEL_NOT_SUPPORTED", assertThrows(
				BusinessException.class,
				() -> provider.connect(commandWithModel("unsupported-model"), null)).code());
	}

	@Test
	void mapsProfileAndSessionResponseFailuresWithoutLeakingCredentials() {
		RecordingHttpClient invalidProfiles = new RecordingHttpClient(
				response(200, "not-json"));
		assertEquals("QINIU_PROFILES_INVALID", assertThrows(
				BusinessException.class,
				() -> provider(invalidProfiles, "server-api-key").connect(command(), null)).code());

		RecordingHttpClient oversized = new RecordingHttpClient(
				response(200, profiles()),
				response(201, "x".repeat(1_048_577)));
		assertEquals("QINIU_RESPONSE_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> provider(oversized, "server-api-key").connect(command(), null)).code());

		RecordingHttpClient missingSession = new RecordingHttpClient(
				response(200, profiles()),
				response(201, "{\"client_endpoint\":{\"url\":\"/rtc\",\"access_token\":\"token\"}}"));
		assertEquals("QINIU_SESSION_ID_MISSING", assertThrows(
				BusinessException.class,
				() -> provider(missingSession, "server-api-key").connect(command(), null)).code());

		RecordingHttpClient invalidEndpoint = new RecordingHttpClient(
				response(200, profiles()),
				response(201, """
						{"session_id":"safe-session","client_endpoint":
						{"url":"http://evil.example/rtc","access_token":"token"}}
						"""),
				response(200, "{}"));
		assertEquals("QINIU_CLIENT_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> provider(invalidEndpoint, "server-api-key").connect(command(), null)).code());
	}

	@Test
	void mapsControlHttpStatusesAndStopSessionIdValidation() {
		RecordingHttpClient unauthorized = new RecordingHttpClient(
				response(401, "{" + "\"code\":\"bad_key\",\"message\":\"denied\"}"));
		BusinessException unauthorizedFailure = assertThrows(
				BusinessException.class,
				() -> provider(unauthorized, "server-api-key").connect(command(), null));
		assertEquals("QINIU_BAD_KEY", unauthorizedFailure.code());

		RecordingHttpClient badRequest = new RecordingHttpClient(
				response(200, profiles()),
				response(400, "not-json"));
		BusinessException badRequestFailure = assertThrows(
				BusinessException.class,
				() -> provider(badRequest, "server-api-key").connect(command(), null));
		assertEquals("QINIU_SESSION_CREATE_FAILED", badRequestFailure.code());

		RecordingHttpClient stopNotFound = new RecordingHttpClient(response(404, ""));
		provider(stopNotFound, "server-api-key").stopSession("session-1", null, null);
		assertEquals(1, stopNotFound.requests.size());

		RecordingHttpClient noStop = new RecordingHttpClient();
		provider(noStop, "server-api-key").stopSession("  ", null, " ");
		assertTrue(noStop.requests.isEmpty());
		assertEquals("QINIU_SESSION_ID_INVALID", assertThrows(
				BusinessException.class,
				() -> provider(noStop, "server-api-key").stopSession("bad/id", null, "done")).code());
	}

	@Test
		void coversNullAndBlankStopInputsAndNonRetryableStopFailures()
				throws IOException, InterruptedException {
		RecordingHttpClient noRequest = new RecordingHttpClient();
		provider(noRequest, "server-api-key").stopSession(null, null, "done");
		assertTrue(noRequest.requests.isEmpty());

		RecordingHttpClient badStop = new RecordingHttpClient(response(400, ""));
		BusinessException failure = assertThrows(
				BusinessException.class,
				() -> provider(badStop, "server-api-key")
						.stopSession("session-1", null, "  "));
		assertEquals("QINIU_SESSION_STOP_FAILED", failure.code());
		assertTrue(readBody(badStop.requests.getFirst()).contains("client_completed"));
	}

	@Test
		void coversProfilePredicateShortCircuitingAndCommandsWithoutOptionalFields()
				throws IOException, InterruptedException {
		RecordingHttpClient client = new RecordingHttpClient(
				response(200, ""),
				response(200, "{\"profiles\":["
						+ "{\"model_profile\":\"wrong\",\"role_profiles\":[],"
						+ "\"voice_profiles\":[],\"client_transports\":[]},"
						+ "{\"model_profile\":\"qwen3.5-omni-plus-realtime\","
						+ "\"role_profiles\":[\"wrong-role\"],\"voice_profiles\":[],"
						+ "\"client_transports\":[]},"
						+ "{\"model_profile\":\"qwen3.5-omni-plus-realtime\","
						+ "\"role_profiles\":[\"default_assistant\"],"
						+ "\"voice_profiles\":[\"Tina\"],"
						+ "\"client_transports\":[\"platform_rtc\"]}]}") );
		BusinessException unavailable = assertThrows(
				BusinessException.class,
				() -> provider(client, "server-api-key").connect(command(), null));
		assertEquals("QINIU_PROFILE_UNAVAILABLE", unavailable.code());

		RecordingHttpClient optional = successfulConnectionClient("optional-fields");
		RealtimeConnectCommand command = new RealtimeConnectCommand(
				null,
				"offer-sdp",
				"user-1",
				"local-session-1",
				"freechat-scene-1",
				null,
				"Tina");
		var result = provider(optional, "server-api-key").connect(command, null);
		assertEquals("optional-fields", result.providerSessionId());
		assertFalse(readBody(optional.requests.get(1)).contains("scenario"));
	}

	@Test
	void mapsMissingQiniuResponseFieldsAndNullBodies() {
		RecordingHttpClient nullProfiles = new RecordingHttpClient(response(200, null));
		assertEquals("QINIU_PROFILE_UNAVAILABLE", assertThrows(
				BusinessException.class,
				() -> provider(nullProfiles, "server-api-key").connect(command(), null)).code());

		RecordingHttpClient missingEndpointUrl = new RecordingHttpClient(
				response(200, profiles()),
				response(201, "{\"session_id\":\"safe-session\","
						+ "\"client_endpoint\":{\"access_token\":\"token\"}}"));
		assertEquals("QINIU_CLIENT_ENDPOINT_MISSING", assertThrows(
				BusinessException.class,
				() -> provider(missingEndpointUrl, "server-api-key").connect(command(), null)).code());

		RecordingHttpClient missingToken = new RecordingHttpClient(
				response(200, profiles()),
				response(201, "{\"session_id\":\"safe-session\","
						+ "\"client_endpoint\":{\"url\":\"/rtc\"}}"));
		assertEquals("QINIU_CLIENT_TOKEN_MISSING", assertThrows(
				BusinessException.class,
				() -> provider(missingToken, "server-api-key").connect(command(), null)).code());

		RecordingHttpClient missingAnswer = new RecordingHttpClient(
				response(200, profiles()),
				response(201, "{\"session_id\":\"safe-session\","
						+ "\"client_endpoint\":{\"url\":\"/rtc\","
						+ "\"access_token\":\"token\"}}"),
				response(200, "{}"),
				response(200, "{}"));
		assertEquals("QINIU_ANSWER_SDP_MISSING", assertThrows(
				BusinessException.class,
				() -> provider(missingAnswer, "server-api-key").connect(command(), null)).code());
	}

	@Test
	void mapsQiniuIoAndInterruptedRequests() throws Exception {
		HttpClient ioClient = mock(HttpClient.class);
		doThrow(new IOException("network")).when(ioClient)
				.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		QiniuRealtimeProperties properties = properties("server-api-key");
		assertEquals("QINIU_PROFILES_IO_ERROR", assertThrows(
				BusinessException.class,
				() -> new QiniuRealtimeProvider(ioClient, new ObjectMapper(), properties)
						.connect(command(), null)).code());

		HttpClient interruptedClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled")).when(interruptedClient)
				.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		Thread.interrupted();
		try {
			assertEquals("QINIU_REQUEST_INTERRUPTED", assertThrows(
					BusinessException.class,
					() -> new QiniuRealtimeProvider(
							interruptedClient, new ObjectMapper(), properties)
							.connect(command(), null)).code());
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
	}

	private RecordingHttpClient successfulConnectionClient(String sessionId) {
		return new RecordingHttpClient(
				response(200, profiles()),
				response(201, """
						{"session_id":"%s","trace_id":"trace-voice",
						 "client_endpoint":{"url":"/v1/realtime/sessions/%s/rtc",
						 "access_token":"short-token"}}
						""".formatted(sessionId, sessionId)),
				response(200, "{\"type\":\"answer\",\"sdp\":\"answer-sdp\"}"));
	}

	private QiniuRealtimeProvider provider(RecordingHttpClient client, String apiKey) {
		QiniuRealtimeProperties properties = properties(apiKey);
		properties.validate();
		return new QiniuRealtimeProvider(client, new ObjectMapper(), properties);
	}

	private QiniuRealtimeProperties properties(String apiKey) {
		return new QiniuRealtimeProperties(
				"https://miku-rtic.qiniuapi.com",
				apiKey,
				"unispeaking_001",
					"qwen3.5-omni-plus-realtime",
					"default_assistant",
					"Tina",
					Map.of(
							"Harvey", "Ethan",
							"Dolce", "Ethan",
							"Mione", "Cindy"),
					"platform_rtc",
				"cn-east",
				Duration.ofSeconds(20),
				1_048_576);
	}

	private RealtimeConnectCommand command() {
		return command("Tina");
	}

	private RealtimeConnectCommand command(String voiceId) {
		return new RealtimeConnectCommand(
				"qwen3.5-omni-plus-realtime",
				"offer-sdp",
				"user-1",
				"local-session-1",
				"freechat-scene-1",
				SceneType.FREE_CHAT,
				voiceId);
	}

	private RealtimeConnectCommand commandWithOffer(String offerSdp) {
		return new RealtimeConnectCommand(
				"qwen3.5-omni-plus-realtime", offerSdp, "user-1", "local-session-1",
				"freechat-scene-1", SceneType.FREE_CHAT, "Tina");
	}

	private RealtimeConnectCommand commandWithModel(String modelId) {
		return new RealtimeConnectCommand(
				modelId, "offer-sdp", "user-1", "local-session-1",
				"freechat-scene-1", SceneType.FREE_CHAT, "Tina");
	}

	private String profiles() {
		return """
				{"profiles":[{"model_profile":"qwen3.5-omni-plus-realtime",
				"role_profiles":["default_assistant","english_coach_default"],
					"voice_profiles":["default_voice","Tina","Cherry","Serena","Ethan","Chelsie","Cindy"],
				"client_transports":["platform_rtc","platform_wss"]}]}
				""";
	}

	private String authorization(HttpRequest request) {
		return request.headers().firstValue("Authorization").orElseThrow();
	}

	private static StubResponse response(int status, String body) {
		return new StubResponse(status, body);
	}

	private static String readBody(HttpRequest request) {
		var subscriber = new BodySubscriber();
		request.bodyPublisher().orElseThrow().subscribe(subscriber);
		return subscriber.result.join();
	}

	private static final class RecordingHttpClient extends HttpClient {
		private final Queue<StubResponse> responses = new ArrayDeque<>();
		private final List<HttpRequest> requests = new ArrayList<>();

		private RecordingHttpClient(StubResponse... responses) {
			this.responses.addAll(List.of(responses));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
				throws IOException {
			requests.add(request);
			StubResponse response = responses.poll();
			if (response == null) throw new IOException("No queued response");
			return (HttpResponse<T>) response;
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request,
				HttpResponse.BodyHandler<T> handler,
				HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
			throw new UnsupportedOperationException();
		}

		@Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
		@Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
		@Override public Redirect followRedirects() { return Redirect.NEVER; }
		@Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
		@Override public SSLContext sslContext() { return null; }
		@Override public SSLParameters sslParameters() { return null; }
		@Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
		@Override public Version version() { return Version.HTTP_1_1; }
		@Override public Optional<Executor> executor() { return Optional.empty(); }
	}

	private record StubResponse(int statusCode, String body) implements HttpResponse<String> {
		@Override public HttpRequest request() { return null; }
		@Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return URI.create("https://miku-rtic.qiniuapi.com"); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}

	private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {
		private final CompletableFuture<String> result = new CompletableFuture<>();
		private final StringBuilder body = new StringBuilder();

		@Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
		@Override public void onNext(ByteBuffer item) {
			byte[] bytes = new byte[item.remaining()];
			item.get(bytes);
			body.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
		}
		@Override public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
		@Override public void onComplete() { result.complete(body.toString()); }
	}
}
