package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.infrastructure.config.RealtimeProperties;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import com.unispeaking.infrastructure.realtime.RealtimeCredentialIssuer;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.ProviderCredentialOverride;
import com.unispeaking.provider.RealtimeProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class QwenRealtimeProvider extends RealtimeProvider {

	private final HttpClient httpClient;
	private final RealtimeProperties properties;
	private final RealtimeCredentialIssuer credentialIssuer;

	public QwenRealtimeProvider(
			HttpClient realtimeHttpClient,
			RealtimeProperties properties,
			RealtimeCredentialIssuer credentialIssuer) {
		super(
				ProviderType.QWEN,
				Set.of(AiProviderRegistry.QWEN_REALTIME_FLASH));
		this.httpClient = realtimeHttpClient;
		this.properties = properties;
		this.credentialIssuer = credentialIssuer;
	}

	@Override
	public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
		return exchangeRealtime(modelId, offerSdp, token).answerSdp();
	}

	@Override
	public RealtimeConnectionResult connect(
			RealtimeConnectCommand command,
			RealtimeCredential credential) {
		ExchangeResult exchange = exchangeRealtime(
				command.modelId(), command.offerSdp(), credential.bearerToken());
			return new RealtimeConnectionResult(
					exchange.providerSessionId(),
				type(),
				command.modelId(),
				command.voiceId(),
				exchange.requestId(),
				exchange.answerSdp(),
				credential.expiresAt());
	}

	private ExchangeResult exchangeRealtime(String modelId, String offerSdp, String token) {
		if (offerSdp == null || offerSdp.isBlank()) {
			throw nonRetryableFailure("INVALID_SDP", "WebRTC offer SDP is required");
		}
		if (token == null || token.isBlank()) {
			throw retryableFailure("QWEN_CREDENTIAL_MISSING", "Qwen bearer credential is not configured");
		}
		String model = modelId == null || modelId.isBlank()
				? AiProviderRegistry.QWEN_REALTIME_FLASH
				: modelId.trim();
		if (!supports(model)) {
			throw nonRetryableFailure(
					"QWEN_REALTIME_MODEL_NOT_SUPPORTED",
					"Qwen realtime model is not registered: " + model);
		}
		String workspaceId = ProviderCredentialOverride.currentOr(
				"workspaceId", properties.getWorkspaceId());
		String sdpExchangeUrl = properties.getWebRtcSdpExchangeUrl(model, workspaceId);
		if (sdpExchangeUrl.isBlank()) {
			throw retryableFailure(
					"QWEN_WORKSPACE_OR_MODEL_MISSING",
					"Set BAILIAN_WORKSPACE_ID before starting a Qwen realtime session");
		}
		try {
			return attemptExchange(model, sdpExchangeUrl, offerSdp, token);
		}
		catch (TransientSignalingFailure transientFailure) {
			// 瞬时失败（IOException/429/5xx）用新的临时 key 重试一次后再抛；4xx 不重试。
			RealtimeCredential freshCredential = credentialIssuer.issue(ProviderType.QWEN);
			try {
				return attemptExchange(
						model,
						sdpExchangeUrl,
						offerSdp,
						freshCredential.bearerToken());
			}
			catch (TransientSignalingFailure retryFailure) {
				throw retryFailure.failure();
			}
		}
	}

	private ExchangeResult attemptExchange(
			String model,
			String sdpExchangeUrl,
			String offerSdp,
			String token) {
		RealtimeFlowLog.info("flow.3.sdp.request provider={} url={} model={} temporaryToken={} offerSdp={}",
				type(), sdpExchangeUrl, model,
				RealtimeFlowLog.maskSecret(token),
				RealtimeFlowLog.sdpSummary(offerSdp));
		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(sdpExchangeUrl))
				.timeout(properties.getReadTimeout())
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/sdp")
				.POST(HttpRequest.BodyPublishers.ofString(offerSdp))
				.build();
		HttpResponse<String> response;
		try {
			response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException exception) {
			throw new TransientSignalingFailure(
					retryableFailure("QWEN_SIGNALING_IO_ERROR", "Failed to call Qwen signaling"));
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure("QWEN_SIGNALING_INTERRUPTED", "Qwen signaling call was interrupted");
		}
		int statusCode = response.statusCode();
		if (statusCode == 429 || statusCode >= 500) {
			// 429/5xx：瞬时失败，用新的临时 key 重试。
			throw new TransientSignalingFailure(
					retryableFailure("QWEN_SIGNALING_FAILED",
						signalingFailureMessage(statusCode, response.body())));
		}
		if (statusCode >= 400) {
			// 其余 4xx：offer 被拒，属于确定性失败，不重试。
			throw nonRetryableFailure("QWEN_SIGNALING_FAILED",
					signalingFailureMessage(statusCode, response.body()));
		}
		if (statusCode < 200 || statusCode >= 300) {
			// 罕见 3xx：瞬时失败，可重试。
			throw new TransientSignalingFailure(
				retryableFailure("QWEN_SIGNALING_FAILED",
						signalingFailureMessage(statusCode, response.body())));
		}
		if (response.body().length() > properties.getMaxAnswerBytes()) {
			throw retryableFailure("QWEN_ANSWER_TOO_LARGE", "Qwen answer SDP exceeds the configured limit");
		}
		RealtimeFlowLog.info("flow.3.sdp.response status={} answerSdp={}",
				statusCode,
				RealtimeFlowLog.sdpSummary(response.body()));
			return new ExchangeResult(response.body(), officialRequestId(response), officialSessionId(response));
		}

		private static String officialRequestId(HttpResponse<?> response) {
		return response.headers().firstValue("x-request-id")
				.or(() -> response.headers().firstValue("x-dashscope-request-id"))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.orElse(null);
		}

		private static String officialSessionId(HttpResponse<?> response) {
			return response.headers().firstValue("x-dashscope-session-id")
					.or(() -> response.headers().firstValue("x-session-id"))
					.or(() -> response.headers().firstValue("x-provider-session-id"))
					.map(String::trim)
					.filter(value -> !value.isBlank())
					.orElse(null);
		}

		private String signalingFailureMessage(int statusCode, String body) {
		String detail = body == null ? "" : body.replaceAll("\\s+", " ").trim();
		if (detail.length() > 500) {
			detail = detail.substring(0, 500) + "...";
		}

			return "Qwen signaling returned " + statusCode
				+ (detail.isBlank() ? "" : ": " + detail);
	}

	/** 瞬时信令失败载体：携带重试后需抛出的业务异常（避免二次重试吞掉原始失败）。 */
	private static final class TransientSignalingFailure extends RuntimeException {

		private final BusinessException failure;

		private TransientSignalingFailure(BusinessException failure) {
			super(failure.getMessage());
			this.failure = failure;
		}

		private BusinessException failure() {
			return failure;
		}
	}

		private record ExchangeResult(String answerSdp, String requestId, String providerSessionId) {}

}
