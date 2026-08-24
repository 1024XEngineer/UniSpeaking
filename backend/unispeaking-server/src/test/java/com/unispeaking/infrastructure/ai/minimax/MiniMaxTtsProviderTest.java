package com.unispeaking.infrastructure.ai.minimax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.unispeaking.common.exception.BusinessException;
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

class MiniMaxTtsProviderTest {

	@Test
	void sendsConfiguredAudioFormatSampleRateAndBitrateAndDecodesHexAudio() {
		RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{\"data\":{\"audio\":\"494433040000\"},\"base_resp\":{\"status_code\":0}}")));
		MiniMaxTtsProvider provider = provider(client, "minimax-key", "flac", 44_100, 256_000, 128);

		byte[] audio = provider.generateSpeechAudio("  Practice makes progress.  ", null);

		assertArrayEquals(new byte[] {0x49, 0x44, 0x33, 0x04, 0, 0}, audio);
		HttpRequest request = client.requests.getFirst();
		assertEquals("Bearer minimax-key",
				request.headers().firstValue("Authorization").orElseThrow());
		String body = readBody(request);
		assertTrue(body.contains("\"format\":\"flac\""));
		assertTrue(body.contains("\"sample_rate\":44100"));
		assertTrue(body.contains("\"bitrate\":256000"));
		assertTrue(body.contains("\"output_format\":\"hex\""));
		assertFalse(body.contains("minimax-key"));
	}

	@Test
	void acceptsTrustedRegionalEndpointsAndRegistersConfiguredModel() {
		for (String host : List.of("api.minimaxi.com", "api-bj.minimaxi.com", "api.minimax.io", "api-uw.minimax.io")) {
			MiniMaxTtsProvider provider = provider(
					new RecordingHttpClient(new QueuedResponse(
							200,
							utf8("{\"data\":{\"audio\":\"00\"},\"base_resp\":{\"status_code\":0}}"))),
					"key",
					"wav",
					32_000,
					128_000,
					128,
					URI.create("https://" + host + "/v1/t2a_v2"),
					"custom-model");

			assertEquals("custom-model", provider.supportedModels().iterator().next());
			assertArrayEquals(new byte[] {0}, provider.generateSpeechAudio("hello", null));
		}
	}

	@Test
	void rejectsUntrustedOrMalformedEndpointsBeforeSendingCredentials() {
		List<URI> invalidEndpoints = List.of(
				URI.create("http://api.minimaxi.com/v1/t2a_v2"),
				URI.create("https://evil.example/v1/t2a_v2"),
				URI.create("https://api.minimaxi.com:443/v1/t2a_v2"),
				URI.create("https://api.minimaxi.com/v1/t2a_v2?debug=true"),
				URI.create("https://user:pass@api.minimaxi.com/v1/t2a_v2"),
				URI.create("https://api.minimaxi.com/v1/tts"));

		for (URI endpoint : invalidEndpoints) {
			RecordingHttpClient client = new RecordingHttpClient();
			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> provider(client, "secret", "wav", 32_000, 128_000, 128, endpoint, "model")
							.generateSpeechAudio("hello", null));

			assertEquals("MINIMAX_TTS_ENDPOINT_INVALID", exception.code());
			assertTrue(client.requests.isEmpty());
		}
	}

	@Test
	void mapsProviderBusinessErrorsAndHttpErrors() {
		RecordingHttpClient providerError = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{\"data\":null,\"base_resp\":{\"status_code\":1008,\"status_msg\":\"insufficient balance\"}}")));
		BusinessException businessException = assertThrows(
				BusinessException.class,
				() -> provider(providerError, "secret").generateSpeechAudio("hello", null));
		assertEquals("MINIMAX_TTS_REQUEST_FAILED", businessException.code());
		assertTrue(businessException.getMessage().contains("1008"));
		assertFalse(businessException.getMessage().contains("secret"));

		BusinessException httpException = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(new QueuedResponse(429, utf8("rate limited"))), "key")
						.generateSpeechAudio("hello", null));
		assertEquals("MINIMAX_TTS_REQUEST_FAILED", httpException.code());
	}

	@Test
	void mapsEmptyInvalidAndOversizedAudioAndInvalidJson() {
		assertResponseError("{\"data\":{\"audio\":\"\"},\"base_resp\":{\"status_code\":0}}",
				"MINIMAX_TTS_AUDIO_EMPTY");
		assertResponseError("{\"data\":{\"audio\":\"not-hex\"},\"base_resp\":{\"status_code\":0}}",
				"MINIMAX_TTS_AUDIO_INVALID");
		assertResponseError("not-json", "MINIMAX_TTS_RESPONSE_INVALID");

		RecordingHttpClient oversized = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{\"data\":{\"audio\":\"00010203\"},\"base_resp\":{\"status_code\":0}}")));
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(oversized, "key", "wav", 32_000, 128_000, 2)
						.generateSpeechAudio("hello", null));
		assertEquals("MINIMAX_TTS_AUDIO_TOO_LARGE", exception.code());
	}

	@Test
	void mapsTransportAndInterruptedFailuresWithoutLeakingCredentials() throws Exception {
		BusinessException ioException = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(QueuedResponse.ioError()), "secret")
						.generateSpeechAudio("hello", null));
		assertEquals("MINIMAX_TTS_IO_ERROR", ioException.code());
		assertFalse(ioException.getMessage().contains("secret"));

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
		assertEquals("MINIMAX_TTS_INTERRUPTED", interrupted.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void validatesCredentialTextFormatSampleRateAndBitrate() {
		RecordingHttpClient client = new RecordingHttpClient();
		assertEquals("MINIMAX_TTS_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> provider(client, "").generateSpeechAudio("hello", null)).code());
		assertEquals("INVALID_TTS_TEXT", assertThrows(
				BusinessException.class,
				() -> provider(client, "key").generateSpeechAudio(" ", null)).code());
		assertEquals("TTS_TEXT_TOO_LONG", assertThrows(
				BusinessException.class,
				() -> provider(client, "key").generateSpeechAudio("x".repeat(10_000), null)).code());
		assertTrue(client.requests.isEmpty());

		assertThrows(IllegalArgumentException.class,
				() -> provider(client, "key", "ogg", 32_000, 128_000, 128));
		assertThrows(IllegalArgumentException.class,
				() -> provider(client, "key", "wav", 48_000, 128_000, 128));
		assertThrows(IllegalArgumentException.class,
				() -> provider(client, "key", "wav", 32_000, 96_000, 128));
	}

	@Test
	void validatesSpringConstructorTimeoutsAndNullDependencies() {
		assertThrows(IllegalArgumentException.class, () -> new MiniMaxTtsProvider(
				new ObjectMapper(), "key", "https://api.minimaxi.com/v1/t2a_v2", "model", "voice",
				"wav", 32_000, 128_000, 0, 1, 128));
		assertThrows(IllegalArgumentException.class, () -> new MiniMaxTtsProvider(
				new ObjectMapper(), "key", "https://api.minimaxi.com/v1/t2a_v2", "model", "voice",
				"wav", 32_000, 128_000, 1, 0, 128));
		assertThrows(IllegalArgumentException.class, () -> new MiniMaxTtsProvider(
				null, new ObjectMapper(), "key", URI.create("https://api.minimaxi.com/v1/t2a_v2"),
				"model", "voice", "wav", 32_000, 128_000, Duration.ofSeconds(1), 128));
	}

	@Test
	void handlesMalformedConfiguredEndpointAndJsonResponseLimit() {
		MiniMaxTtsProvider malformed = new MiniMaxTtsProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", null,
				"model", "voice", "wav", 32_000, 128_000, Duration.ofSeconds(1), 128);
		assertEquals("MINIMAX_TTS_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> malformed.generateSpeechAudio("hello", null)).code());

		RecordingHttpClient oversized = new RecordingHttpClient(new QueuedResponse(
				200, utf8("x".repeat(2_000_000))));
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(oversized, "key", "wav", 32_000, 128_000, 1)
						.generateSpeechAudio("hello", null));
		assertEquals("MINIMAX_TTS_RESPONSE_TOO_LARGE", exception.code());
	}

	private void assertResponseError(String response, String expectedCode) {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(new RecordingHttpClient(new QueuedResponse(200, utf8(response))), "key")
						.generateSpeechAudio("hello", null));
		assertEquals(expectedCode, exception.code());
	}

	private MiniMaxTtsProvider provider(RecordingHttpClient client, String apiKey) {
		return provider(client, apiKey, "wav", 32_000, 128_000, 1_048_576);
	}

	private MiniMaxTtsProvider provider(HttpClient client, String apiKey) {
		return provider(client, apiKey, "wav", 32_000, 128_000, 1_048_576);
	}

	private MiniMaxTtsProvider provider(
			RecordingHttpClient client,
			String apiKey,
			String format,
			int sampleRate,
			int bitrate,
			int maxAudioBytes) {
		return provider(
				client,
				apiKey,
				format,
				sampleRate,
				bitrate,
				maxAudioBytes,
				URI.create("https://api.minimaxi.com/v1/t2a_v2"),
				"speech-2.8-hd");
	}

	private MiniMaxTtsProvider provider(
			HttpClient client,
			String apiKey,
			String format,
			int sampleRate,
			int bitrate,
			int maxAudioBytes) {
		return new MiniMaxTtsProvider(
				client,
				new ObjectMapper(),
				apiKey,
				URI.create("https://api.minimaxi.com/v1/t2a_v2"),
				"speech-2.8-hd",
				"male-qn-qingse",
				format,
				sampleRate,
				bitrate,
				Duration.ofSeconds(20),
				maxAudioBytes);
	}

	private MiniMaxTtsProvider provider(
			RecordingHttpClient client,
			String apiKey,
			String format,
			int sampleRate,
			int bitrate,
			int maxAudioBytes,
			URI endpoint,
			String model) {
		return new MiniMaxTtsProvider(
				client,
				new ObjectMapper(),
				apiKey,
				endpoint,
				model,
				"male-qn-qingse",
				format,
				sampleRate,
				bitrate,
				Duration.ofSeconds(20),
				maxAudioBytes);
	}

	private static byte[] utf8(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String readBody(HttpRequest request) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		CompletableFuture<Void> completed = new CompletableFuture<>();
		request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
			@Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
			@Override public void onNext(ByteBuffer item) {
				byte[] chunk = new byte[item.remaining()];
				item.get(chunk);
				bytes.writeBytes(chunk);
			}
			@Override public void onError(Throwable throwable) { completed.completeExceptionally(throwable); }
			@Override public void onComplete() { completed.complete(null); }
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
			if (response.failWithIoError()) throw new IOException("simulated network failure");
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
			try { return CompletableFuture.completedFuture(send(request, responseBodyHandler)); }
			catch (IOException exception) { return CompletableFuture.failedFuture(exception); }
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
		private QueuedResponse(int statusCode, Object body) { this(statusCode, body, Map.of(), false); }
		private static QueuedResponse ioError() { return new QueuedResponse(0, null, Map.of(), true); }
	}

	private record RecordedResponseInfo(
			int statusCode,
			Map<String, List<String>> responseHeaders)
			implements HttpResponse.ResponseInfo {
		@Override public HttpHeaders headers() { return HttpHeaders.of(responseHeaders, (name, value) -> true); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}

	private record RecordedHttpResponse<T>(
			HttpRequest request,
			int statusCode,
			T body,
			Map<String, List<String>> responseHeaders)
			implements HttpResponse<T> {
		@Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() { return HttpHeaders.of(responseHeaders, (name, value) -> true); }
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return request.uri(); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}
}
