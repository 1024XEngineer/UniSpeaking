package com.unispeaking.infrastructure.ai.aliyun;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.MeteredProviderException;
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
import tools.jackson.databind.ObjectMapper;

class AliyunTtsProviderTest {

	@Test
	void sendsConfiguredFormatAndSampleRateAndDownloadsAudio() {
		byte[] audio = new byte[] {1, 2, 3, 4};
		String audioUrl =
				"http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio%2Bfile.mp3?sig=a%2Fb";
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("{\"request_id\":\"body-request\",\"output\":{\"audio\":{\"url\":\""
								+ audioUrl + "\"}}}"),
						Map.of("x-request-id", List.of("header-request"))),
				new QueuedResponse(200, audio));
		AliyunTtsProvider provider = provider(client, "dashscope-key", "pcm", 16_000, 128);

		var measured = provider.generateSpeechAudioMeasured("  hello there  ", null);

		assertArrayEquals(audio, measured.response());
		assertEquals("body-request", measured.providerRequestId());
		assertEquals(2, client.requests.size());
		HttpRequest synthesisRequest = client.requests.getFirst();
		assertEquals("https", client.requests.get(1).uri().getScheme());
		assertEquals(
				"https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio%2Bfile.mp3?sig=a%2Fb",
				client.requests.get(1).uri().toString());
		String body = readBody(synthesisRequest);
		assertTrue(body.contains("\"format\":\"pcm\""));
		assertTrue(body.contains("\"sample_rate\":16000"));
		assertTrue(body.contains("\"language_hints\":[\"en\"]"));
		assertEquals("Bearer dashscope-key",
				synthesisRequest.headers().firstValue("Authorization").orElseThrow());
	}

	@Test
	void fallsBackToRequestIdHeaderWhenResponseBodyHasNoRequestId() {
		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("{\"output\":{\"audio\":{\"url\":\"" + url + "\"}}}"),
						Map.of("x-dashscope-request-id", List.of("header-request"))),
				new QueuedResponse(200, new byte[] {9}));

		var response = provider(client, "key", "wav", 24_000, 128)
				.generateSpeechAudioMeasured("hello", null);

		assertEquals("header-request", response.providerRequestId());
		assertEquals(5, response.usage().inputCharacters());
	}

	@Test
	void rejectsInvalidAliyunEndpointsBeforeSendingCredentials() {
		List<URI> invalidEndpoints = List.of(
				URI.create("http://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
				URI.create("https://evil.example/api/v1/services/audio/tts/SpeechSynthesizer"),
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com:443/api/v1/services/audio/tts/SpeechSynthesizer"),
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer?debug=true"),
				URI.create("https://user:pass@workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"));

		for (URI endpoint : invalidEndpoints) {
			RecordingHttpClient client = new RecordingHttpClient();
			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> provider(client, "secret", "wav", 24_000, 128, endpoint)
							.generateSpeechAudio("hello", null));

			assertEquals("ALIYUN_TTS_ENDPOINT_INVALID", exception.code());
			assertTrue(client.requests.isEmpty());
		}
	}

	@Test
	void rejectsMalformedAndUntrustedAudioUrls() {
		assertAudioResponseError("{\"output\":{\"audio\":{}}}", "ALIYUN_TTS_AUDIO_URL_MISSING");
		assertAudioResponseError("{\"output\":{\"audio\":{\"url\":\"not a uri\"}}}",
				"ALIYUN_TTS_AUDIO_URL_INVALID");
		assertAudioResponseError(
				"{\"output\":{\"audio\":{\"url\":\"https://evil.example/audio.wav\"}}}",
				"ALIYUN_TTS_AUDIO_URL_UNTRUSTED");
	}

	@Test
	void mapsHttpAndMalformedResponsesToRetryableBusinessErrors() {
		BusinessException httpError = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(new QueuedResponse(503, utf8("busy"))), "key")
						.generateSpeechAudio("hello", null));
		assertEquals("ALIYUN_TTS_REQUEST_FAILED", httpError.code());

		BusinessException invalidJson = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(new QueuedResponse(200, utf8("not-json"))), "key")
						.generateSpeechAudio("hello", null));
		assertEquals("ALIYUN_TTS_RESPONSE_INVALID", invalidJson.code());
	}

	@Test
	void mapsEmptyAndOversizedAudioResponses() {
		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		BusinessException empty = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(
						new QueuedResponse(200, utf8(audioResponse(url))),
						new QueuedResponse(200, new byte[0])) , "key")
						.generateSpeechAudio("hello", null));
		assertEquals("ALIYUN_TTS_AUDIO_EMPTY", empty.code());

			RuntimeException oversized = assertThrows(
					RuntimeException.class,
					() -> provider(new RecordingHttpClient(
							new QueuedResponse(200, utf8(audioResponse(url))),
							new QueuedResponse(200, new byte[] {1, 2, 3})), "key", "wav", 24_000, 2)
							.generateSpeechAudio("hello", null));
		assertTrue(oversized instanceof BusinessException
				|| oversized.getCause() instanceof BusinessException);
	}

	@Test
	void mapsTransportAndInterruptedFailuresAndRestoresInterruptStatus() throws Exception {
		BusinessException ioError = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(QueuedResponse.ioError()), "secret")
						.generateSpeechAudio("hello", null));
		assertEquals("ALIYUN_TTS_IO_ERROR", ioError.code());
		assertFalse(ioError.getMessage().contains("secret"));

		HttpClient interruptedClient = mock(HttpClient.class);
		try {
			doThrow(new InterruptedException("cancelled"))
					.when(interruptedClient)
					.send(any(HttpRequest.class),
							org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		}
		catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
		Thread.interrupted();
		BusinessException interrupted = assertThrows(
				BusinessException.class,
				() -> provider(interruptedClient, "key").generateSpeechAudio("hello", null));
		assertEquals("ALIYUN_TTS_INTERRUPTED", interrupted.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void validatesCredentialTextFormatAndSampleRate() {
		RecordingHttpClient client = new RecordingHttpClient();
		assertEquals("ALIYUN_TTS_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> provider(client, "").generateSpeechAudio("hello", null)).code());
		assertEquals("INVALID_TTS_TEXT", assertThrows(
				BusinessException.class,
				() -> provider(client, "key").generateSpeechAudio("  ", null)).code());
		assertEquals("TTS_TEXT_TOO_LONG", assertThrows(
				BusinessException.class,
				() -> provider(client, "key").generateSpeechAudio("x".repeat(5_001), null)).code());
		assertTrue(client.requests.isEmpty());

		assertThrows(IllegalArgumentException.class,
				() -> provider(client, "key", "ogg", 24_000, 128));
		assertThrows(IllegalArgumentException.class,
				() -> provider(client, "key", "wav", 11_025, 128));
	}

	@Test
	void wrapsPostRequestFailuresAsMeteredFailuresWhenRequestIdWasCaptured() {
		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		AliyunTtsProvider provider = provider(
				new RecordingHttpClient(
						new QueuedResponse(200, utf8("{\"request_id\":\"billable-1\",\"output\":{\"audio\":{\"url\":\""
								+ url + "\"}}}")),
						new QueuedResponse(502, utf8("download failed"))),
				"key");

		MeteredProviderException exception = assertThrows(
				MeteredProviderException.class,
				() -> provider.generateSpeechAudioMeasured("hello", null));

		assertEquals("ALIYUN_TTS_AUDIO_DOWNLOAD_FAILED", exception.code());
		assertEquals("billable-1", exception.providerRequestId());
		assertEquals(5, exception.usage().inputCharacters());
	}

	@Test
	void coversHeaderlessMeasuredSuccessAndAudioDownloadFailure() {
		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		RecordingHttpClient successClient = new RecordingHttpClient(
				new QueuedResponse(200, utf8(audioResponse(url))),
				new QueuedResponse(201, new byte[] {7}));
		var measured = provider(successClient, "key")
				.generateSpeechAudioMeasured("hello", null);
		assertEquals(null, measured.providerRequestId());
		assertEquals("NONE", measured.usage().source());

		RecordingHttpClient failedDownload = new RecordingHttpClient(
				new QueuedResponse(200, utf8(audioResponse(url))),
				new QueuedResponse(404, utf8("missing")));
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(failedDownload, "key")
						.generateSpeechAudio("hello", null));
		assertEquals("ALIYUN_TTS_AUDIO_DOWNLOAD_FAILED", exception.code());
	}

	@Test
	void rejectsGeneratedEndpointsWithUnsafeWorkspaceAndRegionComponents() {
		AliyunTtsProvider invalidWorkspace = new AliyunTtsProvider(
				new ObjectMapper(), "key", "bad.workspace", "cn-beijing", "model", "voice",
				"wav", 24_000, 1, 1, 128);
		assertEquals("ALIYUN_TTS_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> invalidWorkspace.generateSpeechAudio("hello", null)).code());

		AliyunTtsProvider invalidRegion = new AliyunTtsProvider(
				new ObjectMapper(), "key", "workspace", "cn_beijing", "model", "voice",
				"wav", 24_000, 1, 1, 128);
		assertEquals("ALIYUN_TTS_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> invalidRegion.generateSpeechAudio("hello", null)).code());
	}

	@Test
	void validatesSpringConstructorTimeoutsAndNullDependencies() {
		assertThrows(IllegalArgumentException.class, () -> new AliyunTtsProvider(
				new ObjectMapper(), "key", "workspace", "cn-beijing", "model", "voice",
				"wav", 24_000, 0, 1, 128));
		assertThrows(IllegalArgumentException.class, () -> new AliyunTtsProvider(
				new ObjectMapper(), "key", "workspace", "cn-beijing", "model", "voice",
				"wav", 24_000, 1, 0, 128));
		assertThrows(IllegalArgumentException.class, () -> new AliyunTtsProvider(
				null, new ObjectMapper(), "key", URI.create(
						"https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
				"model", "voice", "wav", 24_000, Duration.ofSeconds(1), 128));
	}

	private void assertAudioResponseError(String response, String expectedCode) {
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(200, utf8(response)));
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(client, "key").generateSpeechAudio("hello", null));
		assertEquals(expectedCode, exception.code());
		assertEquals(1, client.requests.size());
	}

	private AliyunTtsProvider provider(RecordingHttpClient client, String apiKey) {
		return provider(client, apiKey, "wav", 24_000, 1_048_576);
	}

	private AliyunTtsProvider provider(HttpClient client, String apiKey) {
		return provider(client, apiKey, "wav", 24_000, 1_048_576);
	}

	private AliyunTtsProvider provider(
			RecordingHttpClient client,
			String apiKey,
			String format,
			int sampleRate,
			int maxAudioBytes) {
		return provider(
				client,
				apiKey,
				format,
				sampleRate,
				maxAudioBytes,
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"));
	}

	private AliyunTtsProvider provider(
			HttpClient client,
			String apiKey,
			String format,
			int sampleRate,
			int maxAudioBytes) {
		return new AliyunTtsProvider(
				client,
				new ObjectMapper(),
				apiKey,
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
				"cosyvoice-v3-flash",
				"loongemily_v3",
				format,
				sampleRate,
				Duration.ofSeconds(20),
				maxAudioBytes);
	}

	private AliyunTtsProvider provider(
			RecordingHttpClient client,
			String apiKey,
			String format,
			int sampleRate,
			int maxAudioBytes,
			URI endpoint) {
		return new AliyunTtsProvider(
				client,
				new ObjectMapper(),
				apiKey,
				endpoint,
				"cosyvoice-v3-flash",
				"loongemily_v3",
				format,
				sampleRate,
				Duration.ofSeconds(20),
				maxAudioBytes);
	}

	private static String audioResponse(String url) {
		return "{\"output\":{\"audio\":{\"url\":\"" + url + "\"}}}";
	}

	private static byte[] utf8(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String readBody(HttpRequest request) {
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

		private final List<QueuedResponse> responses;
		private final List<HttpRequest> requests = new ArrayList<>();

		private RecordingHttpClient(QueuedResponse... responses) {
			this.responses = new ArrayList<>(List.of(responses));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler)
				throws IOException {
			requests.add(request);
			QueuedResponse response = responses.removeFirst();
			if (response.failWithIoError()) {
				throw new IOException("simulated network failure");
			}
			byte[] rawBody = response.body() instanceof byte[] bytes
					? bytes
					: response.body().toString().getBytes(StandardCharsets.UTF_8);
			HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(
					new RecordedResponseInfo(response.statusCode(), response.headers()));
			subscriber.onSubscribe(new NoopSubscription());
			subscriber.onNext(List.of(ByteBuffer.wrap(rawBody)));
			subscriber.onComplete();
			T handledBody;
			try {
				handledBody = subscriber.getBody().toCompletableFuture().join();
			}
			catch (java.util.concurrent.CompletionException exception) {
				if (exception.getCause() instanceof RuntimeException runtimeException) {
					throw runtimeException;
				}
				throw exception;
			}
			return (HttpResponse<T>) new RecordedHttpResponse<>(
					request, response.statusCode(), handledBody, response.headers());
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

	private static final class NoopSubscription implements Flow.Subscription {
		@Override public void request(long n) { }
		@Override public void cancel() { }
	}

	private record QueuedResponse(
			int statusCode,
			Object body,
			Map<String, List<String>> headers,
			boolean failWithIoError) {

		private QueuedResponse(int statusCode, Object body) {
			this(statusCode, body, Map.of(), false);
		}

		private QueuedResponse(
				int statusCode,
				Object body,
				Map<String, List<String>> headers) {
			this(statusCode, body, headers, false);
		}

		private static QueuedResponse ioError() {
			return new QueuedResponse(0, null, Map.of(), true);
		}
	}

	private record RecordedResponseInfo(
			int statusCode,
			Map<String, List<String>> responseHeaders)
			implements HttpResponse.ResponseInfo {
		@Override
		public HttpHeaders headers() {
			return HttpHeaders.of(responseHeaders, (name, value) -> true);
		}

		@Override
		public HttpClient.Version version() {
			return HttpClient.Version.HTTP_1_1;
		}
	}

	private record RecordedHttpResponse<T>(
			HttpRequest request,
			int statusCode,
			T body,
			Map<String, List<String>> responseHeaders)
			implements HttpResponse<T> {
		@Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() {
			return HttpHeaders.of(responseHeaders, (name, value) -> true);
		}
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return request.uri(); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}
}
