package com.unispeaking.infrastructure.ai.qwen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.ai.aliyun.AliyunTtsProvider;
import com.unispeaking.infrastructure.ai.deepseek.DeepSeekLlmProvider;
import com.unispeaking.infrastructure.ai.doubao.DoubaoAsrProvider;
import com.unispeaking.infrastructure.ai.iflytek.IflytekScoringProvider;
import com.unispeaking.infrastructure.ai.minimax.MiniMaxTtsProvider;
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
		QwenRealtimeProvider provider = new QwenRealtimeProvider(httpClient, properties);
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
	void generatesAliyunSpeechAndDownloadsTheReturnedAudio() {
		byte[] audio = new byte[] {1, 2, 3, 4};
		String audioUrl = "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test/audio%2Bfile.mp3?Expires=1&Signature=a%2Fb%3D";
		RecordingHttpClient httpClient = new RecordingHttpClient(
				new QueuedResponse(
						200,
						utf8("""
						{"request_id":"req-1","output":{"finish_reason":"stop","audio":{"url":"%s"}}}
						""".formatted(audioUrl))),
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

		byte[] response = provider.generateSpeechAudio("Practice makes progress.", null);

		assertArrayEquals(new byte[] {1, 2, 3, 4}, response);
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
						{"output":{"finish_reason":"stop","audio":{"url":"%s"}}}
						""".formatted(audioUrl))),
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

		byte[] response = provider.generateSpeechAudio(
				"Practice makes progress.",
				"must-not-be-used");
		byte[] cachedResponse = provider.generateSpeechAudio(
				"Practice makes progress.",
				"must-not-be-used");

		assertArrayEquals(wav, response);
		assertArrayEquals(wav, cachedResponse);
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

	private IflytekScoringProvider iflytekProvider(
			RecordingWebSocketConnector connector,
			URI endpoint,
			Duration readTimeout) {
		return new IflytekScoringProvider(
				new ObjectMapper(),
				connector,
				"app-id",
				"api-key",
				"api-secret",
				endpoint,
				"en",
				"sent",
				readTimeout,
				1_048_576,
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
				HttpResponse.BodyHandler<T> responseBodyHandler) {
			requests.add(request);
			QueuedResponse response = responses.removeFirst();
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
			return CompletableFuture.completedFuture(send(request, responseBodyHandler));
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(
				HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler,
				HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
			return CompletableFuture.completedFuture(send(request, responseBodyHandler));
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
			Map<String, List<String>> headers) {

		private QueuedResponse(int statusCode, Object body) {
			this(statusCode, body, Map.of());
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
