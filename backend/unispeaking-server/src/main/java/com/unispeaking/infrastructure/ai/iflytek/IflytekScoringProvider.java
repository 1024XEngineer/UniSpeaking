package com.unispeaking.infrastructure.ai.iflytek;

import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.ScoringProvider;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class IflytekScoringProvider extends ScoringProvider {

	private static final int AUDIO_FRAME_BYTES = 1_280;
	private static final int DEFAULT_MAX_AUDIO_BYTES = 10 * 1024 * 1024;
	private static final int MAX_PCM_SESSION_BYTES = 16_000 * 2 * 300;
	private static final int MAX_REFERENCE_TEXT_CHARS = 10_000;
	private static final int MAX_MESSAGE_CHARS = 1_500_000;

	private final ObjectMapper objectMapper;
	private final WebSocketConnector connector;
	private final String appId;
	private final String apiKey;
	private final String apiSecret;
	private final URI endpoint;
	private final String language;
	private final String category;
	private final Duration readTimeout;
	private final int maxAudioBytes;
	private final Duration frameDelay;

	@Autowired
	public IflytekScoringProvider(
			ObjectMapper objectMapper,
			@Value("${XFYUN_APP_ID:}") String appId,
			@Value("${XFYUN_API_KEY:}") String apiKey,
			@Value("${XFYUN_API_SECRET:}") String apiSecret,
			@Value("${XFYUN_ISE_ENDPOINT:wss://ise-api.xfyun.cn/v2/open-ise}") URI endpoint,
			@Value("${XFYUN_ISE_LANGUAGE:en_vip}") String language,
			@Value("${XFYUN_ISE_CATEGORY:read_sentence}") String category,
			@Value("${XFYUN_ISE_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${XFYUN_ISE_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${XFYUN_ISE_MAX_AUDIO_BYTES:10485760}") int maxAudioBytes) {
		this(
				objectMapper,
				defaultConnector(connectTimeoutSeconds),
				appId,
				apiKey,
				apiSecret,
				endpoint,
				language,
				category,
				positiveDuration(readTimeoutSeconds, "iFlytek ISE read timeout"),
				maxAudioBytes,
				Duration.ofMillis(40));
	}

	public IflytekScoringProvider(
			ObjectMapper objectMapper,
			WebSocketConnector connector,
			String appId,
			String apiKey,
			String apiSecret,
			URI endpoint,
			String language,
			String category,
			Duration readTimeout,
			int maxAudioBytes,
			Duration frameDelay) {
		super(
				"iflytek",
				Set.of(AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING));
		this.objectMapper = Objects.requireNonNull(objectMapper, "iFlytek ISE JSON mapper is required");
		this.connector = Objects.requireNonNull(connector, "iFlytek ISE WebSocket connector is required");
		this.appId = trim(appId);
		this.apiKey = trim(apiKey);
		this.apiSecret = trim(apiSecret);
		this.endpoint = Objects.requireNonNull(endpoint, "iFlytek ISE endpoint is required");
		this.language = requiredText(language, "iFlytek ISE language");
		this.category = requiredText(category, "iFlytek ISE category");
		this.readTimeout = requirePositive(readTimeout, "iFlytek ISE read timeout");
		this.maxAudioBytes = maxAudioBytes > 0 ? maxAudioBytes : DEFAULT_MAX_AUDIO_BYTES;
		this.frameDelay = Objects.requireNonNull(frameDelay, "iFlytek ISE frame delay is required");
		if (frameDelay.isNegative()) {
			throw new IllegalArgumentException("iFlytek ISE frame delay must not be negative");
		}
	}

	@FunctionalInterface
	public interface WebSocketConnector {
		CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);
	}

	@Override
	public String evaluatePronunciation(String text, Byte[] audio, String token) {
		String referenceText = validateReferenceText(text);
		byte[] originalAudio = unboxAudio(audio, "iFlytek pronunciation evaluation");
		if (originalAudio.length > maxAudioBytes) {
			throw nonRetryableFailure(
					"PRONUNCIATION_AUDIO_TOO_LARGE",
					"Pronunciation evaluation audio exceeds the configured limit");
		}
		byte[] pcmAudio = wavPayload(originalAudio);
		if (pcmAudio.length > MAX_PCM_SESSION_BYTES) {
			throw nonRetryableFailure(
					"PRONUNCIATION_AUDIO_TOO_LONG",
					"iFlytek PCM audio must not exceed five minutes");
		}
		requireCredentials();
		requireEndpoint();

		WebSocket socket = null;
		boolean completed = false;
		long deadlineNanos = System.nanoTime() + readTimeout.toNanos();
		try {
			CompletableFuture<String> result = new CompletableFuture<>();
			RawIseListener listener = new RawIseListener(result);
			socket = connector.connect(signedEndpoint(apiKey), listener)
					.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
			send(socket, startFrame(referenceText, false), deadlineNanos);
			for (int offset = 0; offset < pcmAudio.length; offset += AUDIO_FRAME_BYTES) {
				int end = Math.min(offset + AUDIO_FRAME_BYTES, pcmAudio.length);
				int audioStatus = offset == 0 ? 1 : 2;
				send(socket, audioFrame(
						Arrays.copyOfRange(pcmAudio, offset, end),
						false,
						audioStatus),
						deadlineNanos);
				pauseBetweenFrames(deadlineNanos);
			}
			send(socket, endFrame(false), deadlineNanos);
			String response = result.get(
					remainingMillis(deadlineNanos),
					TimeUnit.MILLISECONDS);
			completed = true;
			return response;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (TimeoutException exception) {
			throw retryableFailure(
					"IFLYTEK_ISE_TIMEOUT",
					"iFlytek pronunciation evaluation timed out");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"IFLYTEK_ISE_INTERRUPTED",
					"iFlytek pronunciation evaluation was interrupted");
		}
		catch (ExecutionException | CompletionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof BusinessException businessException) {
				throw businessException;
			}
			throw retryableFailure(
					"IFLYTEK_ISE_CONNECTION_FAILED",
					"Failed to communicate with iFlytek pronunciation evaluation");
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"IFLYTEK_ISE_REQUEST_INVALID",
					"Failed to encode the iFlytek pronunciation request");
		}
		finally {
			if (socket != null && !socket.isOutputClosed()) {
				if (completed) {
					socket.sendClose(WebSocket.NORMAL_CLOSURE, "complete");
				}
				else {
					socket.abort();
				}
			}
		}
	}

	private String validateReferenceText(String text) {
		String referenceText = trim(text);
		if (referenceText.isBlank()) {
			throw nonRetryableFailure(
					"INVALID_PRONUNCIATION_REFERENCE",
					"Pronunciation reference text is required");
		}
		if (referenceText.length() > MAX_REFERENCE_TEXT_CHARS) {
			throw nonRetryableFailure(
					"PRONUNCIATION_REFERENCE_TOO_LONG",
					"Pronunciation reference text exceeds the configured limit");
		}
		return referenceText;
	}

	private String startFrame(String referenceText, boolean mp3) throws JacksonException {
		String markedText = "\uFEFF[content]\n" + referenceText;
		Map<String, Object> business = Map.ofEntries(
				Map.entry("sub", "ise"),
				Map.entry("ent", language),
				Map.entry("category", category),
				Map.entry("cmd", "ssb"),
				Map.entry("auf", "audio/L16;rate=16000"),
				Map.entry("aue", mp3 ? "lame" : "raw"),
				Map.entry("tte", "utf-8"),
				Map.entry("rst", "entirety"),
				Map.entry("ise_unite", "1"),
				Map.entry("extra_ability", "multi_dimension"),
				Map.entry("text", markedText),
				Map.entry("ttp_skip", true));
		return objectMapper.writeValueAsString(Map.of(
				"common", Map.of("app_id", appId),
				"business", business,
				"data", Map.of("status", 0, "data", "")));
	}

	private String audioFrame(byte[] audio, boolean mp3, int audioStatus) throws JacksonException {
		return objectMapper.writeValueAsString(Map.of(
				"business", Map.of(
						"cmd", "auw",
						"aus", audioStatus,
						"aue", mp3 ? "lame" : "raw"),
				"data", Map.of(
						"status", 1,
						"data", Base64.getEncoder().encodeToString(audio),
						"data_type", 1,
						"encoding", mp3 ? "lame" : "raw")));
	}

	private String endFrame(boolean mp3) throws JacksonException {
		return objectMapper.writeValueAsString(Map.of(
				"business", Map.of(
						"cmd", "auw",
						"aus", 4,
						"aue", mp3 ? "lame" : "raw"),
				"data", Map.of(
						"status", 2,
						"data", "",
						"data_type", 1,
						"encoding", mp3 ? "lame" : "raw")));
	}

	private void send(WebSocket socket, String frame, long deadlineNanos)
			throws InterruptedException, ExecutionException, TimeoutException {
		socket.sendText(frame, true)
				.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
	}

	private void pauseBetweenFrames(long deadlineNanos)
			throws InterruptedException, TimeoutException {
		if (!frameDelay.isZero()) {
			if (frameDelay.toNanos() >= remainingNanos(deadlineNanos)) {
				throw new TimeoutException("iFlytek ISE deadline reached while pacing audio");
			}
			Thread.sleep(frameDelay);
		}
	}

	private long remainingMillis(long deadlineNanos) throws TimeoutException {
		long remainingNanos = remainingNanos(deadlineNanos);
		return Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
	}

	private long remainingNanos(long deadlineNanos) throws TimeoutException {
		long remaining = deadlineNanos - System.nanoTime();
		if (remaining <= 0) {
			throw new TimeoutException("iFlytek ISE deadline reached");
		}
		return remaining;
	}

	private URI signedEndpoint(String signingApiKey) {
		try {
			String host = endpoint.getHost();
			String date = ZonedDateTime.now(ZoneOffset.UTC)
					.format(DateTimeFormatter.RFC_1123_DATE_TIME);
			String requestLine = "GET " + endpoint.getRawPath() + " HTTP/1.1";
			String signatureOrigin = "host: " + host + "\n"
					+ "date: " + date + "\n"
					+ requestLine;
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String signature = Base64.getEncoder().encodeToString(
					mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
			String authorizationOrigin = "api_key=\"" + signingApiKey
					+ "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
					+ signature + "\"";
			String authorization = Base64.getEncoder().encodeToString(
					authorizationOrigin.getBytes(StandardCharsets.UTF_8));
			String query = "authorization=" + encode(authorization)
					+ "&date=" + encode(date)
					+ "&host=" + encode(host);
			String separator = endpoint.getRawQuery() == null ? "?" : "&";
			return URI.create(endpoint + separator + query);
		}
		catch (GeneralSecurityException exception) {
			throw retryableFailure(
					"IFLYTEK_ISE_SIGNATURE_FAILED",
					"Failed to sign the iFlytek pronunciation request");
		}
	}

	private byte[] wavPayload(byte[] wav) {
		if (wav.length < 12
				|| wav[0] != 'R'
				|| wav[1] != 'I'
				|| wav[2] != 'F'
				|| wav[3] != 'F'
				|| wav[8] != 'W'
				|| wav[9] != 'A'
				|| wav[10] != 'V'
				|| wav[11] != 'E') {
			throw nonRetryableFailure(
					"INVALID_PRONUNCIATION_WAV",
					"Pronunciation WAV audio has an invalid header");
		}
		int declaredRiffBytes = littleEndianInt(wav, 4);
		if (declaredRiffBytes < 4 || (long) declaredRiffBytes + 8 > wav.length) {
			throw invalidWav("Pronunciation WAV audio is truncated");
		}
		boolean validFormat = false;
		int audioDataStart = -1;
		int audioDataBytes = -1;
		for (int index = 12; index + 8 <= wav.length;) {
			int size = littleEndianInt(wav, index + 4);
			if (size < 0) {
				throw invalidWav("Pronunciation WAV chunk size is invalid");
			}
			int dataStart = index + 8;
			long dataEnd = (long) dataStart + size;
			if (dataEnd > wav.length) {
				throw invalidWav("Pronunciation WAV chunk is truncated");
			}
			if (wav[index] == 'f'
					&& wav[index + 1] == 'm'
					&& wav[index + 2] == 't'
					&& wav[index + 3] == ' ') {
				if (size < 16) {
					throw invalidWav("Pronunciation WAV format chunk is invalid");
				}
				int encoding = littleEndianShort(wav, dataStart);
				int channels = littleEndianShort(wav, dataStart + 2);
				int sampleRate = littleEndianInt(wav, dataStart + 4);
				int bitsPerSample = littleEndianShort(wav, dataStart + 14);
				validFormat = encoding == 1
						&& channels == 1
						&& sampleRate == 16_000
						&& bitsPerSample == 16;
			}
			else if (wav[index] == 'd'
					&& wav[index + 1] == 'a'
					&& wav[index + 2] == 't'
					&& wav[index + 3] == 'a') {
				if (size <= 0) {
					throw invalidWav("Pronunciation WAV audio data is empty");
				}
				audioDataStart = dataStart;
				audioDataBytes = size;
			}
			long next = dataEnd + (size & 1);
			if (next <= index || next > wav.length) {
				break;
			}
			index = (int) next;
		}
		if (!validFormat) {
			throw invalidWav(
					"Pronunciation WAV must be PCM, 16 kHz, 16-bit, and mono");
		}
		if (audioDataStart < 0) {
			throw invalidWav("Pronunciation WAV audio does not contain a data chunk");
		}
		return Arrays.copyOfRange(
				wav,
				audioDataStart,
				audioDataStart + audioDataBytes);
	}

	private int littleEndianInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff)
				| ((bytes[offset + 1] & 0xff) << 8)
				| ((bytes[offset + 2] & 0xff) << 16)
				| ((bytes[offset + 3] & 0xff) << 24);
	}

	private int littleEndianShort(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
	}

	private BusinessException invalidWav(String message) {
		return nonRetryableFailure("INVALID_PRONUNCIATION_WAV", message);
	}

	private void requireCredentials() {
		if (appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
			throw retryableFailure(
					"IFLYTEK_ISE_CREDENTIAL_MISSING",
					"Set XFYUN_APP_ID, XFYUN_API_KEY, and XFYUN_API_SECRET before calling iFlytek ISE");
		}
	}

	private void requireEndpoint() {
		if (!endpoint.isAbsolute()
				|| !"wss".equalsIgnoreCase(endpoint.getScheme())
				|| !"ise-api.xfyun.cn".equalsIgnoreCase(endpoint.getHost())
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !"/v2/open-ise".equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"IFLYTEK_ISE_ENDPOINT_INVALID",
					"iFlytek ISE endpoint must be an absolute wss URL");
		}
	}

	private static WebSocketConnector defaultConnector(int connectTimeoutSeconds) {
		Duration connectTimeout = positiveDuration(
				connectTimeoutSeconds,
				"iFlytek ISE connect timeout");
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.build();
		return (uri, listener) -> client.newWebSocketBuilder()
				.connectTimeout(connectTimeout)
				.buildAsync(uri, listener);
	}

	private static Duration positiveDuration(int seconds, String name) {
		if (seconds <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return Duration.ofSeconds(seconds);
	}

	private static Duration requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return duration;
	}

	private static String requiredText(String value, String name) {
		String trimmed = trim(value);
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return trimmed;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private final class RawIseListener implements WebSocket.Listener {

		private final CompletableFuture<String> result;
		private final StringBuilder message = new StringBuilder();

		private RawIseListener(CompletableFuture<String> result) {
			this.result = result;
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(
				WebSocket webSocket,
				CharSequence data,
				boolean last) {
			if (data.length() > MAX_MESSAGE_CHARS - message.length()) {
				result.completeExceptionally(retryableFailure(
						"IFLYTEK_ISE_RESPONSE_TOO_LARGE",
						"iFlytek pronunciation response exceeds the configured limit"));
				webSocket.abort();
				return CompletableFuture.completedFuture(null);
			}
			message.append(data);
			if (last) {
				handleMessage(message.toString());
				message.setLength(0);
			}
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		private void handleMessage(String payload) {
			try {
				JsonNode root = objectMapper.readTree(payload);
				int code = root.path("code").asInt(-1);
				if (code != 0) {
					result.completeExceptionally(retryableFailure(
							"IFLYTEK_ISE_REQUEST_FAILED",
							"iFlytek pronunciation evaluation failed with code " + code));
					return;
				}
				if (root.path("data").path("status").asInt(-1) == 2) {
					result.complete(payload);
				}
			}
			catch (JacksonException exception) {
				result.completeExceptionally(retryableFailure(
						"IFLYTEK_ISE_RESPONSE_INVALID",
						"iFlytek pronunciation evaluation returned an invalid response"));
			}
		}

		@Override
		public CompletionStage<?> onClose(
				WebSocket webSocket,
				int statusCode,
				String reason) {
			if (!result.isDone()) {
				result.completeExceptionally(retryableFailure(
						"IFLYTEK_ISE_CONNECTION_CLOSED",
						"iFlytek pronunciation connection closed before returning its final response"));
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			result.completeExceptionally(retryableFailure(
					"IFLYTEK_ISE_CONNECTION_FAILED",
					"iFlytek pronunciation connection failed"));
		}
	}

}
