package com.unispeaking.infrastructure.ai.qwen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.ai.aliyun.AliyunTtsProvider;
import com.unispeaking.infrastructure.ai.deepseek.DeepSeekLlmProvider;
import com.unispeaking.infrastructure.ai.doubao.DoubaoAsrProvider;
import com.unispeaking.infrastructure.ai.minimax.MiniMaxTtsProvider;
import com.unispeaking.provider.MeteredProviderException;
import com.unispeaking.provider.LlmResponseFormat;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QwenProvidersCoverageTest {

	private static final URI QWEN_ENDPOINT = URI.create(
			"https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions");
	private static final URI TTS_ENDPOINT = URI.create(
			"https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
	private static final byte[] WAV = new byte[] {
			'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'
	};

	@Test
	void qwenLlmSendsTextAndJsonFormatAndMeasuresProviderUsage() {
		RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
				200,
				"{\"id\":\"response-id\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],"
						+ "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":4}}",
				Map.of("x-request-id", List.of("header-id"))));
		QwenLlmProvider provider = llm(client, "secret");

		var response = provider.executeLlmTaskMeasured(
				"  say hello  ", null, LlmResponseFormat.JSON_OBJECT);

		assertEquals("{\"ok\":true}", response.response());
		assertEquals("header-id", response.providerRequestId());
		assertEquals(12, response.usage().inputTokens());
		assertEquals(4, response.usage().outputTokens());
		assertTrue(body(client.requests.getFirst()).contains("response_format"));
		assertEquals("Bearer secret",
				client.requests.getFirst().headers().firstValue("Authorization").orElseThrow());
	}

	@Test
	void qwenLlmUsesBodyRequestIdAndMapsBoundaryFailures() {
		RecordingHttpClient bodyId = new RecordingHttpClient(new QueuedResponse(
				200,
				"{\"request_id\":\"body-id\",\"choices\":[{\"message\":{\"content\":\"answer\"}}]}"));
		assertEquals("body-id", llm(bodyId, "key")
				.executeLlmTaskMeasured("prompt", null).providerRequestId());

		RecordingHttpClient noCredentialClient = new RecordingHttpClient();
		assertCode("QWEN_LLM_CREDENTIAL_MISSING", () -> llm(noCredentialClient, "")
				.executeLlmTask("prompt", null));
		assertCode("INVALID_LLM_PROMPT", () -> llm(noCredentialClient, "key")
				.executeLlmTask(" ", null));
		assertCode("QWEN_LLM_REQUEST_FAILED", () -> llm(
				new RecordingHttpClient(new QueuedResponse(503, "busy")), "key")
				.executeLlmTask("prompt", null));
		assertCode("QWEN_LLM_RESPONSE_INVALID", () -> llm(
				new RecordingHttpClient(new QueuedResponse(200, "not-json")), "key")
				.executeLlmTask("prompt", null));
		assertCode("QWEN_LLM_EMPTY_RESPONSE", () -> llm(
				new RecordingHttpClient(new QueuedResponse(200,
						"{\"choices\":[{\"message\":{\"content\":\" \"}}]}")), "key")
				.executeLlmTask("prompt", null));
		assertCode("QWEN_LLM_RESPONSE_TOO_LARGE", () -> new QwenLlmProvider(
				new RecordingHttpClient(new QueuedResponse(200, "x".repeat(129))),
				new ObjectMapper(), "key", QWEN_ENDPOINT, "model", Duration.ofSeconds(1), 128)
				.executeLlmTask("prompt", null));
	}

	@Test
	void qwenLlmRejectsUntrustedEndpointAndTransportInterrupt() throws Exception {
		RecordingHttpClient client = new RecordingHttpClient();
		assertCode("QWEN_LLM_ENDPOINT_INVALID", () -> new QwenLlmProvider(
				client, new ObjectMapper(), "key",
				URI.create("https://evil.example/chat"), "model", Duration.ofSeconds(1), 128)
				.executeLlmTask("prompt", null));

		HttpClient ioClient = mock(HttpClient.class);
		doThrow(new IOException("network")).when(ioClient).send(any(HttpRequest.class),
				any(HttpResponse.BodyHandler.class));
		assertCode("QWEN_LLM_IO_ERROR", () -> llm(ioClient, "key")
				.executeLlmTask("prompt", null));

		HttpClient interruptedClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled")).when(interruptedClient)
				.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		Thread.interrupted();
		assertCode("QWEN_LLM_INTERRUPTED", () -> llm(interruptedClient, "key")
				.executeLlmTask("prompt", null));
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void qwenAsrSendsAudioAndSupportsCommonFormats() {
		RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
				200,
				"{\"choices\":[{\"message\":{\"content\":\"recognized text\"}}]}"));
		QwenAsrProvider provider = asr(client, "secret", 128, 1_024);

		assertEquals("recognized text", provider.convertAudioToText(new byte[] {1, 2}, null));
		String requestBody = body(client.requests.getFirst());
		assertTrue(requestBody.contains("audio/wav"));
		assertEquals("Bearer secret",
				client.requests.getFirst().headers().firstValue("Authorization").orElseThrow());
	}

	@Test
	void qwenAsrMapsCredentialAudioHttpAndResponseFailures() {
		RecordingHttpClient noCredential = new RecordingHttpClient();
		assertCode("QWEN_ASR_CREDENTIAL_MISSING", () -> asr(noCredential, "", 128, 128)
				.convertAudioToText(new byte[] {1}, null));
		assertCode("INVALID_AUDIO", () -> asr(noCredential, "key", 128, 128)
				.convertAudioToText(new byte[0], null));
		assertCode("TRANSCRIPTION_AUDIO_TOO_LARGE", () -> asr(noCredential, "key", 1, 128)
				.convertAudioToText(new byte[] {1, 2}, null));
		assertCode("QWEN_ASR_REQUEST_FAILED", () -> asr(
				new RecordingHttpClient(new QueuedResponse(500, "failed")), "key", 128, 128)
				.convertAudioToText(new byte[] {1}, null));
		assertCode("QWEN_ASR_RESPONSE_INVALID", () -> asr(
				new RecordingHttpClient(new QueuedResponse(200, "not-json")), "key", 128, 128)
				.convertAudioToText(new byte[] {1}, null));
		assertCode("QWEN_ASR_RESULT_EMPTY", () -> asr(
				new RecordingHttpClient(new QueuedResponse(200,
						"{\"choices\":[{\"message\":{\"content\":\"\"}}]}")), "key", 128, 128)
				.convertAudioToText(new byte[] {1}, null));
		assertCode("QWEN_ASR_RESPONSE_TOO_LARGE", () -> asr(
				new RecordingHttpClient(new QueuedResponse(200, "x".repeat(129))), "key", 128, 128)
				.convertAudioToText(new byte[] {1}, null));
	}

	@Test
	void qwenAsrRejectsUntrustedEndpointAndTransportFailures() throws Exception {
		assertCode("QWEN_ASR_ENDPOINT_INVALID", () -> asr(
				new RecordingHttpClient(), "key", 128, 128,
				URI.create("https://evil.example/chat"))
				.convertAudioToText(new byte[] {1}, null));
		HttpClient ioClient = mock(HttpClient.class);
		doThrow(new IOException("network")).when(ioClient).send(any(HttpRequest.class),
				any(HttpResponse.BodyHandler.class));
		assertCode("QWEN_ASR_IO_ERROR", () -> asr(ioClient, "key", 128, 128)
				.convertAudioToText(new byte[] {1}, null));
	}

	@Test
	void qwenTtsDownloadsWavUsesVoiceMappingAndCachesAudio() {
		String audioUrl = "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(200, "{\"request_id\":\"tts-id\",\"output\":{\"audio\":{\"url\":\""
						+ audioUrl + "\"}}}"),
				new QueuedResponse(200, WAV));
		QwenTtsProvider provider = tts(client, "secret", 1_024, 1_024);

		var measured = provider.generateSpeechAudioMeasured("hello", null, "Harvey");
		assertArrayEquals(WAV, measured.response());
		assertEquals("tts-id", measured.providerRequestId());
		assertEquals(2, client.requests.size());
		assertTrue(body(client.requests.getFirst()).contains("\"voice\":\"Neil\""));
		assertEquals("https", client.requests.get(1).uri().getScheme());
		assertArrayEquals(WAV, provider.generateSpeechAudio("hello", null, "Harvey"));
		assertEquals(2, client.requests.size(), "second call should use the audio cache");
	}

	@Test
	void qwenTtsMapsInvalidResponsesAndMeasuredDownloadFailures() {
		RecordingHttpClient client = new RecordingHttpClient();
		assertCode("QWEN_TTS_CREDENTIAL_MISSING", () -> tts(client, "", 128, 128)
				.generateSpeechAudio("hello", null));
		assertCode("INVALID_TTS_TEXT", () -> tts(client, "key", 128, 128)
				.generateSpeechAudio(" ", null));
		assertCode("TTS_TEXT_TOO_LONG", () -> tts(client, "key", 128, 128)
				.generateSpeechAudio("x".repeat(5_001), null));

		assertCode("QWEN_TTS_REQUEST_FAILED", () -> tts(
				new RecordingHttpClient(new QueuedResponse(503, "busy")), "key", 128, 128)
				.generateSpeechAudio("hello", null));
		assertCode("QWEN_TTS_RESPONSE_INVALID", () -> tts(
				new RecordingHttpClient(new QueuedResponse(200, "not-json")), "key", 128, 128)
				.generateSpeechAudio("hello", null));
		assertCode("QWEN_TTS_AUDIO_URL_MISSING", () -> tts(
				new RecordingHttpClient(new QueuedResponse(200, "{\"output\":{}}")), "key", 128, 128)
				.generateSpeechAudio("hello", null));
		assertCode("QWEN_TTS_AUDIO_URL_UNTRUSTED", () -> tts(
				new RecordingHttpClient(new QueuedResponse(200,
						"{\"output\":{\"audio\":{\"url\":\"https://evil.example/a.wav\"}}}")),
				"key", 128, 128).generateSpeechAudio("hello", null));

		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		RecordingHttpClient failedDownload = new RecordingHttpClient(
				new QueuedResponse(200, "{\"request_id\":\"tts-id\",\"output\":{\"audio\":{\"url\":\"" + url + "\"}}}"),
				new QueuedResponse(502, "failed"));
		MeteredProviderException metered = assertThrows(MeteredProviderException.class,
				() -> tts(failedDownload, "key", 128, 128)
						.generateSpeechAudioMeasured("hello", null));
		assertEquals("QWEN_TTS_AUDIO_DOWNLOAD_FAILED", metered.code());
		assertEquals("tts-id", metered.providerRequestId());

		assertCode("QWEN_TTS_RESPONSE_TOO_LARGE", () -> tts(
				new RecordingHttpClient(new QueuedResponse(200, "x".repeat(129))), "key", 128, 128)
				.generateSpeechAudio("hello", null));
		RecordingHttpClient oversizedAudio = new RecordingHttpClient(
				new QueuedResponse(200, "{\"output\":{\"audio\":{\"url\":\"" + url + "\"}}}"),
				new QueuedResponse(200, new byte[129]));
		assertCode("QWEN_TTS_AUDIO_TOO_LARGE", () -> tts(oversizedAudio, "key", 1_024, 128)
				.generateSpeechAudio("hello", null));
	}

	@Test
	void qwenTtsRejectsInvalidAudioAndEndpointAndRestoresInterrupt() throws Exception {
		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav";
		assertCode("QWEN_TTS_AUDIO_INVALID", () -> tts(new RecordingHttpClient(
				new QueuedResponse(200, "{\"output\":{\"audio\":{\"url\":\"" + url + "\"}}}"),
				new QueuedResponse(200, "not-wav")), "key", 128, 128)
				.generateSpeechAudio("hello", null));
		assertCode("QWEN_TTS_ENDPOINT_INVALID", () -> tts(
				new RecordingHttpClient(), "key", 128, 128,
				URI.create("https://evil.example/generate"))
				.generateSpeechAudio("hello", null));

		HttpClient interrupted = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled")).when(interrupted)
				.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		Thread.interrupted();
		assertCode("QWEN_TTS_INTERRUPTED", () -> tts(interrupted, "key", 128, 128)
				.generateSpeechAudio("hello", null));
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void limitedBodySubscribersHandleDuplicateSubscriptionsErrorsAndLateChunks() throws Exception {
		for (Class<?> owner : List.of(
				QwenLlmProvider.class, QwenAsrProvider.class, QwenTtsProvider.class,
				AliyunTtsProvider.class, DoubaoAsrProvider.class,
				MiniMaxTtsProvider.class, DeepSeekLlmProvider.class)) {
			HttpResponse.BodySubscriber<byte[]> normal = newLimitedSubscriber(owner, 16);
			Flow.Subscription first = mock(Flow.Subscription.class);
			Flow.Subscription duplicate = mock(Flow.Subscription.class);
			normal.onSubscribe(first);
			normal.onSubscribe(duplicate);
			verify(first).request(1);
			verify(duplicate).cancel();
			normal.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2})));
			normal.onComplete();
			assertArrayEquals(new byte[] {1, 2}, normal.getBody().toCompletableFuture().join());

			HttpResponse.BodySubscriber<byte[]> failed = newLimitedSubscriber(owner, 16);
			failed.onSubscribe(mock(Flow.Subscription.class));
			failed.onError(new IOException("body failed"));
			failed.onNext(List.of(ByteBuffer.wrap(new byte[] {3})));
			assertThrows(CompletionException.class, () -> failed.getBody().toCompletableFuture().join());
		}
	}

	@Test
	void qwenAsrSupportsEveryMediaTypeAndRejectsUnknownFormats() throws Exception {
		QwenAsrProvider provider = asr(new RecordingHttpClient(), "key", 128, 128);
		Map<String, String> expected = Map.of(
				"wav", "audio/wav", "mp3", "audio/mpeg", "aac", "audio/aac",
				"m4a", "audio/mp4", "flac", "audio/flac", "ogg", "audio/ogg",
				"opus", "audio/ogg");
		for (var entry : expected.entrySet()) {
			assertEquals(entry.getValue(), invoke(provider, "mediaType",
					new Class<?>[] {String.class}, " " + entry.getKey().toUpperCase() + " "));
		}
		assertCode("UNSUPPORTED_TRANSCRIPTION_AUDIO_FORMAT",
				() -> invoke(provider, "mediaType", new Class<?>[] {String.class}, (Object) null));
		assertCode("UNSUPPORTED_TRANSCRIPTION_AUDIO_FORMAT",
				() -> invoke(provider, "mediaType", new Class<?>[] {String.class}, "aiff"));
	}

	@Test
	void qwenLlmAndAsrRejectEveryUntrustedEndpointComponent() {
		URI[] invalid = {
				null,
				URI.create("/compatible-mode/v1/chat/completions"),
				URI.create("http://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				URI.create("https://evil.example/compatible-mode/v1/chat/completions"),
				URI.create("https://user@workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com:443/compatible-mode/v1/chat/completions"),
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com/wrong"),
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions?q=1"),
				URI.create("https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions#f")
		};
		for (URI endpoint : invalid) {
			assertCode("QWEN_LLM_ENDPOINT_INVALID", () -> new QwenLlmProvider(
					new RecordingHttpClient(), new ObjectMapper(), "key", endpoint, null,
					Duration.ofSeconds(1), 0).executeLlmTask("prompt", null));
			assertCode("QWEN_ASR_ENDPOINT_INVALID", () -> asr(
					new RecordingHttpClient(), "key", 0, 0, endpoint)
					.convertAudioToText(new byte[] {1}, null));
		}
	}

	@Test
	void qwenTtsRejectsEveryEndpointAndAudioUrlComponent() {
		String trustedPath = "/api/v1/services/aigc/multimodal-generation/generation";
		URI[] invalidEndpoints = {
				URI.create("/api/v1"),
				URI.create("http://dashscope.aliyuncs.com" + trustedPath),
				URI.create("https://evil.example" + trustedPath),
				URI.create("https://user@dashscope.aliyuncs.com" + trustedPath),
				URI.create("https://dashscope.aliyuncs.com:443" + trustedPath),
				URI.create("https://dashscope.aliyuncs.com/wrong"),
				URI.create("https://dashscope.aliyuncs.com" + trustedPath + "?q=1"),
				URI.create("https://dashscope.aliyuncs.com" + trustedPath + "#f")
		};
		for (URI endpoint : invalidEndpoints) {
			assertCode("QWEN_TTS_ENDPOINT_INVALID", () -> tts(
					new RecordingHttpClient(), "key", 0, 0, endpoint)
					.generateSpeechAudio("hello", null));
		}

		String[] invalidAudioUrls = {
				"/audio.wav", "ftp://bucket.aliyuncs.com/audio.wav",
				"https://evil.example/audio.wav", "https://user@bucket.aliyuncs.com/audio.wav",
				"https://bucket.aliyuncs.com:443/audio.wav", "not a uri"
		};
		for (String url : invalidAudioUrls) {
			RecordingHttpClient client = new RecordingHttpClient(new QueuedResponse(
					200, "{\"output\":{\"audio\":{\"url\":\"" + url + "\"}}}"));
			assertThrows(BusinessException.class,
					() -> tts(client, "key", 128, 128).generateSpeechAudio("hello", null));
		}
	}

	@Test
	void qwenTtsValidatesEveryWavSignatureByteAndVoiceFallback() throws Exception {
		QwenTtsProvider provider = tts(new RecordingHttpClient(), "key", 128, 128);
		assertEquals("Aiden", invoke(provider, "resolveVoice", new Class<?>[] {String.class}, (Object) null));
		assertEquals("Aiden", invoke(provider, "resolveVoice", new Class<?>[] {String.class}, " "));
		assertEquals("Aiden", invoke(provider, "resolveVoice", new Class<?>[] {String.class}, "unknown"));
		assertEquals("Ryan", invoke(provider, "resolveVoice", new Class<?>[] {String.class}, "Raymond"));

		assertCode("QWEN_TTS_AUDIO_INVALID",
				() -> invoke(provider, "requireWav", new Class<?>[] {byte[].class}, (Object) null));
		assertCode("QWEN_TTS_AUDIO_INVALID",
				() -> invoke(provider, "requireWav", new Class<?>[] {byte[].class}, new byte[11]));
		for (int index : new int[] {0, 1, 2, 3, 8, 9, 10, 11}) {
			byte[] corrupt = WAV.clone();
			corrupt[index] = 'X';
			assertCode("QWEN_TTS_AUDIO_INVALID",
					() -> invoke(provider, "requireWav", new Class<?>[] {byte[].class}, corrupt));
		}
		invoke(provider, "requireWav", new Class<?>[] {byte[].class}, WAV);
	}

	@Test
	void providerConstructorsRejectNullBlankAndNonPositiveDependencies() {
		assertThrows(IllegalArgumentException.class, () -> new QwenLlmProvider(
				null, new ObjectMapper(), "key", QWEN_ENDPOINT, null, Duration.ofSeconds(1), 1));
		assertThrows(IllegalArgumentException.class, () -> new QwenLlmProvider(
				new RecordingHttpClient(), null, "key", QWEN_ENDPOINT, null, Duration.ofSeconds(1), 1));
		for (Duration duration : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1)}) {
			assertThrows(IllegalArgumentException.class, () -> new QwenLlmProvider(
					new RecordingHttpClient(), new ObjectMapper(), "key", QWEN_ENDPOINT, null, duration, 1));
		}
		assertThrows(IllegalArgumentException.class, () -> new QwenAsrProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", QWEN_ENDPOINT, " ",
				Duration.ofSeconds(1), 1, 1));
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", TTS_ENDPOINT, "model", " ",
				"English", Duration.ofSeconds(1), 1, 1));
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", TTS_ENDPOINT, "model", "voice",
				" ", Duration.ofSeconds(1), 1, 1));
	}

	private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
			throws Exception {
		Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
		method.setAccessible(true);
		try {
			return method.invoke(target, args);
		}
		catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof Exception cause) throw cause;
			throw exception;
		}
	}

	@SuppressWarnings("unchecked")
	private static HttpResponse.BodySubscriber<byte[]> newLimitedSubscriber(Class<?> owner, int limit)
			throws Exception {
		Class<?> type = Class.forName(owner.getName() + "$LimitedBodySubscriber");
		Constructor<?> constructor = type.getDeclaredConstructor(int.class, String.class, String.class);
		constructor.setAccessible(true);
		return (HttpResponse.BodySubscriber<byte[]>) constructor.newInstance(limit, "TOO_LARGE", "too large");
	}

	private static QwenLlmProvider llm(HttpClient client, String key) {
		return new QwenLlmProvider(client, new ObjectMapper(), key, QWEN_ENDPOINT,
				"qwen-model", Duration.ofSeconds(1), 1_024);
	}

	private static QwenAsrProvider asr(HttpClient client, String key, int maxAudio, int maxResponse) {
		return asr(client, key, maxAudio, maxResponse, QWEN_ENDPOINT);
	}

	private static QwenAsrProvider asr(
			HttpClient client, String key, int maxAudio, int maxResponse, URI endpoint) {
		return new QwenAsrProvider(client, new ObjectMapper(), key, endpoint, "qwen-asr",
				Duration.ofSeconds(1), maxAudio, maxResponse);
	}

	private static QwenTtsProvider tts(HttpClient client, String key, int maxResponse, int maxAudio) {
		return tts(client, key, maxResponse, maxAudio, TTS_ENDPOINT);
	}

	private static QwenTtsProvider tts(
			HttpClient client, String key, int maxResponse, int maxAudio, URI endpoint) {
		return new QwenTtsProvider(client, new ObjectMapper(), key, endpoint, "qwen-tts",
				"Aiden", "English", Duration.ofSeconds(1), maxResponse, maxAudio);
	}

	private static void assertCode(String expected, org.junit.jupiter.api.function.Executable executable) {
		BusinessException exception = assertThrows(BusinessException.class, executable);
		assertEquals(expected, exception.code());
	}

	private static String body(HttpRequest request) {
		var publisher = request.bodyPublisher().orElseThrow();
		var bytes = new java.io.ByteArrayOutputStream();
		var complete = new CompletableFuture<Void>();
		publisher.subscribe(new Flow.Subscriber<>() {
			@Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(1); }
			@Override public void onNext(ByteBuffer item) {
				var copy = new byte[item.remaining()];
				item.get(copy);
				bytes.writeBytes(copy);
			}
			@Override public void onError(Throwable throwable) { complete.completeExceptionally(throwable); }
			@Override public void onComplete() { complete.complete(null); }
		});
		complete.join();
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
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
				throws IOException {
			requests.add(request);
			QueuedResponse response = responses.removeFirst();
			if (response.ioError()) throw new IOException("simulated network failure");
			byte[] raw = response.body() instanceof byte[] bytes
					? bytes : response.body().toString().getBytes(StandardCharsets.UTF_8);
			HttpResponse.BodySubscriber<T> subscriber = handler.apply(
					new ResponseInfo(response.status(), response.headers()));
			subscriber.onSubscribe(new NoopSubscription());
			subscriber.onNext(List.of(ByteBuffer.wrap(raw)));
			subscriber.onComplete();
			T handled;
			try {
				handled = subscriber.getBody().toCompletableFuture().join();
			} catch (java.util.concurrent.CompletionException exception) {
				if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
				throw exception;
			}
			return (HttpResponse<T>) new RecordedResponse<>(request, response.status(), handled,
					response.headers());
		}

		@Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
			HttpRequest request, HttpResponse.BodyHandler<T> handler) {
			try { return CompletableFuture.completedFuture(send(request, handler)); }
			catch (IOException exception) { return CompletableFuture.failedFuture(exception); }
		}
		@Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
			HttpRequest request, HttpResponse.BodyHandler<T> handler,
			HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return sendAsync(request, handler); }
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
		@Override public void request(long count) { }
		@Override public void cancel() { }
	}

	private record QueuedResponse(int status, Object body,
			Map<String, List<String>> headers, boolean ioError) {
		private QueuedResponse(int status, Object body) { this(status, body, Map.of(), false); }
		private QueuedResponse(int status, Object body, Map<String, List<String>> headers) {
			this(status, body, headers, false);
		}
	}

	private record ResponseInfo(int status, Map<String, List<String>> values)
			implements HttpResponse.ResponseInfo {
		@Override public int statusCode() { return status; }
		@Override public HttpHeaders headers() { return HttpHeaders.of(values, (n, v) -> true); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}

	private record RecordedResponse<T>(HttpRequest request, int status, T body,
			Map<String, List<String>> values) implements HttpResponse<T> {
		@Override public int statusCode() { return status; }
		@Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() { return HttpHeaders.of(values, (n, v) -> true); }
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return request.uri(); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}
}
