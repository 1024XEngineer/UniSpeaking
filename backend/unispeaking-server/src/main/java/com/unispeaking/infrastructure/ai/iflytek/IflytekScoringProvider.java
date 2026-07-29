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
import java.util.LinkedHashMap;
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

/**
 * 科大讯飞 Suntone 句子发音评测适配器。
 *
 * <p>服务层统一接收 16 kHz、16-bit、单声道 PCM WAV；本适配器在内存中
 * 转为 Suntone 支持的 MP3，并请求逐音素、0–100 分制的完整句子评测。</p>
 */
@Component
public class IflytekScoringProvider extends ScoringProvider {

	private static final int AUDIO_FRAME_BYTES = 1_024;
	private static final int DEFAULT_MAX_AUDIO_BYTES = 10 * 1024 * 1024;
	private static final int MAX_REFERENCE_TEXT_CHARS = 4_096;
	private static final int MAX_MESSAGE_CHARS = 1_500_000;
	private static final String SUNTONE_HOST =
			"cn-east-1.ws-api.xf-yun.com";
	private static final String SUNTONE_PATH =
			"/v1/private/s8e098720";

	private final ObjectMapper objectMapper;
	private final WebSocketConnector connector;
	private final IflytekPcmMp3Encoder mp3Encoder;
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
			@Value("${XFYUN_SUNTONE_ENDPOINT:wss://cn-east-1.ws-api.xf-yun.com/v1/private/s8e098720}")
			URI endpoint,
			@Value("${XFYUN_SUNTONE_LANGUAGE:en}") String language,
			@Value("${XFYUN_SUNTONE_CATEGORY:sent}") String category,
			@Value("${XFYUN_SUNTONE_CONNECT_TIMEOUT_SECONDS:10}")
			int connectTimeoutSeconds,
			@Value("${XFYUN_SUNTONE_READ_TIMEOUT_SECONDS:60}")
			int readTimeoutSeconds,
			@Value("${XFYUN_SUNTONE_MAX_AUDIO_BYTES:10485760}")
			int maxAudioBytes) {
		this(
				objectMapper,
				defaultConnector(connectTimeoutSeconds),
				appId,
				apiKey,
				apiSecret,
				endpoint,
				language,
				category,
				positiveDuration(
						readTimeoutSeconds,
						"iFlytek Suntone read timeout"),
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
		this.objectMapper = Objects.requireNonNull(
				objectMapper,
				"iFlytek Suntone JSON mapper is required");
		this.connector = Objects.requireNonNull(
				connector,
				"iFlytek Suntone WebSocket connector is required");
		this.mp3Encoder = new IflytekPcmMp3Encoder();
		this.appId = trim(appId);
		this.apiKey = trim(apiKey);
		this.apiSecret = trim(apiSecret);
		this.endpoint = Objects.requireNonNull(
				endpoint,
				"iFlytek Suntone endpoint is required");
		this.language = requiredText(
				language,
				"iFlytek Suntone language");
		this.category = requiredText(
				category,
				"iFlytek Suntone category");
		this.readTimeout = requirePositive(
				readTimeout,
				"iFlytek Suntone read timeout");
		this.maxAudioBytes =
				maxAudioBytes > 0 ? maxAudioBytes : DEFAULT_MAX_AUDIO_BYTES;
		this.frameDelay = Objects.requireNonNull(
				frameDelay,
				"iFlytek Suntone frame delay is required");
		if (frameDelay.isNegative()) {
			throw new IllegalArgumentException(
					"iFlytek Suntone frame delay must not be negative");
		}
	}

	@FunctionalInterface
	public interface WebSocketConnector {
		CompletableFuture<WebSocket> connect(
				URI uri,
				WebSocket.Listener listener);
	}

	@Override
	public String evaluatePronunciation(
			String text,
			Byte[] audio,
			String token) {
		String referenceText = validateReferenceText(text);
		byte[] wav = unboxAudio(
				audio,
				"iFlytek pronunciation evaluation");
		if (wav.length > maxAudioBytes) {
			throw nonRetryableFailure(
					"PRONUNCIATION_AUDIO_TOO_LARGE",
					"Pronunciation evaluation audio exceeds the configured limit");
		}
		byte[] pcm = wavPayload(wav);
		byte[] mp3;
		try {
			mp3 = mp3Encoder.encode(pcm);
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			throw nonRetryableFailure(
					"PRONUNCIATION_AUDIO_ENCODING_FAILED",
					"Pronunciation WAV audio could not be encoded");
		}
		requireCredentials();
		requireEndpoint();

		WebSocket socket = null;
		boolean completed = false;
		long deadlineNanos =
				System.nanoTime() + readTimeout.toNanos();
		try {
			CompletableFuture<String> result =
					new CompletableFuture<>();
			RawSuntoneListener listener =
					new RawSuntoneListener(result);
			socket = connector.connect(
					signedEndpoint(apiKey),
					listener).get(
							remainingMillis(deadlineNanos),
							TimeUnit.MILLISECONDS);
			sendAudio(
					socket,
					referenceText,
					mp3,
					deadlineNanos);
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
					"IFLYTEK_SUNTONE_TIMEOUT",
					"iFlytek pronunciation evaluation timed out");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"IFLYTEK_SUNTONE_INTERRUPTED",
					"iFlytek pronunciation evaluation was interrupted");
		}
		catch (ExecutionException | CompletionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof BusinessException businessException) {
				throw businessException;
			}
			throw retryableFailure(
					"IFLYTEK_SUNTONE_CONNECTION_FAILED",
					"Failed to communicate with iFlytek pronunciation evaluation");
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"IFLYTEK_SUNTONE_REQUEST_INVALID",
					"Failed to encode the iFlytek pronunciation request");
		}
		finally {
			if (socket != null && !socket.isOutputClosed()) {
				if (completed) {
					socket.sendClose(
							WebSocket.NORMAL_CLOSURE,
							"complete");
				}
				else {
					socket.abort();
				}
			}
		}
	}

	private void sendAudio(
			WebSocket socket,
			String referenceText,
			byte[] mp3,
			long deadlineNanos)
			throws JacksonException,
			InterruptedException,
			ExecutionException,
			TimeoutException {
		int sequence = 0;
		for (int offset = 0; offset < mp3.length;
				offset += AUDIO_FRAME_BYTES) {
			int end = Math.min(
					offset + AUDIO_FRAME_BYTES,
					mp3.length);
			boolean first = offset == 0;
			boolean last = end == mp3.length;
			int status = first ? 0 : (last ? 2 : 1);
			send(
					socket,
					audioFrame(
							referenceText,
							Arrays.copyOfRange(mp3, offset, end),
							sequence,
							status,
							first),
					deadlineNanos);
			sequence++;
			if (!last) {
				pauseBetweenFrames(deadlineNanos);
			}
			else if (first) {
				send(
						socket,
						audioFrame(
								referenceText,
								new byte[0],
								sequence,
								2,
								false),
						deadlineNanos);
			}
		}
	}

	private String audioFrame(
			String referenceText,
			byte[] audio,
			int sequence,
			int status,
			boolean first)
			throws JacksonException {
		Map<String, Object> frame = new LinkedHashMap<>();
		frame.put(
				"header",
				Map.of(
						"app_id",
						appId,
						"status",
						status));
		if (first) {
			frame.put(
					"parameter",
					Map.of(
							"st",
							Map.ofEntries(
									Map.entry("lang", language),
									Map.entry("core", category),
									Map.entry("refText", referenceText),
									Map.entry("phoneme_output", 1),
									Map.entry("scale", 100),
									Map.entry("precision", 0.1),
									Map.entry("output_rawtext", 1),
									Map.entry("dict_type", "IPA88"),
									Map.entry("dict_dialect", "en_us"),
									Map.entry(
											"result",
											Map.of(
													"encoding",
													"utf8",
													"compress",
													"raw",
													"format",
													"plain")))));
		}
		frame.put(
				"payload",
				Map.of(
						"data",
						Map.ofEntries(
								Map.entry("encoding", "lame"),
								Map.entry("sample_rate", 16_000),
								Map.entry("channels", 1),
								Map.entry("bit_depth", 16),
								Map.entry("status", status),
								Map.entry("seq", sequence),
								Map.entry(
										"audio",
										Base64.getEncoder()
												.encodeToString(audio)),
								Map.entry(
										"frame_size",
										audio.length))));
		return objectMapper.writeValueAsString(frame);
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

	private void send(
			WebSocket socket,
			String frame,
			long deadlineNanos)
			throws InterruptedException,
			ExecutionException,
			TimeoutException {
		socket.sendText(frame, true).get(
				remainingMillis(deadlineNanos),
				TimeUnit.MILLISECONDS);
	}

	private void pauseBetweenFrames(long deadlineNanos)
			throws InterruptedException, TimeoutException {
		if (!frameDelay.isZero()) {
			if (frameDelay.toNanos()
					>= remainingNanos(deadlineNanos)) {
				throw new TimeoutException(
						"iFlytek Suntone deadline reached while pacing audio");
			}
			Thread.sleep(frameDelay);
		}
	}

	private long remainingMillis(long deadlineNanos)
			throws TimeoutException {
		return Math.max(
				1,
				TimeUnit.NANOSECONDS.toMillis(
						remainingNanos(deadlineNanos)));
	}

	private long remainingNanos(long deadlineNanos)
			throws TimeoutException {
		long remaining = deadlineNanos - System.nanoTime();
		if (remaining <= 0) {
			throw new TimeoutException(
					"iFlytek Suntone deadline reached");
		}
		return remaining;
	}

	private URI signedEndpoint(String signingApiKey) {
		try {
			String host = endpoint.getHost();
			String date = ZonedDateTime.now(ZoneOffset.UTC)
					.format(DateTimeFormatter.RFC_1123_DATE_TIME);
			String requestLine =
					"GET " + endpoint.getRawPath() + " HTTP/1.1";
			String signatureOrigin =
					"host: " + host + "\n"
							+ "date: " + date + "\n"
							+ requestLine;
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
					apiSecret.getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"));
			String signature = Base64.getEncoder().encodeToString(
					mac.doFinal(signatureOrigin.getBytes(
							StandardCharsets.UTF_8)));
			String authorizationOrigin =
					"api_key=\"" + signingApiKey
							+ "\", algorithm=\"hmac-sha256\", "
							+ "headers=\"host date request-line\", "
							+ "signature=\"" + signature + "\"";
			String authorization =
					Base64.getEncoder().encodeToString(
							authorizationOrigin.getBytes(
									StandardCharsets.UTF_8));
			String query =
					"host=" + encode(host)
							+ "&date=" + encode(date)
							+ "&authorization="
							+ encode(authorization);
			return URI.create(endpoint + "?" + query);
		}
		catch (GeneralSecurityException exception) {
			throw retryableFailure(
					"IFLYTEK_SUNTONE_SIGNATURE_FAILED",
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
			throw invalidWav(
					"Pronunciation WAV audio has an invalid header");
		}
		int declaredRiffBytes = littleEndianInt(wav, 4);
		if (declaredRiffBytes < 4
				|| (long) declaredRiffBytes + 8 > wav.length) {
			throw invalidWav(
					"Pronunciation WAV audio is truncated");
		}
		boolean validFormat = false;
		int audioDataStart = -1;
		int audioDataBytes = -1;
		for (int index = 12; index + 8 <= wav.length;) {
			int size = littleEndianInt(wav, index + 4);
			if (size < 0) {
				throw invalidWav(
						"Pronunciation WAV chunk size is invalid");
			}
			int dataStart = index + 8;
			long dataEnd = (long) dataStart + size;
			if (dataEnd > wav.length) {
				throw invalidWav(
						"Pronunciation WAV chunk is truncated");
			}
			if (chunkEquals(wav, index, "fmt ")) {
				if (size < 16) {
					throw invalidWav(
							"Pronunciation WAV format chunk is invalid");
				}
				validFormat =
						littleEndianShort(wav, dataStart) == 1
								&& littleEndianShort(
										wav,
										dataStart + 2) == 1
								&& littleEndianInt(
										wav,
										dataStart + 4) == 16_000
								&& littleEndianShort(
										wav,
										dataStart + 14) == 16;
			}
			else if (chunkEquals(wav, index, "data")) {
				if (size <= 0 || (size & 1) != 0) {
					throw invalidWav(
							"Pronunciation WAV audio data is invalid");
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
			throw invalidWav(
					"Pronunciation WAV audio does not contain a data chunk");
		}
		return Arrays.copyOfRange(
				wav,
				audioDataStart,
				audioDataStart + audioDataBytes);
	}

	private boolean chunkEquals(
			byte[] bytes,
			int offset,
			String expected) {
		for (int index = 0; index < 4; index++) {
			if (bytes[offset + index] != expected.charAt(index)) {
				return false;
			}
		}
		return true;
	}

	private int littleEndianInt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff)
				| ((bytes[offset + 1] & 0xff) << 8)
				| ((bytes[offset + 2] & 0xff) << 16)
				| ((bytes[offset + 3] & 0xff) << 24);
	}

	private int littleEndianShort(byte[] bytes, int offset) {
		return (bytes[offset] & 0xff)
				| ((bytes[offset + 1] & 0xff) << 8);
	}

	private BusinessException invalidWav(String message) {
		return nonRetryableFailure(
				"INVALID_PRONUNCIATION_WAV",
				message);
	}

	private void requireCredentials() {
		if (appId.isBlank()
				|| apiKey.isBlank()
				|| apiSecret.isBlank()) {
			throw retryableFailure(
					"IFLYTEK_SUNTONE_CREDENTIAL_MISSING",
					"Set XFYUN_APP_ID, XFYUN_API_KEY, and "
							+ "XFYUN_API_SECRET before calling "
							+ "iFlytek Suntone");
		}
	}

	private void requireEndpoint() {
		if (!endpoint.isAbsolute()
				|| !"wss".equalsIgnoreCase(endpoint.getScheme())
				|| !SUNTONE_HOST.equalsIgnoreCase(
						endpoint.getHost())
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !SUNTONE_PATH.equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"IFLYTEK_SUNTONE_ENDPOINT_INVALID",
					"iFlytek Suntone endpoint must be the official "
							+ "absolute wss URL");
		}
	}

	private static WebSocketConnector defaultConnector(
			int connectTimeoutSeconds) {
		Duration connectTimeout = positiveDuration(
				connectTimeoutSeconds,
				"iFlytek Suntone connect timeout");
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.build();
		return (uri, listener) ->
				client.newWebSocketBuilder()
						.connectTimeout(connectTimeout)
						.buildAsync(uri, listener);
	}

	private static Duration positiveDuration(
			int seconds,
			String name) {
		if (seconds <= 0) {
			throw new IllegalArgumentException(
					name + " must be positive");
		}
		return Duration.ofSeconds(seconds);
	}

	private static Duration requirePositive(
			Duration duration,
			String name) {
		if (duration == null
				|| duration.isZero()
				|| duration.isNegative()) {
			throw new IllegalArgumentException(
					name + " must be positive");
		}
		return duration;
	}

	private static String requiredText(
			String value,
			String name) {
		String trimmed = trim(value);
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException(
					name + " is required");
		}
		return trimmed;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String encode(String value) {
		return URLEncoder.encode(
				value,
				StandardCharsets.UTF_8);
	}

	private final class RawSuntoneListener
			implements WebSocket.Listener {

		private final CompletableFuture<String> result;
		private final StringBuilder message =
				new StringBuilder();

		private RawSuntoneListener(
				CompletableFuture<String> result) {
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
			if (data.length()
					> MAX_MESSAGE_CHARS - message.length()) {
				result.completeExceptionally(
						retryableFailure(
								"IFLYTEK_SUNTONE_RESPONSE_TOO_LARGE",
								"iFlytek pronunciation response "
										+ "exceeds the configured limit"));
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
				JsonNode header = root.path("header");
				int code = header.path("code").asInt(-1);
				if (code != 0) {
					result.completeExceptionally(
							retryableFailure(
									"IFLYTEK_SUNTONE_REQUEST_FAILED",
									"iFlytek pronunciation evaluation "
											+ "failed with code " + code));
					return;
				}
				int headerStatus =
						header.path("status").asInt(-1);
				int payloadStatus = root.path("payload")
						.path("result")
						.path("status")
						.asInt(-1);
				if (headerStatus == 2 || payloadStatus == 2) {
					result.complete(payload);
				}
			}
			catch (JacksonException exception) {
				result.completeExceptionally(
						retryableFailure(
								"IFLYTEK_SUNTONE_RESPONSE_INVALID",
								"iFlytek pronunciation evaluation "
										+ "returned an invalid response"));
			}
		}

		@Override
		public CompletionStage<?> onClose(
				WebSocket webSocket,
				int statusCode,
				String reason) {
			if (!result.isDone()) {
				result.completeExceptionally(
						retryableFailure(
								"IFLYTEK_SUNTONE_CONNECTION_CLOSED",
								"iFlytek pronunciation connection "
										+ "closed before its final response"));
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(
				WebSocket webSocket,
				Throwable error) {
			result.completeExceptionally(
					retryableFailure(
							"IFLYTEK_SUNTONE_CONNECTION_FAILED",
							"iFlytek pronunciation connection failed"));
		}
	}
}
