package com.unispeaking.infrastructure.ai.qiniu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.infrastructure.config.QiniuRealtimeProperties;
import java.io.ByteArrayOutputStream;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class QiniuRtiClientTest {

	@Test
	void createsSessionWithControlPlanePayloadAndParsesRtcEndpoint() throws Exception {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new ResponseData(
						200,
						"{\"session_id\":\"rti-1\",\"trace_id\":\"trace-1\","
								+ "\"client_endpoint\":{\"type\":\"platform_rtc\","
								+ "\"url\":\"/rtc/rti-1\",\"access_token\":\"rtc-token\","
								+ "\"expires_at_ms\":1786424400000}}"));
			QiniuRtiClient client = client(httpClient);

		QiniuRtiClient.CreatedSession session = client.createSession(command(), "voice-katerina");

		HttpRequest request = httpClient.requests.getFirst();
		JsonNode payload = new ObjectMapper().readTree(readBody(request));
		assertEquals("https://rti.example.test/rtic/v1/realtime/sessions", request.uri().toString());
		assertEquals("Bearer qiniu-api-key", request.headers().firstValue("Authorization").orElseThrow());
		assertEquals("app-1", payload.path("app_id").asString());
		assertEquals("user-1", payload.path("user_id").asString());
		assertEquals("local-session-1", payload.path("client_id").asString());
		assertEquals("voice-katerina", payload.path("voice_profile").asString());
		assertEquals("platform_rtc", payload.path("client_transport").asString());
		assertEquals("rti-1", session.sessionId());
		assertEquals(URI.create("https://rti.example.test/rtc/rti-1"), session.rtcEndpoint());
		assertEquals("rtc-token", session.accessToken());
	}

	@Test
	void exchangesOfferAndStopsSessionWithExpectedHeaders() throws Exception {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new ResponseData(
						200,
						"{\"session_id\":\"rti-1\",\"client_endpoint\":{"
								+ "\"type\":\"platform_rtc\",\"url\":\"https://rtc.example.test/signal\","
								+ "\"access_token\":\"rtc-token\"}}"),
				new ResponseData(200, "{\"type\":\"answer\",\"sdp\":\"answer-sdp\"}"),
				new ResponseData(204, ""));
			QiniuRtiClient client = client(httpClient);

		QiniuRtiClient.CreatedSession session = client.createSession(command(), "voice-katerina");
		assertEquals("answer-sdp", client.exchangeSdp(session, "offer-sdp"));
		client.stopSession("rti-1", "client_completed");

		HttpRequest signalingRequest = httpClient.requests.get(1);
		assertEquals("https://rtc.example.test/signal", signalingRequest.uri().toString());
		assertEquals("Bearer rtc-token", signalingRequest.headers().firstValue("Authorization").orElseThrow());
		JsonNode signalingPayload = new ObjectMapper().readTree(readBody(signalingRequest));
		assertEquals("offer", signalingPayload.path("type").asString());
		assertEquals("offer-sdp", signalingPayload.path("sdp").asString());
		HttpRequest stopRequest = httpClient.requests.get(2);
		assertEquals("https://rti.example.test/rtic/v1/realtime/sessions/rti-1/stop", stopRequest.uri().toString());
		assertEquals("Bearer qiniu-api-key", stopRequest.headers().firstValue("Authorization").orElseThrow());
	}

	@Test
	void rejectsNonRtcEndpointFromControlPlane() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new ResponseData(
						200,
						"{\"session_id\":\"rti-1\",\"client_endpoint\":{"
								+ "\"type\":\"platform_wss\",\"url\":\"https://rtc.example.test/signal\","
								+ "\"access_token\":\"rtc-token\"}}"));
		QiniuRtiClient client = client(httpClient);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> client.createSession(command(), "voice-katerina"));

		assertEquals("QINIU_RTI_RESPONSE_INVALID", exception.code());
	}

	private QiniuRtiClient client(RecordingHttpClient httpClient) {
		return new QiniuRtiClient(httpClient, new ObjectMapper(), properties());
	}

	private QiniuRealtimeProperties properties() {
		return new QiniuRealtimeProperties(
				"https://rti.example.test",
				"qiniu-api-key",
				"app-1",
				"qwen-profile",
				"coach-profile",
				"language-learning",
				Map.of("katerina", "voice-katerina"),
				Duration.ofSeconds(1),
				Duration.ofSeconds(1),
				1024 * 1024);
	}

	private RealtimeConnectCommand command() {
		return new RealtimeConnectCommand(
				"local-session-1",
				"user-1",
				"qiniu/qwen3.5-omni-plus-realtime",
				"Katerina",
				"offer-sdp");
	}

	private String readBody(HttpRequest request) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		CompletableFuture<Void> completed = new CompletableFuture<>();
		request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				subscription.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(ByteBuffer item) {
				byte[] chunk = new byte[item.remaining()];
				item.get(chunk);
				bytes.writeBytes(chunk);
			}

			@Override
			public void onError(Throwable throwable) {
				completed.completeExceptionally(throwable);
			}

			@Override
			public void onComplete() {
				completed.complete(null);
			}
		});
		completed.join();
		return bytes.toString(StandardCharsets.UTF_8);
	}

	private static final class RecordingHttpClient extends HttpClient {

		private final ArrayDeque<ResponseData> responses;
		private final List<HttpRequest> requests = new ArrayList<>();

		private RecordingHttpClient(ResponseData... responses) {
			this.responses = new ArrayDeque<>(List.of(responses));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler)
				throws IOException {
			requests.add(request);
			ResponseData response = responses.removeFirst();
			if (response.ioError()) {
				throw new IOException("simulated network failure");
			}
			return (HttpResponse<T>) new RecordedHttpResponse(
					request,
					response.statusCode(),
					response.body());
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler) {
			try {
				return CompletableFuture.completedFuture(send(request, responseBodyHandler));
			}
			catch (IOException exception) {
				return CompletableFuture.failedFuture(exception);
			}
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler,
				HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
			return sendAsync(request, responseBodyHandler);
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

	private record ResponseData(int statusCode, String body, boolean ioError) {
		private ResponseData(int statusCode, String body) {
			this(statusCode, body, false);
		}
	}

	private static final class RecordedHttpResponse implements HttpResponse<String> {

		private final HttpRequest request;
		private final int statusCode;
		private final String body;

		private RecordedHttpResponse(HttpRequest request, int statusCode, String body) {
			this.request = request;
			this.statusCode = statusCode;
			this.body = body;
		}

		@Override public int statusCode() { return statusCode; }
		@Override public HttpRequest request() { return request; }
		@Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (key, value) -> true); }
		@Override public String body() { return body; }
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return request.uri(); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}
}
