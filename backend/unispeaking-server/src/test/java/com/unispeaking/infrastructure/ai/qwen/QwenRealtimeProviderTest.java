package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.infrastructure.config.RealtimeProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import com.unispeaking.infrastructure.ai.aliyun.AliyunTtsProvider;
import com.unispeaking.infrastructure.ai.deepseek.DeepSeekLlmProvider;
import com.unispeaking.infrastructure.ai.doubao.DoubaoAsrProvider;
import com.unispeaking.infrastructure.ai.iflytek.IflytekScoringProvider;
import com.unispeaking.infrastructure.ai.minimax.MiniMaxTtsProvider;
import com.unispeaking.infrastructure.realtime.RealtimeCredentialIssuer;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.LlmResponseFormat;
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
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class QwenRealtimeProviderTest {

	@Test
	void registersTheActuallyConfiguredModelForEveryReplaceableAdapter() {
		RecordingHttpClient httpClient = new RecordingHttpClient();
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals(
				Set.of("qwen-custom-llm"),
				new QwenLlmProvider(
						httpClient,
						objectMapper,
						"key",
						URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
						"qwen-custom-llm",
						Duration.ofSeconds(20),
						1_048_576)
						.supportedModels());
		assertEquals(
				Set.of("deepseek-custom-llm"),
				new DeepSeekLlmProvider(
						httpClient,
						objectMapper,
						"key",
						URI.create("https://api.deepseek.com/chat/completions"),
						"deepseek-custom-llm",
						Duration.ofSeconds(20),
						1_048_576)
						.supportedModels());
		assertEquals(
				Set.of("qwen-custom-asr"),
				new QwenAsrProvider(
						httpClient,
						objectMapper,
						"key",
						URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
						"qwen-custom-asr",
						Duration.ofSeconds(20),
						7_340_032,
						1_048_576)
						.supportedModels());
		assertEquals(
				Set.of("doubao-custom-asr"),
				new DoubaoAsrProvider(
						httpClient,
						objectMapper,
						"key",
						"",
						"",
						"unispeaking",
						URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
						"doubao-custom-asr",
						Duration.ofSeconds(20),
						20_971_520,
						4_194_304)
						.supportedModels());
		assertEquals(
				Set.of("qwen-custom-tts"),
				new QwenTtsProvider(
						httpClient,
						objectMapper,
						"key",
						URI.create(
								"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
										+ "multimodal-generation/generation"),
						"qwen-custom-tts",
						"Cherry",
						"English",
						Duration.ofSeconds(20),
						10_485_760)
						.supportedModels());
		assertEquals(
				Set.of("aliyun-custom-tts"),
				new AliyunTtsProvider(
						httpClient,
						objectMapper,
						"key",
						URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
						"aliyun-custom-tts",
						"loongemily_v3",
						"wav",
						24_000,
						Duration.ofSeconds(20),
						10_485_760)
						.supportedModels());
		assertEquals(
				Set.of("minimax-custom-tts"),
				new MiniMaxTtsProvider(
						httpClient,
						objectMapper,
						"key",
						URI.create("https://api.minimaxi.com/v1/t2a_v2"),
						"minimax-custom-tts",
						"male-qn-qingse",
						"wav",
						32_000,
						128_000,
						Duration.ofSeconds(20),
						10_485_760)
						.supportedModels());
	}

	@Test
	void exchangesOfferAndAnswerSdpWithTheTemporaryBearerCredential()
			throws IOException, InterruptedException {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(200, "answer-sdp"));
		RealtimeProperties properties = new RealtimeProperties(
				"",
				"workspace-123",
				"qwen3.5-omni-flash-realtime",
				"cn-beijing",
				"https://dashscope.aliyuncs.com/api/v1/tokens",
				300,
				Duration.ofSeconds(10),
				Duration.ofSeconds(20),
				1_048_576);
		properties.validate();
		QwenRealtimeProvider provider = new QwenRealtimeProvider(
				httpClient,
				properties,
				mock(RealtimeCredentialIssuer.class));
		String result = provider.exchangeRealtimeSdp(
				"qwen3.5-omni-flash-realtime",
				"offer-sdp",
				"temporary-token");

		HttpRequest request = httpClient.requests.getFirst();
		assertEquals(
				"https://workspace-123.cn-beijing.maas.aliyuncs.com/api/v1/webrtc/realtime?model=qwen3.5-omni-flash-realtime",
				request.uri().toString());
		assertEquals("Bearer temporary-token",
				request.headers().firstValue("Authorization").orElseThrow());
		assertEquals("application/sdp",
				request.headers().firstValue("Content-Type").orElseThrow());
		assertEquals("offer-sdp", readBody(request));
		assertEquals("answer-sdp", result);
		assertEquals(1, httpClient.requests.size());
	}

	@Test
	void capturesOfficialRequestIdFromRealtimeResponseHeaders() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				"answer-sdp",
				Map.of("x-request-id", List.of("official-request-01"))));
		QwenRealtimeProvider provider = provider(httpClient, mock(RealtimeCredentialIssuer.class));
		RealtimeCredential credential = freshCredential("temporary-token");

		var result = provider.connect(new RealtimeConnectCommand(
				"qwen3.5-omni-flash-realtime", "offer-sdp", "user-1", "session-1",
				"scene-1", SceneType.FREE_CHAT, "Cherry"), credential);

		assertEquals("answer-sdp", result.answerSdp());
		assertEquals("official-request-01", result.traceId());
	}

	@Test
	void retriesTransientServerErrorOnceWithAFreshTemporaryKey()
			throws IOException, InterruptedException {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(503, "unavailable"),
				new QueuedResponse(200, "answer-sdp"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		when(issuer.issue(any())).thenReturn(freshCredential("fresh-token"));
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		String result = provider.exchangeRealtimeSdp(
				"qwen3.5-omni-flash-realtime",
				"offer-sdp",
				"temporary-token");

		assertEquals("answer-sdp", result);
		assertEquals(2, httpClient.requests.size());
		assertEquals("Bearer temporary-token",
				httpClient.requests.get(0).headers().firstValue("Authorization").orElseThrow());
		assertEquals("Bearer fresh-token",
				httpClient.requests.get(1).headers().firstValue("Authorization").orElseThrow());
		verify(issuer).issue(ProviderType.QWEN);
	}

	@Test
	void retriesRateLimitOnceWithAFreshTemporaryKey()
			throws IOException, InterruptedException {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(429, "rate limited"),
				new QueuedResponse(200, "answer-sdp"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		when(issuer.issue(any())).thenReturn(freshCredential("fresh-token"));
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		String result = provider.exchangeRealtimeSdp(
				"qwen3.5-omni-flash-realtime",
				"offer-sdp",
				"temporary-token");

		assertEquals("answer-sdp", result);
		assertEquals(2, httpClient.requests.size());
		verify(issuer).issue(ProviderType.QWEN);
	}

	@Test
	void retriesIoErrorOnceWithAFreshTemporaryKey()
			throws IOException, InterruptedException {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				QueuedResponse.ioError(),
				new QueuedResponse(200, "answer-sdp"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		when(issuer.issue(any())).thenReturn(freshCredential("fresh-token"));
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		String result = provider.exchangeRealtimeSdp(
				"qwen3.5-omni-flash-realtime",
				"offer-sdp",
				"temporary-token");

		assertEquals("answer-sdp", result);
		assertEquals(2, httpClient.requests.size());
		verify(issuer).issue(ProviderType.QWEN);
	}

	@Test
	void doesNotRetryClientErrorStatus() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(400, "bad offer"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						"qwen3.5-omni-flash-realtime",
						"offer-sdp",
						"temporary-token"));

		assertEquals("QWEN_SIGNALING_FAILED", exception.code());
		assertTrue(exception.getMessage().contains("bad offer"));
		assertEquals(1, httpClient.requests.size());
		verify(issuer, never()).issue(any());
	}

	@Test
	void throwsAfterRetryWhenTransientFailurePersists() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(503, "unavailable"),
				new QueuedResponse(502, "bad gateway"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		when(issuer.issue(any())).thenReturn(freshCredential("fresh-token"));
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						"qwen3.5-omni-flash-realtime",
						"offer-sdp",
						"temporary-token"));

		assertEquals("QWEN_SIGNALING_FAILED", exception.code());
		assertEquals(2, httpClient.requests.size());
		verify(issuer).issue(ProviderType.QWEN);
	}

	@Test
	void rejectsInvalidRealtimeInputsAndConfigurationBeforeOpeningHttpConnections() {
		RecordingHttpClient httpClient = new RecordingHttpClient();
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		assertEquals("INVALID_SDP", assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH, "  ", "token")).code());
		assertEquals("QWEN_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH, "offer", "  ")).code());
		assertEquals("QWEN_REALTIME_MODEL_NOT_SUPPORTED", assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						"qwen-other-model", "offer", "token")).code());

		RealtimeProperties missingWorkspace = new RealtimeProperties(
				"", "", "qwen3.5-omni-flash-realtime", "cn-beijing",
				"https://dashscope.aliyuncs.com/api/v1/tokens", 300,
				Duration.ofSeconds(10), Duration.ofSeconds(20), 1_048_576);
		missingWorkspace.validate();
		QwenRealtimeProvider unconfigured = new QwenRealtimeProvider(
				httpClient, missingWorkspace, issuer);
		assertEquals("QWEN_WORKSPACE_OR_MODEL_MISSING", assertThrows(
				BusinessException.class,
				() -> unconfigured.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH, "offer", "token")).code());

		assertTrue(httpClient.requests.isEmpty());
		verify(issuer, never()).issue(any());
	}

	@Test
	void retriesAThreeHundredResponseAndUsesTheFreshCredential() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(302, "redirect"),
				new QueuedResponse(200, "answer-sdp"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		when(issuer.issue(any())).thenReturn(freshCredential("fresh-token"));
		QwenRealtimeProvider provider = provider(httpClient, issuer);

		assertEquals("answer-sdp", provider.exchangeRealtimeSdp(
				AiProviderRegistry.QWEN_REALTIME_FLASH, "offer-sdp", "temporary-token"));
		assertEquals(2, httpClient.requests.size());
		assertEquals("Bearer temporary-token",
				httpClient.requests.get(0).headers().firstValue("Authorization").orElseThrow());
		assertEquals("Bearer fresh-token",
				httpClient.requests.get(1).headers().firstValue("Authorization").orElseThrow());
		verify(issuer).issue(ProviderType.QWEN);
	}

	@Test
	void rejectsAnOversizedAnswerWithoutRetryingTheSignalingRequest() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(200, "123456789"));
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		QwenRealtimeProvider provider = new QwenRealtimeProvider(
				httpClient,
				new RealtimeProperties(
						"", "workspace-123", "qwen3.5-omni-flash-realtime", "cn-beijing",
						"https://dashscope.aliyuncs.com/api/v1/tokens", 300,
						Duration.ofSeconds(10), Duration.ofSeconds(20), 8),
				issuer);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH, "offer-sdp", "token"));

		assertEquals("QWEN_ANSWER_TOO_LARGE", exception.code());
		assertEquals(1, httpClient.requests.size());
		verify(issuer, never()).issue(any());
	}

	@Test
	void mapsInterruptedSignalingCallsWithoutRetryingAndRestoresInterruptStatus() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(httpClient)
				.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		QwenRealtimeProvider interrupted = new QwenRealtimeProvider(
				httpClient,
				new RealtimeProperties(
						"", "workspace-123", "qwen3.5-omni-flash-realtime", "cn-beijing",
						"https://dashscope.aliyuncs.com/api/v1/tokens", 300,
						Duration.ofSeconds(10), Duration.ofSeconds(20), 1_048_576),
				issuer);

		Thread.interrupted();
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> interrupted.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH, "offer-sdp", "token"));

		assertEquals("QWEN_SIGNALING_INTERRUPTED", exception.code());
		assertTrue(Thread.currentThread().isInterrupted());
		verify(issuer, never()).issue(any());
		Thread.interrupted();
	}

	private QwenRealtimeProvider provider(
			RecordingHttpClient httpClient,
			RealtimeCredentialIssuer issuer) {
		RealtimeProperties properties = new RealtimeProperties(
				"",
				"workspace-123",
				"qwen3.5-omni-flash-realtime",
				"cn-beijing",
				"https://dashscope.aliyuncs.com/api/v1/tokens",
				300,
				Duration.ofSeconds(10),
				Duration.ofSeconds(20),
				1_048_576);
		properties.validate();
		return new QwenRealtimeProvider(httpClient, properties, issuer);
	}

	private RealtimeCredential freshCredential(String token) {
		return new RealtimeCredential(token, Instant.now().plusSeconds(300));
	}

	@Test
	void executesQwenLlmTaskWithTheServerConfiguredCredential() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"choices":[{"message":{"content":"{\\"answer\\":\\"ok\\"}"}}]}
				""")));
		QwenLlmProvider provider = new QwenLlmProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				1_048_576);

		String response = provider.executeLlmTask("Return JSON.", null);

		assertEquals("{\"answer\":\"ok\"}", response);
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals("Bearer dashscope-key",
				request.headers().firstValue("Authorization").orElseThrow());
		String body = readBody(request);
		assertTrue(body.contains("\"model\":\"qwen3.5-plus\""));
		assertTrue(body.contains("\"content\":\"Return JSON.\""));
		assertTrue(body.contains("\"enable_thinking\":false"));
		assertFalse(body.contains("dashscope-key"));
		assertFalse(httpClient.bodyCompletedOnSubscribe);
	}

	@Test
	void prefersOfficialResponseHeaderForQwenLlmRequestId() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"id":"body-request-id","choices":[{"message":{"content":"ok"}}]}
				"""),
				Map.of("x-request-id", List.of("header-request-id"))));
		QwenLlmProvider provider = new QwenLlmProvider(
				httpClient, new ObjectMapper(), "dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3.5-plus", Duration.ofSeconds(20), 1_048_576);

		var response = provider.executeLlmTaskMeasured("hello", null);

		assertEquals("header-request-id", response.providerRequestId());
	}

	@Test
	void addsJsonObjectResponseFormatOnlyWhenRequested() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(200, utf8(
						"{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}")));
		QwenLlmProvider provider = new QwenLlmProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				1_048_576);

		provider.executeLlmTask("Return JSON.", null, LlmResponseFormat.JSON_OBJECT);
		String body = readBody(httpClient.requests.getFirst());
		assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"));

		RecordingHttpClient textHttpClient = new RecordingHttpClient(
				new QueuedResponse(200, utf8(
						"{\"choices\":[{\"message\":{\"content\":\"plain\"}}]}")));
		QwenLlmProvider textProvider = new QwenLlmProvider(
				textHttpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				1_048_576);
		textProvider.executeLlmTask("Return text.", null);
		assertFalse(readBody(textHttpClient.requests.getFirst()).contains("response_format"));
	}

	@Test
	void mapsMalformedQwenResponseToABusinessError() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(200, utf8("not-json")));
		QwenLlmProvider provider = new QwenLlmProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				1_048_576);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("Return JSON.", null));

		assertEquals("QWEN_LLM_RESPONSE_INVALID", exception.code());
	}

	@Test
	void rejectsAnUntrustedQwenEndpointBeforeSendingCredentials() {
		RecordingHttpClient httpClient = new RecordingHttpClient();
		QwenLlmProvider provider = new QwenLlmProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://evil.example/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				1_048_576);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("Return JSON.", null));

		assertEquals("QWEN_LLM_ENDPOINT_INVALID", exception.code());
		assertTrue(httpClient.requests.isEmpty());
	}

	@Test
	void rejectsAnOversizedQwenResponseWhileReadingIt() {
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(200, new byte[11]));
		QwenLlmProvider provider = new QwenLlmProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				10);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("Return JSON.", null));

		assertEquals("QWEN_LLM_RESPONSE_TOO_LARGE", exception.code());
	}

	@Test
	void rejectsQwenLlmMissingCredentialAndBlankPromptBeforeSendingRequests() {
		RecordingHttpClient client = new RecordingHttpClient();
		QwenLlmProvider configured = qwenLlmProvider(client, "dashscope-key");
		QwenLlmProvider missingCredential = qwenLlmProvider(client, "");

		assertEquals("INVALID_LLM_PROMPT", assertThrows(
				BusinessException.class,
				() -> configured.executeLlmTask("  ", null)).code());
		assertEquals("QWEN_LLM_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> missingCredential.executeLlmTask("hello", null)).code());
		assertTrue(client.requests.isEmpty());
	}

	@Test
	void mapsQwenLlmHttpAndEmptyContentResponsesToBusinessErrors() {
		RecordingHttpClient httpFailure = new RecordingHttpClient(
				new QueuedResponse(429, utf8("rate limited")));
		assertEquals("QWEN_LLM_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> qwenLlmProvider(httpFailure, "dashscope-key")
						.executeLlmTask("hello", null)).code());

		RecordingHttpClient emptyContent = new RecordingHttpClient(
				new QueuedResponse(200, utf8("{\"choices\":[{\"message\":{\"content\":\"\"}}]}")));
		assertEquals("QWEN_LLM_EMPTY_RESPONSE", assertThrows(
				BusinessException.class,
				() -> qwenLlmProvider(emptyContent, "dashscope-key")
						.executeLlmTask("hello", null)).code());
	}

	@Test
	void mapsQwenLlmTransportAndInterruptedCallsWithoutLeakingCredentials() throws Exception {
		RecordingHttpClient ioFailure = new RecordingHttpClient(QueuedResponse.ioError());
		BusinessException ioException = assertThrows(
				BusinessException.class,
				() -> qwenLlmProvider(ioFailure, "dashscope-key")
						.executeLlmTask("hello", null));
		assertEquals("QWEN_LLM_IO_ERROR", ioException.code());
		assertFalse(ioException.getMessage().contains("dashscope-key"));

		HttpClient interruptedClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(interruptedClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		QwenLlmProvider interrupted = new QwenLlmProvider(
				interruptedClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create(
						"https://workspace-123.cn-beijing.maas.aliyuncs.com/"
								+ "compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				128);
		Thread.interrupted();
		BusinessException interruptedException = assertThrows(
				BusinessException.class,
				() -> interrupted.executeLlmTask("hello", null));
		assertEquals("QWEN_LLM_INTERRUPTED", interruptedException.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void generatesAliyunSpeechAndDownloadsTheReturnedAudio() {
		byte[] audio = new byte[] {1, 2, 3, 4};
		String audioUrl = "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/audio%2Bfile.mp3?Expires=1&Signature=a%2Fb%3D";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("""
						{"request_id":"req-1","output":{"finish_reason":"stop","audio":{"url":"%s"}}}
						""".formatted(audioUrl)),
						Map.of("x-request-id", List.of("different-header-request-id"))),
				new QueuedResponse(200, audio));
		AliyunTtsProvider provider = new AliyunTtsProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
				"cosyvoice-v3-flash",
				"loongemily_v3",
				"mp3",
				24_000,
				Duration.ofSeconds(20),
				1_048_576);

		var measured = provider.generateSpeechAudioMeasured(
				"Practice makes progress.",
				null,
				"configured-voice-only");
		byte[] response = measured.response();

		assertArrayEquals(new byte[] {1, 2, 3, 4}, response);
		assertEquals("req-1", measured.providerRequestId());
		assertEquals(24, measured.usage().inputCharacters());
		assertEquals(2, httpClient.requests.size());
		String requestBody = readBody(httpClient.requests.getFirst());
		assertTrue(requestBody.contains("\"model\":\"cosyvoice-v3-flash\""));
		assertTrue(requestBody.contains("\"voice\":\"loongemily_v3\""));
		assertTrue(requestBody.contains("\"language_hints\":[\"en\"]"));
		assertEquals(
				"https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/audio%2Bfile.mp3?Expires=1&Signature=a%2Fb%3D",
				httpClient.requests.get(1).uri().toString());
		assertFalse(httpClient.bodyCompletedOnSubscribe);
	}

	@Test
	void generatesQwenSpeechWithTheServerConfiguredCredential() {
		byte[] wav = wavWithSampleRate(24_000);
		String audioUrl =
				"https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/qwen.wav";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("""
						{"request_id":"qwen-tts-request-1","output":{"finish_reason":"stop","audio":{"url":"%s"}}}
						""".formatted(audioUrl)),
						Map.of("x-request-id", List.of("different-header-request-id"))),
				new QueuedResponse(200, wav));
		QwenTtsProvider provider = new QwenTtsProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create(
						"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
								+ "multimodal-generation/generation"),
				"qwen3-tts-flash",
				"Aiden",
				"English",
				Duration.ofSeconds(20),
				1_048_576);

		var measured = provider.generateSpeechAudioMeasured(
				"Practice makes progress.",
				"must-not-be-used",
				"Aiden");
		byte[] response = measured.response();
		byte[] cachedResponse = provider.generateSpeechAudio(
				"Practice makes progress.",
				"must-not-be-used");

		assertArrayEquals(wav, response);
		assertArrayEquals(wav, cachedResponse);
		assertEquals("qwen-tts-request-1", measured.providerRequestId());
		assertEquals(24, measured.usage().inputCharacters());
		assertEquals(2, httpClient.requests.size());
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals(
				"Bearer dashscope-key",
				request.headers().firstValue("Authorization").orElseThrow());
		assertEquals(
				"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
						+ "multimodal-generation/generation",
				request.uri().toString());
		String body = readBody(request);
		assertTrue(body.contains("\"model\":\"qwen3-tts-flash\""));
		assertTrue(body.contains("\"text\":\"Practice makes progress.\""));
		assertTrue(body.contains("\"voice\":\"Aiden\""));
		assertTrue(body.contains("\"language_type\":\"English\""));
		assertFalse(body.contains("dashscope-key"));
		assertFalse(body.contains("must-not-be-used"));
	}

	@Test
	void preservesQwenRequestIdAndCharactersWhenAudioDownloadFails() {
		String audioUrl =
				"https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/qwen.wav";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("""
						{"request_id":"qwen-billed-request","output":{"finish_reason":"stop","audio":{"url":"%s"}}}
						""".formatted(audioUrl))),
				new QueuedResponse(502, utf8("bad gateway")));
		QwenTtsProvider provider = new QwenTtsProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create(
						"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
								+ "multimodal-generation/generation"),
				"qwen3-tts-flash",
				"Aiden",
				"English",
				Duration.ofSeconds(20),
				1_048_576);

		MeteredProviderException exception = assertThrows(
				MeteredProviderException.class,
				() -> provider.generateSpeechAudioMeasured("Practice makes progress.", null));

		assertEquals("QWEN_TTS_AUDIO_DOWNLOAD_FAILED", exception.code());
		assertEquals("qwen-billed-request", exception.providerRequestId());
		assertEquals(24, exception.usage().inputCharacters());
		assertEquals(0, exception.usage().audioOutputSeconds());
		assertEquals(Boolean.TRUE, exception.retryable());
	}

	@Test
	void rejectsQwenTtsMissingCredentialAndBlankTextBeforeOpeningHttpConnections() {
		RecordingHttpClient client = new RecordingHttpClient();
		QwenTtsProvider configured = qwenTtsProvider(client, "dashscope-key", 128, 128);
		QwenTtsProvider missingCredential = qwenTtsProvider(client, "", 128, 128);

		assertEquals("INVALID_TTS_TEXT", assertThrows(
				BusinessException.class,
				() -> configured.generateSpeechAudio("  ", null)).code());
		assertEquals("QWEN_TTS_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> missingCredential.generateSpeechAudio("hello", null)).code());
		assertTrue(client.requests.isEmpty());
	}

	@Test
	void measuresDefaultVoiceResponsesWithoutAProviderRequestId() {
		byte[] wav = wavWithSampleRate(24_000);
		String url = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/default.wav";
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(200, utf8("{\"output\":{\"audio\":{\"url\":\""
						+ url + "\"}}}")),
				new QueuedResponse(200, wav));
		QwenTtsProvider provider = qwenTtsProvider(client, "dashscope-key", 128, 128);

		var measured = provider.generateSpeechAudioMeasured("hello", null);

		assertArrayEquals(wav, measured.response());
		assertEquals(null, measured.providerRequestId());
		assertEquals("NONE", measured.usage().source());
	}

	@Test
	void validatesSpringConfigurationAndRequiredQwenConstructorArguments() {
		URI endpoint = URI.create(
				"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
						+ "multimodal-generation/generation");
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new ObjectMapper(), "key", endpoint.toString(), "model", "voice", "English",
				0, 1, 128, 128));
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new ObjectMapper(), "key", endpoint.toString(), "model", "voice", "English",
				1, 0, 128, 128));
		assertEquals("QWEN_TTS_URL_INVALID", assertThrows(
				BusinessException.class,
				() -> new QwenTtsProvider(
						new ObjectMapper(), "key", "%%%", "model", "voice", "English",
						1, 1, 128, 128)).code());
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				null, new ObjectMapper(), "key", endpoint, "model", "voice", "English",
				Duration.ofSeconds(1), 128, 128));
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", endpoint, " ", "voice",
					"English", Duration.ofSeconds(1), 128, 128));
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", endpoint, "model", " ",
					"English", Duration.ofSeconds(1), 128, 128));
		assertThrows(IllegalArgumentException.class, () -> new QwenTtsProvider(
				new RecordingHttpClient(), new ObjectMapper(), "key", endpoint, "model", "voice",
					" ", Duration.ofSeconds(1), 128, 128));
	}

	@Test
	void handlesAnInvalidConfiguredAudioUriAndDefaultLimitFallback() {
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(200, utf8("{\"output\":{\"audio\":{\"url\":\"https://[::1\"}}}")));
		QwenTtsProvider provider = qwenTtsProvider(client, "key", 0, 0);
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.generateSpeechAudio("hello", null));
		assertEquals("QWEN_TTS_URL_INVALID", exception.code());
	}

	@Test
	void rejectsOversizedAndMalformedQwenTtsGenerationResponsesWhileReadingThem() {
		RecordingHttpClient oversized = new RecordingHttpClient(
				new QueuedResponse(200, utf8("0123456789")));
		BusinessException oversizedFailure = assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(oversized, "dashscope-key", 4, 128)
						.generateSpeechAudio("hello", null));
		assertEquals("QWEN_TTS_RESPONSE_TOO_LARGE", oversizedFailure.code());

		RecordingHttpClient missingAudioUrl = new RecordingHttpClient(
				new QueuedResponse(200, utf8("{\"output\":{\"audio\":{}}}")));
		BusinessException malformedFailure = assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(missingAudioUrl, "dashscope-key", 128, 128)
						.generateSpeechAudio("hello", null));
		assertEquals("QWEN_TTS_AUDIO_URL_MISSING", malformedFailure.code());
	}

	@Test
	void mapsQwenTtsGenerationHttpFailuresToRetryableBusinessErrors() {
		RecordingHttpClient client = new RecordingHttpClient(
				new QueuedResponse(503, utf8("temporarily unavailable")));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(client, "dashscope-key", 128, 128)
						.generateSpeechAudio("hello", null));

		assertEquals("QWEN_TTS_REQUEST_FAILED", exception.code());
	}

	@Test
	void rejectsUntrustedQwenTtsAudioUrlsAndInvalidDownloadedAudio() {
		RecordingHttpClient untrustedUrl = new RecordingHttpClient(
				new QueuedResponse(200, utf8(
						"{\"output\":{\"audio\":{\"url\":\"https://evil.example/audio.wav\"}}}")));
		assertEquals("QWEN_TTS_AUDIO_URL_UNTRUSTED", assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(untrustedUrl, "dashscope-key", 128, 128)
						.generateSpeechAudio("hello", null)).code());
		assertEquals(1, untrustedUrl.requests.size());

		String trustedUrl = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/audio.wav";
		RecordingHttpClient invalidWav = new RecordingHttpClient(
				new QueuedResponse(200, utf8(
						"{\"output\":{\"audio\":{\"url\":\"%s\"}}}".formatted(trustedUrl))),
				new QueuedResponse(200, new byte[] {1, 2, 3}));
		assertEquals("QWEN_TTS_AUDIO_INVALID", assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(invalidWav, "dashscope-key", 128, 128)
						.generateSpeechAudio("hello", null)).code());
	}

	@Test
	void mapsQwenTtsTransportFailureWithoutLeakingCredentials() {
		RecordingHttpClient client = new RecordingHttpClient(QueuedResponse.ioError());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(client, "dashscope-key", 128, 128)
						.generateSpeechAudio("hello", null));

		assertEquals("QWEN_TTS_IO_ERROR", exception.code());
		assertFalse(exception.getMessage().contains("dashscope-key"));
	}

	@Test
	void mapsQwenTtsInvalidEndpointAndTextLengthBeforeSendingRequests() {
		RecordingHttpClient endpointClient = new RecordingHttpClient();
		QwenTtsProvider invalidEndpoint = new QwenTtsProvider(
				endpointClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://evil.example/generation"),
				"qwen3-tts-flash",
				"Aiden",
				"English",
				Duration.ofSeconds(20),
				128,
				128);
		assertEquals("QWEN_TTS_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> invalidEndpoint.generateSpeechAudio("hello", null)).code());

		RecordingHttpClient longTextClient = new RecordingHttpClient();
		assertEquals("TTS_TEXT_TOO_LONG", assertThrows(
				BusinessException.class,
				() -> qwenTtsProvider(longTextClient, "dashscope-key", 128, 128)
						.generateSpeechAudio("x".repeat(5_001), null)).code());
		assertTrue(endpointClient.requests.isEmpty());
		assertTrue(longTextClient.requests.isEmpty());
	}

	@Test
	void resolvesProductVoiceAliasesAndCachesAudioByVoiceAndText() {
		byte[] wav = wavWithSampleRate(24_000);
		String audioUrl = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/voice.wav";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(200, utf8(
						"{\"output\":{\"audio\":{\"url\":\"%s\"}}}".formatted(audioUrl))),
				new QueuedResponse(200, wav));
		QwenTtsProvider provider = qwenTtsProvider(httpClient, "dashscope-key", 128, 128);

		assertArrayEquals(wav, provider.generateSpeechAudio("hello", null, "Harvey"));
		assertArrayEquals(wav, provider.generateSpeechAudio("hello", null, "Harvey"));

		assertEquals(2, httpClient.requests.size());
		assertTrue(readBody(httpClient.requests.getFirst()).contains("\"voice\":\"Neil\""));
	}

	@Test
	void mapsQwenTtsInterruptedCallsAndRestoresInterruptStatus() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(httpClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		QwenTtsProvider provider = new QwenTtsProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create(
						"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
								+ "multimodal-generation/generation"),
				"qwen3-tts-flash", "Aiden", "English", Duration.ofSeconds(20), 128, 128);

		Thread.interrupted();
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.generateSpeechAudio("hello", null));
		assertEquals("QWEN_TTS_INTERRUPTED", exception.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void preservesAliyunRequestIdAndCharactersWhenAudioDownloadFails() {
		String audioUrl =
				"https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/cosyvoice.mp3";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("""
						{"request_id":"cosyvoice-billed-request","output":{"finish_reason":"stop","audio":{"url":"%s"}}}
						""".formatted(audioUrl))),
				new QueuedResponse(500, utf8("server error")));
		AliyunTtsProvider provider = new AliyunTtsProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
				"cosyvoice-v3-flash",
				"loongemily_v3",
				"mp3",
				24_000,
				Duration.ofSeconds(20),
				1_048_576);

		MeteredProviderException exception = assertThrows(
				MeteredProviderException.class,
				() -> provider.generateSpeechAudioMeasured("Practice makes progress.", null));

		assertEquals("ALIYUN_TTS_AUDIO_DOWNLOAD_FAILED", exception.code());
		assertEquals("cosyvoice-billed-request", exception.providerRequestId());
		assertEquals(24, exception.usage().inputCharacters());
		assertEquals(0, exception.usage().audioOutputSeconds());
		assertEquals(Boolean.TRUE, exception.retryable());
	}

	@Test
	void rejectsOversizedAliyunAudioWhileReadingIt() {
		String audioUrl = "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/audio.mp3";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("""
						{"output":{"audio":{"url":"%s"}}}
						""".formatted(audioUrl))),
				new QueuedResponse(200, new byte[4]));
		AliyunTtsProvider provider = new AliyunTtsProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"),
				"cosyvoice-v3-flash",
				"loongemily_v3",
				"mp3",
				24_000,
				Duration.ofSeconds(20),
				3);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.generateSpeechAudio("Practice makes progress.", null));

		assertEquals("ALIYUN_TTS_AUDIO_TOO_LARGE", exception.code());
	}

	@Test
	void streamsWavToIflytekWithTheServerConfiguredCredential() throws Exception {
		String finalMessage = """
				{"header":{"code":0,"message":"success","status":2},
				 "payload":{"result":{"status":2,"text":"e30="}}}
				""";
		RecordingWebSocketConnector connector = new RecordingWebSocketConnector(finalMessage);
		IflytekScoringProvider provider = new IflytekScoringProvider(
				new ObjectMapper(),
				connector,
				"app-id",
				"api-key",
				"api-secret",
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				"en",
				"sent",
				Duration.ofSeconds(2),
				1_048_576,
				Duration.ZERO);

		String response = provider.evaluatePronunciation(
				"Practice makes progress.",
				wavWithSampleRate(16_000),
				null);

		assertEquals(finalMessage, response);
		assertTrue(connector.uri.getQuery().contains("authorization="));
		assertTrue(connector.uri.getQuery().contains(
				"host=cn-east-1.ws-api.xf-yun.com"));
		assertTrue(connector.frames.stream().anyMatch(
				frame -> frame.contains("\"encoding\":\"lame\"")));
		assertTrue(connector.frames.stream().anyMatch(
				frame -> frame.contains("\"status\":2")));
		assertTrue(connector.frames.stream().noneMatch(frame -> frame.contains("api-secret")));
		JsonNode startFrame = new ObjectMapper().readTree(connector.frames.getFirst());
		assertEquals(
				"Practice makes progress.",
				startFrame.path("parameter").path("st")
						.path("refText").asString());
		assertEquals(
				1,
				startFrame.path("parameter").path("st")
						.path("phoneme_output").asInt());
		assertEquals(
				"IPA88",
				startFrame.path("parameter").path("st")
						.path("dict_type").asString());
		assertEquals(
				0.2,
				startFrame.path("parameter").path("st")
						.path("slack").asDouble());
	}

	@Test
	void rejectsAnUntrustedIflytekEndpointBeforeConnecting() {
		RecordingWebSocketConnector connector = new RecordingWebSocketConnector("{}");
		IflytekScoringProvider provider = iflytekProvider(
				connector,
				URI.create("wss://evil.example/v1/private/s8e098720"),
				Duration.ofSeconds(2));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.evaluatePronunciation(
						"Practice makes progress.",
						wavWithSampleRate(16_000),
						null));

		assertEquals("IFLYTEK_SUNTONE_ENDPOINT_INVALID", exception.code());
		assertEquals(null, connector.uri);
	}

	@Test
	void appliesTheIflytekDeadlineToAudioFrameSends() {
		String finalMessage = """
				{"header":{"code":0,"status":2},
				 "payload":{"result":{"status":2,"text":"e30="}}}
				""";
		RecordingWebSocketConnector connector =
				new RecordingWebSocketConnector(finalMessage, true);
		IflytekScoringProvider provider = iflytekProvider(
				connector,
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				Duration.ofMillis(10));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.evaluatePronunciation(
						"Practice makes progress.",
						wavWithSampleRate(16_000),
						null));

		assertEquals("IFLYTEK_SUNTONE_TIMEOUT", exception.code());
	}

	@Test
	void rejectsWavAudioThatDoesNotMatchTheIflytekPcmContract() {
		RecordingWebSocketConnector connector = new RecordingWebSocketConnector("{}");
		IflytekScoringProvider provider = iflytekProvider(
				connector,
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				Duration.ofSeconds(2));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.evaluatePronunciation(
						"Practice makes progress.",
						wavWithSampleRate(44_100),
						null));

		assertEquals("INVALID_PRONUNCIATION_WAV", exception.code());
		assertEquals(null, connector.uri);
	}

	@Test
	void returnsTheFinalIflytekResponseWithoutParsingScores() {
		String finalMessage = """
				{"header":{"code":0,"status":2},
				 "payload":{"result":{"status":2,"text":"e30="}}}
				""";
		RecordingWebSocketConnector connector = new RecordingWebSocketConnector(finalMessage);
		IflytekScoringProvider provider = iflytekProvider(
				connector,
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				Duration.ofSeconds(2));

		String response = provider.evaluatePronunciation(
				"Practice makes progress.",
				wavWithSampleRate(16_000),
				null);

		assertEquals(finalMessage, response);
	}

	@Test
	void rejectsIflytekInvalidReferenceAndAudioBeforeOpeningTheWebSocket() {
		RecordingWebSocketConnector connector = new RecordingWebSocketConnector("{}");
		IflytekScoringProvider provider = iflytekProvider(
				connector,
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				Duration.ofSeconds(2));

		assertEquals("INVALID_PRONUNCIATION_REFERENCE", assertThrows(
				BusinessException.class,
				() -> provider.evaluatePronunciation("  ", wavWithSampleRate(16_000), null)).code());
		assertEquals("PRONUNCIATION_REFERENCE_TOO_LONG", assertThrows(
				BusinessException.class,
				() -> provider.evaluatePronunciation("x".repeat(4_097), wavWithSampleRate(16_000), null)).code());
		assertEquals("INVALID_AUDIO", assertThrows(
				BusinessException.class,
				() -> provider.evaluatePronunciation("hello", new byte[0], null)).code());
		assertEquals(null, connector.uri);
	}

	@Test
	void rejectsIflytekOversizedAudioAndMissingCredentialsBeforeConnecting() {
		URI endpoint = URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720");
		RecordingWebSocketConnector oversizedConnector = new RecordingWebSocketConnector("{}");
		IflytekScoringProvider oversized = iflytekProvider(
				oversizedConnector, endpoint, Duration.ofSeconds(2), "app", "key", "secret", 45);
		assertEquals("PRONUNCIATION_AUDIO_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> oversized.evaluatePronunciation("hello", wavWithSampleRate(16_000), null)).code());
		assertEquals(null, oversizedConnector.uri);

		RecordingWebSocketConnector credentialConnector = new RecordingWebSocketConnector("{}");
		IflytekScoringProvider missingCredentials = iflytekProvider(
				credentialConnector, endpoint, Duration.ofSeconds(2), "", "", "", 1_048_576);
		assertEquals("IFLYTEK_SUNTONE_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> missingCredentials.evaluatePronunciation(
						"hello", wavWithSampleRate(16_000), null)).code());
		assertEquals(null, credentialConnector.uri);
	}

	@Test
	void mapsIflytekProviderAndMalformedFinalResponsesToBusinessErrors() {
		URI endpoint = URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720");
		RecordingWebSocketConnector rateLimited = new RecordingWebSocketConnector(
				"{\"header\":{\"code\":11202,\"status\":2}}");
		BusinessException rateLimitFailure = assertThrows(
				BusinessException.class,
				() -> iflytekProvider(rateLimited, endpoint, Duration.ofSeconds(2))
						.evaluatePronunciation("hello", wavWithSampleRate(16_000), null));
		assertEquals("IFLYTEK_SUNTONE_RATE_LIMITED", rateLimitFailure.code());

		RecordingWebSocketConnector malformed = new RecordingWebSocketConnector("not-json");
		BusinessException malformedFailure = assertThrows(
				BusinessException.class,
				() -> iflytekProvider(malformed, endpoint, Duration.ofSeconds(2))
						.evaluatePronunciation("hello", wavWithSampleRate(16_000), null));
		assertEquals("IFLYTEK_SUNTONE_RESPONSE_INVALID", malformedFailure.code());
	}

	@Test
	void executesDeepSeekLlmTaskWithTheServerConfiguredCredential() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"choices":[{"message":{"content":"{\\"answer\\":\\"deepseek-ok\\"}"}}]}
				""")));
		DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
				httpClient,
				new ObjectMapper(),
				"deepseek-key",
				URI.create("https://api.deepseek.com/chat/completions"),
				"deepseek-v4-flash",
				Duration.ofSeconds(20),
				1_048_576);

		String response = provider.executeLlmTask("Return JSON.", null);

		assertEquals("{\"answer\":\"deepseek-ok\"}", response);
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals("Bearer deepseek-key",
				request.headers().firstValue("Authorization").orElseThrow());
		String body = readBody(request);
		assertTrue(body.contains("\"model\":\"deepseek-v4-flash\""));
		assertTrue(body.contains("\"content\":\"Return JSON.\""));
		assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"));
		assertFalse(body.contains("deepseek-key"));
		assertFalse(httpClient.bodyCompletedOnSubscribe);
	}

	@Test
	void rejectsAnUntrustedDeepSeekEndpointBeforeSendingCredentials() {
		RecordingHttpClient httpClient = new RecordingHttpClient();
		DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
				httpClient,
				new ObjectMapper(),
				"deepseek-key",
				URI.create("https://evil.example/chat/completions"),
				"deepseek-v4-flash",
				Duration.ofSeconds(20),
				1_048_576);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("Return JSON.", null));

		assertEquals("DEEPSEEK_LLM_ENDPOINT_INVALID", exception.code());
		assertTrue(httpClient.requests.isEmpty());
	}

	@Test
	void rejectsDeepSeekMissingCredentialAndBlankPromptBeforeSendingRequests() {
		RecordingHttpClient httpClient = new RecordingHttpClient();
		DeepSeekLlmProvider configured = new DeepSeekLlmProvider(
				httpClient, new ObjectMapper(), "deepseek-key",
				URI.create("https://api.deepseek.com/chat/completions"),
				"deepseek-v4-flash", Duration.ofSeconds(20), 128);
		DeepSeekLlmProvider missingCredential = new DeepSeekLlmProvider(
				httpClient, new ObjectMapper(), "",
				URI.create("https://api.deepseek.com/chat/completions"),
				"deepseek-v4-flash", Duration.ofSeconds(20), 128);

		assertEquals("INVALID_LLM_PROMPT", assertThrows(
				BusinessException.class,
				() -> configured.executeLlmTask("  ", null)).code());
		assertEquals("DEEPSEEK_LLM_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> missingCredential.executeLlmTask("hello", null)).code());
		assertTrue(httpClient.requests.isEmpty());
	}

	@Test
	void mapsDeepSeekHttpJsonAndResponseSizeFailures() {
		RecordingHttpClient httpFailure = new RecordingHttpClient(
				new QueuedResponse(503, utf8("temporarily unavailable")));
		assertEquals("DEEPSEEK_LLM_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(httpFailure, 128)
						.executeLlmTask("hello", null)).code());

		RecordingHttpClient malformed = new RecordingHttpClient(
				new QueuedResponse(200, utf8("not-json")));
		assertEquals("DEEPSEEK_LLM_RESPONSE_INVALID", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(malformed, 128)
						.executeLlmTask("hello", null)).code());

		RecordingHttpClient empty = new RecordingHttpClient(
				new QueuedResponse(200, utf8("{\"choices\":[{\"message\":{\"content\":\" \"}}]}")));
		assertEquals("DEEPSEEK_LLM_EMPTY_RESPONSE", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(empty, 128)
						.executeLlmTask("hello", null)).code());

		RecordingHttpClient oversized = new RecordingHttpClient(
				new QueuedResponse(200, utf8("123456789")));
		assertEquals("DEEPSEEK_LLM_RESPONSE_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(oversized, 8)
						.executeLlmTask("hello", null)).code());
	}

	@Test
	void recordsDeepSeekProviderUsageAndMapsTransportFailures() {
		RecordingHttpClient measured = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"id":"request-1","choices":[{"message":{"content":"answer"}}],
				"usage":{"prompt_tokens":11,"completion_tokens":7}}
				""")));
		var response = deepSeekProvider(measured, 128)
				.executeLlmTaskMeasured("hello", null);
		assertEquals("answer", response.response());
		assertEquals("request-1", response.providerRequestId());
		assertEquals(11, response.usage().inputTokens());
		assertEquals(7, response.usage().outputTokens());
		assertEquals("PROVIDER", response.usage().source());

		RecordingHttpClient estimated = new RecordingHttpClient(new QueuedResponse(
				200, utf8("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}")));
		assertEquals("ESTIMATED", deepSeekProvider(estimated, 128)
				.executeLlmTaskMeasured("hello", null).usage().source());

		RecordingHttpClient ioFailure = new RecordingHttpClient(QueuedResponse.ioError());
		BusinessException ioException = assertThrows(
				BusinessException.class,
				() -> deepSeekProvider(ioFailure, 128).executeLlmTask("hello", null));
		assertEquals("DEEPSEEK_LLM_IO_ERROR", ioException.code());
	}

	@Test
	void mapsInterruptedDeepSeekCallsAndRestoresInterruptStatus() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(httpClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
				httpClient, new ObjectMapper(), "deepseek-key",
				URI.create("https://api.deepseek.com/chat/completions"),
				"deepseek-v4-flash", Duration.ofSeconds(20), 128);

		Thread.interrupted();
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.executeLlmTask("hello", null));
		assertEquals("DEEPSEEK_LLM_INTERRUPTED", exception.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	private DeepSeekLlmProvider deepSeekProvider(
			RecordingHttpClient httpClient,
			int maxResponseBytes) {
		return new DeepSeekLlmProvider(
				httpClient, new ObjectMapper(), "deepseek-key",
				URI.create("https://api.deepseek.com/chat/completions"),
				"deepseek-v4-flash", Duration.ofSeconds(20), maxResponseBytes);
	}

	@Test
	void generatesMiniMaxSpeechFromHexAudio() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{
				  "data":{"audio":"494433040000","status":2},
				  "base_resp":{"status_code":0,"status_msg":"success"}
				}
				""")));
		MiniMaxTtsProvider provider = new MiniMaxTtsProvider(
				httpClient,
				new ObjectMapper(),
				"minimax-key",
				URI.create("https://api.minimaxi.com/v1/t2a_v2"),
				"speech-2.8-hd",
				"male-qn-qingse",
				"mp3",
				32_000,
				128_000,
				Duration.ofSeconds(20),
				1_048_576);

		byte[] response = provider.generateSpeechAudio("Practice makes progress.", null);

		assertArrayEquals(
				new byte[] {(byte) 0x49, (byte) 0x44, (byte) 0x33, (byte) 0x04, 0, 0},
				response);
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals("Bearer minimax-key",
				request.headers().firstValue("Authorization").orElseThrow());
		String body = readBody(request);
		assertTrue(body.contains("\"model\":\"speech-2.8-hd\""));
		assertTrue(body.contains("\"voice_id\":\"male-qn-qingse\""));
		assertTrue(body.contains("\"sample_rate\":32000"));
		assertTrue(body.contains("\"bitrate\":128000"));
		assertTrue(body.contains("\"output_format\":\"hex\""));
		assertFalse(body.contains("minimax-key"));
		assertFalse(httpClient.bodyCompletedOnSubscribe);
	}

	@Test
	void mapsMiniMaxProviderErrorsToABusinessError() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{
				  "data":null,
				  "trace_id":"trace-123",
				  "base_resp":{"status_code":1008,"status_msg":"insufficient balance"}
				}
				""")));
		MiniMaxTtsProvider provider = new MiniMaxTtsProvider(
				httpClient,
				new ObjectMapper(),
				"minimax-key",
				URI.create("https://api.minimaxi.com/v1/t2a_v2"),
				"speech-2.8-hd",
				"male-qn-qingse",
				"mp3",
				32_000,
				128_000,
				Duration.ofSeconds(20),
				1_048_576);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.generateSpeechAudio("Practice makes progress.", null));

		assertEquals("MINIMAX_TTS_REQUEST_FAILED", exception.code());
		assertTrue(exception.getMessage().contains("1008"));
		assertFalse(exception.getMessage().contains("minimax-key"));
	}

	@Test
	void transcribesAudioWithQwenAsrUsingAnInlineDataUrl() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"choices":[{"message":{"content":"Practice makes progress."}}]}
				""")));
		QwenAsrProvider provider = new QwenAsrProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions"),
				"qwen3-asr-flash",
				Duration.ofSeconds(20),
				7_340_032,
				1_048_576);

		String response = provider.convertAudioToText(
				new byte[] {1, 2, 3},
				null);

		assertEquals("Practice makes progress.", response);
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals("Bearer dashscope-key",
				request.headers().firstValue("Authorization").orElseThrow());
		String body = readBody(request);
		assertTrue(body.contains("\"model\":\"qwen3-asr-flash\""));
		assertTrue(body.contains("\"type\":\"input_audio\""));
		assertTrue(body.contains("data:audio/wav;base64,AQID"));
		assertTrue(body.contains("\"enable_itn\":true"));
		assertFalse(body.contains("dashscope-key"));
	}

	@Test
	void rejectsQwenAsrInvalidAudioAndMissingCredentialsBeforeSendingRequests() {
		RecordingHttpClient client = new RecordingHttpClient();
		QwenAsrProvider configured = qwenAsrProvider(client, "dashscope-key", 8, 128);
		QwenAsrProvider missingCredential = qwenAsrProvider(client, "", 8, 128);

		assertEquals("INVALID_AUDIO", assertThrows(
				BusinessException.class,
				() -> configured.convertAudioToText(new byte[0], null)).code());
		assertEquals("TRANSCRIPTION_AUDIO_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> configured.convertAudioToText(new byte[9], null)).code());
		assertEquals("QWEN_ASR_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> missingCredential.convertAudioToText(new byte[] {1}, null)).code());
		assertTrue(client.requests.isEmpty());
	}

	@Test
	void mapsQwenAsrHttpInvalidAndLimitedResponsesToBusinessErrors() {
		RecordingHttpClient httpFailure = new RecordingHttpClient(
				new QueuedResponse(502, utf8("bad gateway")));
		assertEquals("QWEN_ASR_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> qwenAsrProvider(httpFailure, "dashscope-key", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());

		RecordingHttpClient invalidJson = new RecordingHttpClient(
				new QueuedResponse(200, utf8("not-json")));
		assertEquals("QWEN_ASR_RESPONSE_INVALID", assertThrows(
				BusinessException.class,
				() -> qwenAsrProvider(invalidJson, "dashscope-key", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());

		RecordingHttpClient oversized = new RecordingHttpClient(
				new QueuedResponse(200, utf8("0123456789")));
		assertEquals("QWEN_ASR_RESPONSE_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> qwenAsrProvider(oversized, "dashscope-key", 128, 4)
						.convertAudioToText(new byte[] {1}, null)).code());
	}

	@Test
	void rejectsUntrustedQwenAsrEndpointsAndEmptyTranscriptions() {
		RecordingHttpClient untrustedClient = new RecordingHttpClient();
		QwenAsrProvider untrusted = new QwenAsrProvider(
				untrustedClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create("https://evil.example/compatible-mode/v1/chat/completions"),
				"qwen3-asr-flash",
				Duration.ofSeconds(20),
				128,
				128);
		assertEquals("QWEN_ASR_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> untrusted.convertAudioToText(new byte[] {1}, null)).code());
		assertTrue(untrustedClient.requests.isEmpty());

		RecordingHttpClient emptyResult = new RecordingHttpClient(
				new QueuedResponse(200, utf8("{\"choices\":[{\"message\":{\"content\":\"\"}}]}")));
		assertEquals("QWEN_ASR_RESULT_EMPTY", assertThrows(
				BusinessException.class,
				() -> qwenAsrProvider(emptyResult, "dashscope-key", 128, 128)
						.convertAudioToText(new byte[] {1}, null)).code());
	}

	@Test
	void mapsQwenAsrTransportFailuresWithoutIncludingCredentials() {
		RecordingHttpClient client = new RecordingHttpClient(QueuedResponse.ioError());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> qwenAsrProvider(client, "dashscope-key", 128, 128)
						.convertAudioToText(new byte[] {1}, null));

		assertEquals("QWEN_ASR_IO_ERROR", exception.code());
		assertFalse(exception.getMessage().contains("dashscope-key"));
	}

	@Test
	void mapsQwenAsrInterruptedCallsAndRestoresInterruptStatus() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(httpClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		QwenAsrProvider provider = new QwenAsrProvider(
				httpClient,
				new ObjectMapper(),
				"dashscope-key",
				URI.create(
						"https://workspace-123.cn-beijing.maas.aliyuncs.com/"
								+ "compatible-mode/v1/chat/completions"),
				"qwen3-asr-flash", Duration.ofSeconds(20), 128, 128);

		Thread.interrupted();
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(new byte[] {1}, null));
		assertEquals("QWEN_ASR_INTERRUPTED", exception.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void transcribesAudioWithDoubaoBigAsr() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("""
				{"result":{"text":"Practice makes progress."}}
				"""),
				Map.of("X-Api-Status-Code", List.of("20000000"))));
		DoubaoAsrProvider provider = new DoubaoAsrProvider(
				httpClient,
				new ObjectMapper(),
				"doubao-api-key",
				"",
				"",
				"unispeaking",
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
				"volc.bigasr.auc_turbo",
				Duration.ofSeconds(20),
				20_971_520,
				4_194_304);

		String response = provider.convertAudioToText(
				new byte[] {1, 2, 3},
				null);

		assertEquals("Practice makes progress.", response);
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals("doubao-api-key",
				request.headers().firstValue("X-Api-Key").orElseThrow());
		assertEquals("volc.bigasr.auc_turbo",
				request.headers().firstValue("X-Api-Resource-Id").orElseThrow());
		assertEquals("-1",
				request.headers().firstValue("X-Api-Sequence").orElseThrow());
		assertFalse(request.headers().firstValue("X-Api-Request-Id").orElseThrow().isBlank());
		String body = readBody(request);
		assertTrue(body.contains("\"uid\":\"unispeaking\""));
		assertTrue(body.contains("\"data\":\"AQID\""));
		assertTrue(body.contains("\"model_name\":\"bigmodel\""));
		assertFalse(body.contains("doubao-api-key"));
	}

	@Test
	void mapsDoubaoProviderStatusToARetryableBusinessError() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{}"),
				Map.of("X-Api-Status-Code", List.of("55000031"))));
		DoubaoAsrProvider provider = new DoubaoAsrProvider(
				httpClient,
				new ObjectMapper(),
				"doubao-api-key",
				"",
				"",
				"unispeaking",
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
				"volc.bigasr.auc_turbo",
				Duration.ofSeconds(20),
				20_971_520,
				4_194_304);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(
						new byte[] {1, 2, 3},
						null));

		assertEquals("DOUBAO_ASR_REQUEST_FAILED", exception.code());
		assertTrue(exception.getMessage().contains("55000031"));
	}

	@Test
	void usesLegacyDoubaoCredentialsWhenApiKeyIsAbsent() {
		RecordingHttpClient httpClient = new RecordingHttpClient(new QueuedResponse(
				200,
				utf8("{\"result\":{\"text\":\"legacy-ok\"}}"),
				Map.of("X-Api-Status-Code", List.of("20000000"))));
		DoubaoAsrProvider provider = new DoubaoAsrProvider(
				httpClient, new ObjectMapper(), "", "app-key", "access-key",
				"legacy-user",
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
				"resource", Duration.ofSeconds(20), 128, 128);

		assertEquals("legacy-ok", provider.convertAudioToText(new byte[] {1, 2}, null));
		HttpRequest request = httpClient.requests.getFirst();
		assertEquals("app-key", request.headers().firstValue("X-Api-App-Key").orElseThrow());
		assertEquals("access-key", request.headers().firstValue("X-Api-Access-Key").orElseThrow());
		assertTrue(request.headers().firstValue("X-Api-Key").isEmpty());
	}

	@Test
	void rejectsDoubaoInvalidInputsCredentialsEndpointAndResponseSize() {
		RecordingHttpClient httpClient = new RecordingHttpClient();
		DoubaoAsrProvider provider = doubaoProvider(httpClient, "doubao-key", 128, 128,
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"));
		assertEquals("INVALID_AUDIO", assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(new byte[0], null)).code());
		assertEquals("TRANSCRIPTION_AUDIO_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(
						new RecordingHttpClient(), "doubao-key", 1, 128,
						URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"))
						.convertAudioToText(new byte[] {1, 2}, null)).code());

		DoubaoAsrProvider missingCredential = doubaoProvider(
				httpClient, "", 128, 128,
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"));
		assertEquals("DOUBAO_ASR_CREDENTIAL_MISSING", assertThrows(
				BusinessException.class,
				() -> missingCredential.convertAudioToText(new byte[] {1}, null)).code());

		DoubaoAsrProvider untrusted = doubaoProvider(
				httpClient, "doubao-key", 128, 128,
				URI.create("https://evil.example/api/v3/auc/bigmodel/recognize/flash"));
		assertEquals("DOUBAO_ASR_ENDPOINT_INVALID", assertThrows(
				BusinessException.class,
				() -> untrusted.convertAudioToText(new byte[] {1}, null)).code());
		assertTrue(httpClient.requests.isEmpty());

		DoubaoAsrProvider oversized = doubaoProvider(
				new RecordingHttpClient(new QueuedResponse(200, utf8("123456789"),
						Map.of("X-Api-Status-Code", List.of("20000000")))),
				"doubao-key", 128, 8,
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"));
		assertEquals("DOUBAO_ASR_RESPONSE_TOO_LARGE", assertThrows(
				BusinessException.class,
				() -> oversized.convertAudioToText(new byte[] {1}, null)).code());
	}

	@Test
	void mapsDoubaoHttpJsonEmptyAndTransportFailures() {
		URI endpoint = URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash");
		RecordingHttpClient httpFailure = new RecordingHttpClient(new QueuedResponse(500, utf8("error")));
		assertEquals("DOUBAO_ASR_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(httpFailure, "key", 128, 128, endpoint)
						.convertAudioToText(new byte[] {1}, null)).code());

		RecordingHttpClient missingStatus = new RecordingHttpClient(new QueuedResponse(
				200, utf8("{}")));
		assertEquals("DOUBAO_ASR_REQUEST_FAILED", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(missingStatus, "key", 128, 128, endpoint)
						.convertAudioToText(new byte[] {1}, null)).code());

		RecordingHttpClient empty = new RecordingHttpClient(new QueuedResponse(
				200, utf8("{\"result\":{\"text\":\"\"}}"),
				Map.of("X-Api-Status-Code", List.of("20000000"))));
		assertEquals("DOUBAO_ASR_RESULT_EMPTY", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(empty, "key", 128, 128, endpoint)
						.convertAudioToText(new byte[] {1}, null)).code());

		RecordingHttpClient malformed = new RecordingHttpClient(new QueuedResponse(
				200, utf8("not-json"), Map.of("X-Api-Status-Code", List.of("20000000"))));
		assertEquals("DOUBAO_ASR_RESPONSE_INVALID", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(malformed, "key", 128, 128, endpoint)
						.convertAudioToText(new byte[] {1}, null)).code());

		RecordingHttpClient ioFailure = new RecordingHttpClient(QueuedResponse.ioError());
		assertEquals("DOUBAO_ASR_IO_ERROR", assertThrows(
				BusinessException.class,
				() -> doubaoProvider(ioFailure, "key", 128, 128, endpoint)
						.convertAudioToText(new byte[] {1}, null)).code());
	}

	@Test
	void mapsInterruptedDoubaoCallsAndRestoresInterruptStatus() throws Exception {
		HttpClient httpClient = mock(HttpClient.class);
		doThrow(new InterruptedException("cancelled"))
				.when(httpClient)
				.send(any(HttpRequest.class),
						org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
		DoubaoAsrProvider provider = new DoubaoAsrProvider(
				httpClient, new ObjectMapper(), "key", "", "", "user",
				URI.create("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"),
				"resource", Duration.ofSeconds(20), 128, 128);

		Thread.interrupted();
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.convertAudioToText(new byte[] {1}, null));
		assertEquals("DOUBAO_ASR_INTERRUPTED", exception.code());
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	private DoubaoAsrProvider doubaoProvider(
			RecordingHttpClient httpClient,
			String apiKey,
			int maxAudioBytes,
			int maxResponseBytes,
			URI endpoint) {
		return new DoubaoAsrProvider(
				httpClient, new ObjectMapper(), apiKey, "", "", "user", endpoint,
				"resource", Duration.ofSeconds(20), maxAudioBytes, maxResponseBytes);
	}

	private QwenTtsProvider qwenTtsProvider(
			RecordingHttpClient httpClient,
			String apiKey,
			int maxResponseBytes,
			int maxAudioBytes) {
		return new QwenTtsProvider(
				httpClient,
				new ObjectMapper(),
				apiKey,
				URI.create(
						"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
								+ "multimodal-generation/generation"),
				"qwen3-tts-flash",
				"Aiden",
				"English",
				Duration.ofSeconds(20),
				maxResponseBytes,
				maxAudioBytes);
	}

	private QwenLlmProvider qwenLlmProvider(
			RecordingHttpClient httpClient,
			String apiKey) {
		return new QwenLlmProvider(
				httpClient,
				new ObjectMapper(),
				apiKey,
				URI.create(
						"https://workspace-123.cn-beijing.maas.aliyuncs.com/"
								+ "compatible-mode/v1/chat/completions"),
				"qwen3.5-plus",
				Duration.ofSeconds(20),
				128);
	}

	private QwenAsrProvider qwenAsrProvider(
			RecordingHttpClient httpClient,
			String apiKey,
			int maxAudioBytes,
			int maxResponseBytes) {
		return new QwenAsrProvider(
				httpClient,
				new ObjectMapper(),
				apiKey,
				URI.create(
						"https://workspace-123.cn-beijing.maas.aliyuncs.com/"
								+ "compatible-mode/v1/chat/completions"),
				"qwen3-asr-flash",
				Duration.ofSeconds(20),
				maxAudioBytes,
				maxResponseBytes);
	}

	private IflytekScoringProvider iflytekProvider(
			RecordingWebSocketConnector connector,
			URI endpoint,
			Duration readTimeout) {
		return iflytekProvider(
				connector, endpoint, readTimeout, "app-id", "api-key", "api-secret", 1_048_576);
	}

	private IflytekScoringProvider iflytekProvider(
			RecordingWebSocketConnector connector,
			URI endpoint,
			Duration readTimeout,
			String appId,
			String apiKey,
			String apiSecret,
			int maxAudioBytes) {
		return new IflytekScoringProvider(
				new ObjectMapper(),
				connector,
				appId,
				apiKey,
				apiSecret,
				endpoint,
				"en",
				"sent",
				readTimeout,
				maxAudioBytes,
				Duration.ZERO);
	}

	private static byte[] utf8(String text) {
		return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private byte[] wavWithSampleRate(int sampleRate) {
		ByteBuffer wav = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(38);
		wav.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(16);
		wav.putShort((short) 1);
		wav.putShort((short) 1);
		wav.putInt(sampleRate);
		wav.putInt(sampleRate * 2);
		wav.putShort((short) 2);
		wav.putShort((short) 16);
		wav.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(2);
		wav.putShort((short) 0);
		return wav.array();
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
		return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
	}

	private List<Byte> toByteList(byte[] bytes) {
		List<Byte> result = new ArrayList<>();
		for (byte value : bytes) {
			result.add(value);
		}
		return result;
	}

	private Byte[] box(byte[] bytes) {
		Byte[] result = new Byte[bytes.length];
		for (int index = 0; index < bytes.length; index++) {
			result[index] = bytes[index];
		}
		return result;
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
					: response.body().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(
					new RecordedResponseInfo(response.statusCode(), response.headers()));
			subscriber.onSubscribe(new Flow.Subscription() {
				@Override public void request(long n) { }
				@Override public void cancel() { }
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
					request,
					response.statusCode(),
					handledBody,
					response.headers());
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

	private record QueuedResponse(
			int statusCode,
			Object body,
			Map<String, List<String>> headers,
			boolean failWithIoError) {

		private QueuedResponse(int statusCode, Object body) {
			this(statusCode, body, Map.of(), false);
		}

		private QueuedResponse(int statusCode, Object body, Map<String, List<String>> headers) {
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
		@Override public HttpHeaders headers() {
			return HttpHeaders.of(responseHeaders, (name, value) -> true);
		}
		@Override public HttpClient.Version version() {
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

	private static final class RecordingWebSocketConnector
			implements IflytekScoringProvider.WebSocketConnector {

		private final String finalMessage;
		private final boolean delayFirstSend;
		private final List<String> frames = new ArrayList<>();
		private URI uri;

		private RecordingWebSocketConnector(String finalMessage) {
			this(finalMessage, false);
		}

		private RecordingWebSocketConnector(String finalMessage, boolean delayFirstSend) {
			this.finalMessage = finalMessage;
			this.delayFirstSend = delayFirstSend;
		}

		@Override
		public CompletableFuture<java.net.http.WebSocket> connect(
				URI uri,
				java.net.http.WebSocket.Listener listener) {
			this.uri = uri;
			RecordingWebSocket socket = new RecordingWebSocket(
					listener,
					frames,
					finalMessage,
					delayFirstSend);
			listener.onOpen(socket);
			return CompletableFuture.completedFuture(socket);
		}
	}

	private static final class RecordingWebSocket implements java.net.http.WebSocket {

		private final Listener listener;
		private final List<String> frames;
		private final String finalMessage;
		private final boolean delayFirstSend;
		private boolean inputClosed;
		private boolean outputClosed;

		private RecordingWebSocket(
				Listener listener,
				List<String> frames,
				String finalMessage,
				boolean delayFirstSend) {
			this.listener = listener;
			this.frames = frames;
			this.finalMessage = finalMessage;
			this.delayFirstSend = delayFirstSend;
		}

		@Override
		public CompletableFuture<java.net.http.WebSocket> sendText(
				CharSequence data,
				boolean last) {
			String frame = data.toString();
			frames.add(frame);
			if (delayFirstSend && frames.size() == 1) {
				CompletableFuture<java.net.http.WebSocket> delayed = new CompletableFuture<>();
				CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
						.execute(() -> delayed.complete(this));
				return delayed;
			}
			if (frame.contains("\"status\":2")) {
				listener.onText(this, finalMessage, true);
			}
			return CompletableFuture.completedFuture(this);
		}

		@Override
		public CompletableFuture<java.net.http.WebSocket> sendBinary(ByteBuffer data, boolean last) {
			return CompletableFuture.completedFuture(this);
		}

		@Override
		public CompletableFuture<java.net.http.WebSocket> sendPing(ByteBuffer message) {
			return CompletableFuture.completedFuture(this);
		}

		@Override
		public CompletableFuture<java.net.http.WebSocket> sendPong(ByteBuffer message) {
			return CompletableFuture.completedFuture(this);
		}

		@Override
		public CompletableFuture<java.net.http.WebSocket> sendClose(int statusCode, String reason) {
			outputClosed = true;
			inputClosed = true;
			return CompletableFuture.completedFuture(this);
		}

		@Override public void request(long n) { }
		@Override public String getSubprotocol() { return ""; }
		@Override public boolean isOutputClosed() { return outputClosed; }
		@Override public boolean isInputClosed() { return inputClosed; }
		@Override public void abort() {
			outputClosed = true;
			inputClosed = true;
		}
	}
}
