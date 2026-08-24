package com.unispeaking.infrastructure.ai.deepseek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.ai.doubao.DoubaoAsrProvider;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DoubaoProviderCoverageTest {

	private static final URI DEEPSEEK_ENDPOINT =
			URI.create("https://api.deepseek.com/chat/completions");
	private static final URI DOUBAO_ENDPOINT =
			URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash");

	@Test
	void sendsDeepSeekRequestWithConfiguredCredentialAndRecordsUsage() {
		RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"id":"deepseek-request-1","choices":[{"message":{"content":"answer"}}],
				"usage":{"prompt_tokens":11,"completion_tokens":7}}
				""")));
		DeepSeekLlmProvider provider = deepSeekProvider(client, "deepseek-key", 1_024);

		var response = provider.executeLlmTaskMeasured("  Return JSON.  ", null);

		assertEquals("answer", response.response());
		assertEquals("deepseek-request-1", response.providerRequestId());
		assertEquals(11, response.usage().inputTokens());
		assertEquals(7, response.usage().outputTokens());
		assertEquals("PROVIDER", response.usage().source());
		HttpRequest request = client.requests.getFirst();
		assertEquals("Bearer deepseek-key",
				request.headers().firstValue("Authorization").orElseThrow());
		String body = readBody(request);
		assertTrue(body.contains("\"model\":\"deepseek-v4-flash\""));
		assertTrue(body.contains("\"content\":\"Return JSON.\""));
		assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"));
		assertFalse(body.contains("deepseek-key"));
		assertFalse(client.bodyCompletedOnSubscribe);
	}

	@Test
	void rejectsDeepSeekMissingCredentialAndEmptyPromptBeforeSending() {
		RecordingHttpClient client = new RecordingHttpClient();

		assertEquals("INVALID_LLM_PROMPT", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(client, "deepseek-key", 128)
						.executeLlmTask("  ", null)).code());
		assertEquals("DEEPSEEK_LLM_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(client, "", 128)
						.executeLlmTask("hello", null)).code());
		assertTrue(client.requests.isEmpty());
	}

	@Test
	void rejectsUntrustedDeepSeekEndpointsBeforeSendingCredentials() {
		List<URI> invalidEndpoints = List.of(
				URI.create("http://api.deepseek.com/chat/completions"),
				URI.create("https://evil.example/chat/completions"),
				URI.create("https://api.deepseek.com:443/chat/completions"),
				URI.create("https://api.deepseek.com/chat/completions?debug=true"),
				URI.create("https://user:pass@api.deepseek.com/chat/completions"),
				URI.create("https://api.deepseek.com/v1/chat/completions"));

		for (URI endpoint : invalidEndpoints) {
			RecordingHttpClient client = new RecordingHttpClient();
			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> new DeepSeekLlmProvider(
							client, new ObjectMapper(), "deepseek-key", endpoint,
							"deepseek-v4-flash", Duration.ofSeconds(2), 128)
							.executeLlmTask("hello", null));

			assertEquals("DEEPSEEK_LLM_ENDPOINT_INVALID", exception.code());
			assertTrue(client.requests.isEmpty());
		}

		RecordingHttpClient malformedClient = new RecordingHttpClient();
		DeepSeekLlmProvider malformed = new DeepSeekLlmProvider(
				malformedClient, new ObjectMapper(), "deepseek-key", null,
				"deepseek-v4-flash", Duration.ofSeconds(2), 128);
		assertEquals("DEEPSEEK_LLM_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> malformed.executeLlmTask("hello", null)).code());
		assertTrue(malformedClient.requests.isEmpty());
	}

	@Test
	void mapsDeepSeekHttpJsonEmptyAndOversizedResponses() {
		assertEquals("DEEPSEEK_LLM_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(
						new RecordingHttpClient(new QueuedResponse(503, utf8("busy"))),
						"key", 128).executeLlmTask("hello", null)).code());
		assertEquals("DEEPSEEK_LLM_RESPONSE_INVALID", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(
						new RecordingHttpClient(new QueuedResponse(200, utf8("not-json"))),
						"key", 128).executeLlmTask("hello", null)).code());
		assertEquals("DEEPSEEK_LLM_EMPTY_RESPONSE", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(
						new RecordingHttpClient(new QueuedResponse(
								200, utf8("{\"choices\":[{\"message\":{\"content\":\" \"}}]}"))),
						"key", 128).executeLlmTask("hello", null)).code());
		assertEquals("DEEPSEEK_LLM_RESPONSE_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(
						new RecordingHttpClient(new QueuedResponse(200, utf8("123456789"))),
						"key", 8).executeLlmTask("hello", null)).code());
	}

	@Test
	void mapsDeepSeekIoAndInterruptedFailures() throws Exception {
		BusinessException ioFailure = assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(
						new RecordingHttpClient(QueuedResponse.ioError()), "key", 128)
						.executeLlmTask("hello", null));
		assertEquals("DEEPSEEK_LLM_IO_ERROR", ioFailure.code());

		HttpClient interruptedClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(interruptedClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
				interruptedClient, new ObjectMapper(), "key", DEEPSEEK_ENDPOINT,
				"model", Duration.ofSeconds(2), 128);

		Thread.interrupted();
		BusinessException interrupted = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("hello", null));
		assertEquals("DEEPSEEK_LLM_INTERRUPTED", interrupted.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void sendsDoubaoApiKeyRequestWithExpectedHeadersAndBody() {
		RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{\"result\":{\"text\":\"Practice makes progress.\"}}"),
				Map.of("X-Api-Status-Code", List.of("20000000"))));
		DoubaoAsrProvider provider = doubaoProvider(client, "doubao-api-key", "", "", 128, 1_024);

		assertEquals("Practice makes progress.",
				provider.convertAudioToText(new byte[] {1, 2, 3}, null));

		HttpRequest request = client.requests.getFirst();
		assertEquals("doubao-api-key", request.headers().firstValue("X-Api-Key").orElseThrow());
		assertEquals("volc.bigasr.auc_turbo",
				request.headers().firstValue("X-Api-Resource-Id").orElseThrow());
		assertEquals("-1", request.headers().firstValue("X-Api-Sequence").orElseThrow());
		assertTrue(request.headers().firstValue("X-Api-Request-Id").orElseThrow().length() > 10);
		String body = readBody(request);
		assertTrue(body.contains("\"uid\":\"unispeaking\""));
		assertTrue(body.contains("\"data\":\"AQID\""));
		assertTrue(body.contains("\"model_name\":\"bigmodel\""));
		assertFalse(body.contains("doubao-api-key"));
		assertTrue(request.headers().firstValue("X-Api-App-Key").isEmpty());
		assertTrue(request.headers().firstValue("X-Api-Access-Key").isEmpty());
	}

	@Test
	void usesLegacyDoubaoCredentialsAndHeadersWhenApiKeyIsAbsent() {
		RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{\"result\":{\"text\":\"legacy-ok\"}}"),
				Map.of("X-Api-Status-Code", List.of("20000000"))));
		DoubaoAsrProvider provider = doubaoProvider(
				client, "", "legacy-app", "legacy-access", 128, 1_024);

		assertEquals("legacy-ok", provider.convertAudioToText(new byte[] {1, 2}, null));
		HttpRequest request = client.requests.getFirst();
		assertEquals("legacy-app", request.headers().firstValue("X-Api-App-Key").orElseThrow());
		assertEquals("legacy-access", request.headers().firstValue("X-Api-Access-Key").orElseThrow());
		assertTrue(request.headers().firstValue("X-Api-Key").isEmpty());
	}

	@Test
	void rejectsDoubaoEmptyInputMissingCredentialsAndOversizedAudio() {
		RecordingHttpClient client = new RecordingHttpClient();
		DoubaoAsrProvider provider = doubaoProvider(client, "key", "", "", 1, 128);

		assertEquals("INVALID_AUDIO", assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(new byte[0], null)).code());
		assertEquals("TRANSCRIPTION_AUDIO_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(new byte[] {1, 2}, null)).code());
		assertEquals("DOUBAO_ASR_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(client, "", "legacy-app", "", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());
		assertTrue(client.requests.isEmpty());
	}

	@Test
	void rejectsUntrustedDoubaoEndpointsBeforeSendingCredentials() {
		List<URI> invalidEndpoints = List.of(
				URI.create("http://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
				URI.create("https://evil.example/api/v3/auc/bigmodel/recognize/flash"),
				URI.create("https://openspeech.bytedance.com:443/api/v3/auc/bigmodel/recognize/flash"),
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash?debug=true"),
				URI.create("https://user:pass@openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize"));

		for (URI endpoint : invalidEndpoints) {
			RecordingHttpClient client = new RecordingHttpClient();
			DoubaoAsrProvider provider = new DoubaoAsrProvider(
					client, new ObjectMapper(), "doubao-key", "", "", "user", endpoint,
					"resource", Duration.ofSeconds(2), 128, 128);

			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> provider.convertAudioToText(new byte[] {1}, null));
			assertEquals("DOUBAO_ASR_ENDPOINT_INVALID", exception.code());
			assertTrue(client.requests.isEmpty());
		}
	}

	@Test
	void mapsDoubaoHttpProviderStatusJsonEmptyAndOversizedResponses() {
		assertEquals("DOUBAO_ASR_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(new QueuedResponse(500, utf8("busy"))),
						"key", "", "", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());
		assertEquals("DOUBAO_ASR_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(new QueuedResponse(200, utf8("{}"))),
						"key", "", "", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());
		assertEquals("DOUBAO_ASR_RESPONSE_INVALID", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(new QueuedResponse(
								200, utf8("not-json"), successHeaders())),
						"key", "", "", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());
		assertEquals("DOUBAO_ASR_RESULT_EMPTY", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(new QueuedResponse(
								200, utf8("{\"result\":{\"text\":\" \"}}"), successHeaders())),
						"key", "", "", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());
		assertEquals("DOUBAO_ASR_RESPONSE_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(new QueuedResponse(
								200, utf8("123456789"), successHeaders())),
						"key", "", "", 128, 8)
						.convertAudioToText(new byte[] {1}, null)).code());
	}

	@Test
	void mapsDoubaoIoAndInterruptedFailures() throws Exception {
		BusinessException ioFailure = assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(QueuedResponse.ioError()), "key", "", "", 128, 128)
						.convertAudioToText(new byte[] {1}, null));
		assertEquals("DOUBAO_ASR_IO_ERROR", ioFailure.code());

		HttpClient interruptedClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(interruptedClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		DoubaoAsrProvider provider = new DoubaoAsrProvider(
				interruptedClient, new ObjectMapper(), "key", "", "", "user", DOUBAO_ENDPOINT,
				"resource", Duration.ofSeconds(2), 128, 128);

		Thread.interrupted();
		BusinessException interrupted = assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(new byte[] {1}, null));
		assertEquals("DOUBAO_ASR_INTERRUPTED", interrupted.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	private DeepSeekLlmProvider deepSeekProvider(
			RecordingHttpClient client, String apiKey, int maxResponseBytes) {
		return new DeepSeekLlmProvider(
				client, new ObjectMapper(), apiKey, DEEPSEEK_ENDPOINT,
				"deepseek-v4-flash", Duration.ofSeconds(2), maxResponseBytes);
	}

	private DoubaoAsrProvider doubaoProvider(
			RecordingHttpClient client,
			String apiKey,
			String appKey,
			String accessKey,
			int maxAudioBytes,
			int maxResponseBytes) {
		return new DoubaoAsrProvider(
				client, new ObjectMapper(), apiKey, appKey, accessKey, "unispeaking",
				DOUBAO_ENDPOINT, "volc.bigasr.auc_turbo", Duration.ofSeconds(2),
				maxAudioBytes, maxResponseBytes);
	}

	private static Map<String, List<String>> successHeaders() {
		return Map.of("X-Api-Status-Code", List.of("20000000"));
	}

	private static byte[] utf8(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String readBody(HttpRequest request) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		CompletableFuture<Void> complete = new CompletableFuture<>();
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
				complete.completeExceptionally(throwable);
			}

			@Override
			public void onComplete() {
				complete.complete(null);
			}
		});
		complete.join();
		return bytes.toString(StandardCharsets.UTF_8);
	}

	private static final class RecordingHttpClient extends HttpClient {

		private final List<QueuedResponse> responses;
		private final List<HttpRequest> requests = new ArrayList<>();
		private boolean bodyCompletedOnSubscribe;

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
			subscriber.onSubscribe(new Flow.Subscription() {
				@Override
				public void request(long count) { }

				@Override
				public void cancel() { }
			});
			bodyCompletedOnSubscribe |= subscriber.getBody().toCompletableFuture().isDone();
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
			try {
				return CompletableFuture.completedFuture(send(request, responseBodyHandler));
			}
			catch (IOException exception) {
				return CompletableFuture.failedFuture(exception);
			}
		}

		@Override
		public Optional<CookieHandler> cookieHandler() {
			return Optional.empty();
		}

		@Override
		public Optional<Duration> connectTimeout() {
			return Optional.empty();
		}

		@Override
		public Redirect followRedirects() {
			return Redirect.NEVER;
		}

		@Override
		public Optional<ProxySelector> proxy() {
			return Optional.empty();
		}

		@Override
		public SSLContext sslContext() {
			return null;
		}

		@Override
		public SSLParameters sslParameters() {
			return new SSLParameters();
		}

		@Override
		public Optional<Authenticator> authenticator() {
			return Optional.empty();
		}

		@Override
		public Version version() {
			return Version.HTTP_1_1;
		}

		@Override
		public Optional<Executor> executor() {
			return Optional.empty();
		}
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

		@Override
		public Optional<HttpResponse<T>> previousResponse() {
			return Optional.empty();
		}

		@Override
		public HttpHeaders headers() {
			return HttpHeaders.of(responseHeaders, (name, value) -> true);
		}

		@Override
		public Optional<SSLSession> sslSession() {
			return Optional.empty();
		}

		@Override
		public URI uri() {
			return request.uri();
		}

		@Override
		public HttpClient.Version version() {
			return HttpClient.Version.HTTP_1_1;
		}
	}
}
