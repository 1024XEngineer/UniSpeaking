package com.unispeaking.infrastructure.ai.iflytek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.BusinessException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class IflytekScoringProviderTest {

	private static final URI OFFICIAL_ENDPOINT = URI.create(
			"wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720");
	private static final String FINAL_RESPONSE = """
			{"header":{"code":0,"message":"success","status":2},
			 "payload":{"result":{"status":2,"text":"e30="}}}
			""";

	@Test
	void evaluatesPronunciationAndSendsSmallAudioAsStartAndEmptyEndFrames()
			throws Exception {
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		IflytekScoringProvider provider = provider(connector);

		String response = provider.evaluatePronunciation(
				"  Practice makes progress.  ",
				wav(16_000, 2),
				null);

		assertEquals(FINAL_RESPONSE, response);
		assertEquals(2, connector.frames.size());
		JsonNode first = new ObjectMapper().readTree(connector.frames.get(0));
		JsonNode last = new ObjectMapper().readTree(connector.frames.get(1));
		assertEquals(0, first.path("header").path("status").asInt());
		assertEquals(0, first.path("payload").path("data").path("status").asInt());
		assertEquals(0, first.path("payload").path("seq").asInt());
		assertEquals("Practice makes progress.", first.path("parameter").path("st")
				.path("refText").asString());
		assertEquals("lame", first.path("payload").path("data").path("encoding").asString());
		assertEquals(2, last.path("header").path("status").asInt());
		assertEquals(2, last.path("payload").path("data").path("status").asInt());
		assertEquals(1, last.path("payload").path("data").path("seq").asInt());
		assertTrue(last.path("payload").path("data").path("frame_size").asInt() >= 0);
		assertTrue(connector.socket.outputClosed);
		assertTrue(connector.uri.getQuery().contains("authorization="));
		assertTrue(connector.uri.getQuery().contains("host=cn-east-1.ws-api.xf-yun.com"));
		assertFalse(connector.uri.toString().contains("api-secret"));
	}

	@Test
	void sendsMiddleAndFinalAudioFramesWithIncreasingSequences() throws Exception {
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		IflytekScoringProvider provider = provider(connector, Duration.ofSeconds(2), Duration.ZERO);

		provider.evaluatePronunciation("hello", wav(16_000, 1_000_000), null);

		assertTrue(connector.frames.size() > 2);
		JsonNode first = json(connector.frames.get(0));
		JsonNode middle = json(connector.frames.get(1));
		JsonNode last = json(connector.frames.get(connector.frames.size() - 1));
		assertEquals(0, first.path("payload").path("data").path("status").asInt());
		assertEquals(1, middle.path("payload").path("data").path("status").asInt());
		assertEquals(1, middle.path("payload").path("data").path("seq").asInt());
		assertEquals(2, last.path("payload").path("data").path("status").asInt());
		assertEquals(connector.frames.size() - 1,
				last.path("payload").path("data").path("seq").asInt());
		assertTrue(last.path("payload").path("data").path("frame_size").asInt() > 0);
	}

	@Test
	void signsEndpointUsingConfiguredApiKeyAndEscapedQueryValues() {
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		IflytekScoringProvider provider = new IflytekScoringProvider(
				new ObjectMapper(), connector, "app id", "key+/=", "secret",
				OFFICIAL_ENDPOINT, " en ", " sent ", Duration.ofSeconds(2),
				1_048_576, Duration.ZERO);

		provider.evaluatePronunciation("hello", wav(16_000, 2), null);

		assertNotNull(connector.uri);
		String authorization = queryParameter(connector.uri, "authorization");
		assertTrue(new String(Base64.getDecoder().decode(authorization), StandardCharsets.UTF_8)
				.contains("api_key=\"key+/=\""));
		assertTrue(connector.uri.getRawQuery().contains("authorization="));
		assertTrue(connector.uri.getRawQuery().contains("date="));
	}

	@Test
	void mapsHandshakeRejectionAndAbortsConnectedSocketOnFailure() {
		WebSocketHandshakeException handshake =
				new WebSocketHandshakeException(response(401));
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		connector.connectFailure = handshake;
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(connector).evaluatePronunciation("hello", wav(16_000, 2), null));

		assertEquals("IFLYTEK_SUNTONE_HANDSHAKE_REJECTED", exception.code());
		assertEquals(401, handshake.getResponse().statusCode());
	}

	@Test
	void mapsConnectionFailureAndAbortsSocketAfterSendFailure() {
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		connector.sendFailure = new IOExceptionFailure();
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(connector).evaluatePronunciation("hello", wav(16_000, 2), null));

		assertEquals("IFLYTEK_SUNTONE_CONNECTION_FAILED", exception.code());
		assertTrue(connector.socket.outputClosed);
	}

	@Test
	void mapsInterruptedConnectAndRestoresInterruptFlag() {
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		connector.connectFuture = new CompletableFuture<>();
		Thread.currentThread().interrupt();
		try {
			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> provider(connector).evaluatePronunciation("hello", wav(16_000, 2), null));
			assertEquals("IFLYTEK_SUNTONE_INTERRUPTED", exception.code());
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
	}

	@Test
	void mapsSocketErrorAndCloseBeforeFinalResponse() {
		RecordingConnector errorConnector = new RecordingConnector(FINAL_RESPONSE);
		errorConnector.notifyError = true;
		BusinessException error = assertThrows(
				BusinessException.class,
				() -> provider(errorConnector).evaluatePronunciation("hello", wav(16_000, 2), null));
		assertEquals("IFLYTEK_SUNTONE_CONNECTION_FAILED", error.code());

		RecordingConnector closeConnector = new RecordingConnector(FINAL_RESPONSE);
		closeConnector.closeOnFirstSend = true;
		BusinessException close = assertThrows(
				BusinessException.class,
				() -> provider(closeConnector).evaluatePronunciation("hello", wav(16_000, 2), null));
		assertEquals("IFLYTEK_SUNTONE_CONNECTION_CLOSED", close.code());
	}

	@Test
	void rejectsInvalidConfigurationAndMalformedWavBeforeConnecting() {
		assertEquals("iFlytek Suntone language is required", assertThrows(
				IllegalArgumentException.class,
				() -> new IflytekScoringProvider(new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE),
						"app", "key", "secret", OFFICIAL_ENDPOINT, " ", "sent",
						Duration.ofSeconds(1), 100, Duration.ZERO)).getMessage());
		assertThrows(IllegalArgumentException.class,
				() -> new IflytekScoringProvider(new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE),
						"app", "key", "secret", OFFICIAL_ENDPOINT, "en", "sent",
						Duration.ZERO, 100, Duration.ZERO));
		assertThrows(IllegalArgumentException.class,
				() -> new IflytekScoringProvider(new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE),
						"app", "key", "secret", OFFICIAL_ENDPOINT, "en", "sent",
						Duration.ofSeconds(1), 100, Duration.ofMillis(-1)));

		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider(connector).evaluatePronunciation("hello", new byte[] {'R', 'I', 'F', 'F'}, null));
		assertEquals("INVALID_PRONUNCIATION_WAV", exception.code());
		assertTrue(connector.uri == null);
	}

	@Test
	void mapsResponseErrorsAndOversizedResponse() {
		RecordingConnector quota = new RecordingConnector(
				"{\"header\":{\"code\":11200,\"status\":2}}");
		BusinessException quotaFailure = assertThrows(
				BusinessException.class,
				() -> provider(quota).evaluatePronunciation("hello", wav(16_000, 2), null));
		assertEquals("IFLYTEK_SUNTONE_NOT_AUTHORIZED", quotaFailure.code());

		RecordingConnector oversized = new RecordingConnector(FINAL_RESPONSE);
		oversized.responseParts = List.of("x".repeat(1_500_001));
		BusinessException sizeFailure = assertThrows(
				BusinessException.class,
				() -> provider(oversized).evaluatePronunciation("hello", wav(16_000, 2), null));
		assertEquals("IFLYTEK_SUNTONE_RESPONSE_TOO_LARGE", sizeFailure.code());
		assertTrue(oversized.socket.outputClosed);
	}

	@Test
	void usesCredentialOverridesForSigningAndRejectsMissingEffectiveCredentials() {
		RecordingConnector missing = new RecordingConnector(FINAL_RESPONSE);
		IflytekScoringProvider missingProvider = new IflytekScoringProvider(
				new ObjectMapper(), missing, "", "", "", OFFICIAL_ENDPOINT,
				"en", "sent", Duration.ofSeconds(2), 1_048_576, Duration.ZERO);
		BusinessException exception = assertThrows(
					BusinessException.class,
					() -> missingProvider.evaluatePronunciation("hello", wav(16_000, 2), null));
		assertEquals("IFLYTEK_SUNTONE_CREDENTIAL_MISSING", exception.code());
	}

	@Test
	void coversAllSuntoneProviderStatusMappings() {
		for (var status : List.of(
				new Object[] {11200, "IFLYTEK_SUNTONE_NOT_AUTHORIZED"},
				new Object[] {11201, "IFLYTEK_SUNTONE_DAILY_QUOTA_EXHAUSTED"},
				new Object[] {11202, "IFLYTEK_SUNTONE_RATE_LIMITED"},
				new Object[] {11203, "IFLYTEK_SUNTONE_AUTHORIZATION_EXPIRED"},
				new Object[] {99999, "IFLYTEK_SUNTONE_REQUEST_FAILED"})) {
			RecordingConnector connector = new RecordingConnector(
					"{\"header\":{\"code\":" + status[0] + ",\"status\":2}}");
			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> provider(connector).evaluatePronunciation(
							"hello", wav(16_000, 2), null));
			assertEquals(status[1], exception.code());
		}
	}

	@Test
	void coversPacingTimeoutAndWavChunkValidationBranches() {
		RecordingConnector connector = new RecordingConnector(FINAL_RESPONSE);
		IflytekScoringProvider paced = provider(
				connector, Duration.ofSeconds(2), Duration.ofMillis(1));
		assertEquals(FINAL_RESPONSE, paced.evaluatePronunciation(
				"hello", wav(16_000, 4_000), null));

		List<byte[]> invalidWavs = new ArrayList<>(List.of(
				wavWithChunk("fmt ", 8, 16),
				wavWithChunk("data", -1, 16),
				wavWithChunk("data", 3, 16),
				truncatedUnknownChunk(),
				truncatedRiff(),
				declaredRiffTooSmall(),
				wavWithoutData(),
				wavWithChunk("data", 0, 16),
				wavWithFormat(2, 16_000, 16, 1),
				wavWithFormat(1, 16_000, 16, 2),
				wavWithFormat(1, 8_000, 16, 1),
				wavWithFormat(1, 16_000, 8, 1)));
		for (int signatureIndex : new int[] {0, 1, 2, 3, 8, 9, 10, 11}) {
			byte[] corrupt = wav(16_000, 2);
			corrupt[signatureIndex] = 'X';
			invalidWavs.add(corrupt);
		}
		for (int index = 0; index < invalidWavs.size(); index++) {
			byte[] invalid = invalidWavs.get(index);
			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> provider(new RecordingConnector(FINAL_RESPONSE))
							.evaluatePronunciation("hello", invalid, null),
					"invalid WAV case " + index);
			assertEquals("INVALID_PRONUNCIATION_WAV", exception.code());
		}
	}

	@Test
	void coversCredentialAndEndpointOverridesAndTimeoutResponsePaths() {
		RecordingConnector endpointConnector = new RecordingConnector(FINAL_RESPONSE);
		IflytekScoringProvider endpointProvider = new IflytekScoringProvider(
				new ObjectMapper(), endpointConnector, "app", "key", "secret",
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				"en", "sent", Duration.ofMillis(1), 1_048_576, Duration.ZERO);
		assertEquals("IFLYTEK_SUNTONE_TIMEOUT", assertThrows(
				BusinessException.class,
				() -> endpointProvider.evaluatePronunciation("hello", wav(16_000, 2), null)).code());

		for (URI endpoint : List.of(
				URI.create("/v1/private/s8e098720"),
				URI.create("http://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				URI.create("wss://evil.example/v1/private/s8e098720"),
				URI.create("wss://user@cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720"),
				URI.create("wss://cn-east-1.ws-api.xf-yun.com:443/v1/private/s8e098720"),
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/wrong"),
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720?x=1"),
				URI.create("wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720#f"))) {
			BusinessException exception = assertThrows(
				BusinessException.class,
				() -> new IflytekScoringProvider(
						new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE),
						"app", "key", "secret", endpoint, "en", "sent",
						Duration.ofSeconds(1), 1_048_576, Duration.ZERO)
						.evaluatePronunciation("hello", wav(16_000, 2), null));
			assertEquals("IFLYTEK_SUNTONE_ENDPOINT_INVALID", exception.code());
		}
	}

	@Test
	void validatesEachCredentialAndConstructorBoundaryIndependently() {
		for (String[] credentials : List.of(
				new String[] {"", "key", "secret"},
				new String[] {"app", "", "secret"},
				new String[] {"app", "key", ""})) {
			IflytekScoringProvider provider = new IflytekScoringProvider(
					new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE),
					credentials[0], credentials[1], credentials[2], OFFICIAL_ENDPOINT,
					"en", "sent", Duration.ofSeconds(1), 0, Duration.ZERO);
			assertEquals("IFLYTEK_SUNTONE_CREDENTIAL_MISSING", assertThrows(
					BusinessException.class,
					() -> provider.evaluatePronunciation("hello", wav(16_000, 2), null)).code());
		}
		assertThrows(IllegalArgumentException.class, () -> new IflytekScoringProvider(
				new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE), "app", "key", "secret",
				OFFICIAL_ENDPOINT, " ", "sent", Duration.ofSeconds(1), 1, Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> new IflytekScoringProvider(
				new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE), "app", "key", "secret",
				OFFICIAL_ENDPOINT, "en", " ", Duration.ofSeconds(1), 1, Duration.ZERO));
		for (Duration timeout : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1)}) {
			assertThrows(RuntimeException.class, () -> new IflytekScoringProvider(
					new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE), "app", "key", "secret",
					OFFICIAL_ENDPOINT, "en", "sent", timeout, 1, Duration.ZERO));
		}
		assertThrows(NullPointerException.class, () -> new IflytekScoringProvider(
				new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE), "app", "key", "secret",
				OFFICIAL_ENDPOINT, "en", "sent", Duration.ofSeconds(1), 1, null));
		assertThrows(IllegalArgumentException.class, () -> new IflytekScoringProvider(
				new ObjectMapper(), new RecordingConnector(FINAL_RESPONSE), "app", "key", "secret",
				OFFICIAL_ENDPOINT, "en", "sent", Duration.ofSeconds(1), 1, Duration.ofMillis(-1)));
	}

	private IflytekScoringProvider provider(RecordingConnector connector) {
		return provider(connector, Duration.ofSeconds(2), Duration.ZERO);
	}

	private IflytekScoringProvider provider(
			RecordingConnector connector,
			Duration timeout,
			Duration frameDelay) {
		return new IflytekScoringProvider(
				new ObjectMapper(), connector, "app-id", "api-key", "api-secret",
				OFFICIAL_ENDPOINT, "en", "sent", timeout, 1_048_576, frameDelay);
	}

	private static JsonNode json(String value) throws Exception {
		return new ObjectMapper().readTree(value);
	}

	private static String queryParameter(URI uri, String name) {
		for (String parameter : uri.getRawQuery().split("&")) {
			String[] pair = parameter.split("=", 2);
			if (pair.length == 2 && pair[0].equals(name)) {
				return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
			}
		}
		return "";
	}

	private static byte[] wav(int sampleRate, int pcmBytes) {
		ByteBuffer wav = ByteBuffer.allocate(46 + pcmBytes)
				.order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(38 + pcmBytes);
		wav.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(16).putShort((short) 1).putShort((short) 1);
		wav.putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
		wav.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		wav.putInt(pcmBytes);
		for (int index = 0; index < pcmBytes; index++) {
			wav.put((byte) (index * 17));
		}
		return wav.array();
	}

	private static byte[] wavWithChunk(String chunk, int size, int pcmBytes) {
		byte[] base = wav(16_000, pcmBytes);
		ByteBuffer value = ByteBuffer.allocate(base.length + 8 + Math.max(0, size))
				.order(ByteOrder.LITTLE_ENDIAN);
		value.put(base, 0, 12);
		value.put(chunk.getBytes(StandardCharsets.US_ASCII));
		value.putInt(size);
		if (size > 0) value.put(new byte[size]);
		value.put(base, 12, base.length - 12);
		return value.array();
	}

	private static byte[] truncatedRiff() {
		byte[] value = wav(16_000, 2);
		ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).putInt(0, value.length + 100);
		return value;
	}

	private static byte[] declaredRiffTooSmall() {
		byte[] value = wav(16_000, 2);
		ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 3);
		return value;
	}

	private static byte[] wavWithoutData() {
		byte[] value = wav(16_000, 2);
		System.arraycopy("JUNK".getBytes(StandardCharsets.US_ASCII), 0, value, 36, 4);
		return value;
	}

	private static byte[] truncatedUnknownChunk() {
		byte[] value = wav(16_000, 2);
		ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).putInt(16, value.length);
		return value;
	}

	private static byte[] wavWithFormat(int audioFormat, int sampleRate,
			int bits, int channels) {
		byte[] value = wav(sampleRate, 2);
		ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort(20, (short) audioFormat);
		buffer.putShort(22, (short) channels);
		buffer.putShort(34, (short) bits);
		return value;
	}

	private static HttpResponse<Void> response(int statusCode) {
		return new HttpResponse<>() {
			@Override public int statusCode() { return statusCode; }
			@Override public HttpRequest request() { return null; }
			@Override public Optional<HttpResponse<Void>> previousResponse() { return Optional.empty(); }
			@Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
			@Override public Void body() { return null; }
			@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
			@Override public URI uri() { return OFFICIAL_ENDPOINT; }
			@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
		};
	}

	private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(throwable);
		return future;
	}

	private static final class IOExceptionFailure extends java.io.IOException {
		private static final long serialVersionUID = 1L;
	}

	private static final class RecordingConnector implements IflytekScoringProvider.WebSocketConnector {
		private final String finalResponse;
		private final List<String> frames = new ArrayList<>();
		private URI uri;
		private RecordingSocket socket;
		private Throwable connectFailure;
		private CompletableFuture<WebSocket> connectFuture;
		private Throwable sendFailure;
		private boolean notifyError;
		private boolean closeOnFirstSend;
		private List<String> responseParts = List.of();

		private RecordingConnector(String finalResponse) {
			this.finalResponse = finalResponse;
		}

		@Override
		public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
			this.uri = uri;
			if (connectFuture != null) {
				return connectFuture;
			}
			if (connectFailure != null) {
				return failedFuture(connectFailure);
			}
			socket = new RecordingSocket(listener, this);
			listener.onOpen(socket);
			return CompletableFuture.completedFuture(socket);
		}
	}

	private static final class RecordingSocket implements WebSocket {
		private final Listener listener;
		private final RecordingConnector owner;
		private boolean outputClosed;
		private boolean inputClosed;

		private RecordingSocket(Listener listener, RecordingConnector owner) {
			this.listener = listener;
			this.owner = owner;
		}

		@Override
		public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
			String frame = data.toString();
			owner.frames.add(frame);
			if (owner.sendFailure != null) {
				return failedFuture(owner.sendFailure);
			}
			if (owner.closeOnFirstSend && owner.frames.size() == 1) {
				listener.onClose(this, 1000, "closed");
				return CompletableFuture.completedFuture(this);
			}
			if (owner.notifyError) {
				listener.onError(this, new java.io.IOException("socket error"));
			}
			if (frame.contains("\"status\":2")) {
				List<String> parts = owner.responseParts.isEmpty()
						? List.of(owner.finalResponse) : owner.responseParts;
				for (int index = 0; index < parts.size(); index++) {
					listener.onText(this, parts.get(index), index == parts.size() - 1);
				}
			}
			return CompletableFuture.completedFuture(this);
		}

		@Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { return CompletableFuture.completedFuture(this); }
		@Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
		@Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
		@Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) { outputClosed = true; inputClosed = true; return CompletableFuture.completedFuture(this); }
		@Override public void request(long n) { }
		@Override public String getSubprotocol() { return ""; }
		@Override public boolean isOutputClosed() { return outputClosed; }
		@Override public boolean isInputClosed() { return inputClosed; }
		@Override public void abort() { outputClosed = true; inputClosed = true; }
	}
}
