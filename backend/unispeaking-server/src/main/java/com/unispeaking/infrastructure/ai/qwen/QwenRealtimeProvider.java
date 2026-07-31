package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
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

	public QwenRealtimeProvider(HttpClient realtimeHttpClient, RealtimeProperties properties) {
		super(
				ProviderType.QWEN,
				Set.of(
						AiProviderRegistry.QWEN_REALTIME_FLASH,
						AiProviderRegistry.QWEN_REALTIME_PLUS));
		this.httpClient = realtimeHttpClient;
		this.properties = properties;
	}

	@Override
	public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
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
		String sdpExchangeUrl = properties.getWebRtcSdpExchangeUrl(model);
		if (sdpExchangeUrl.isBlank()) {
			throw retryableFailure(
					"QWEN_WORKSPACE_OR_MODEL_MISSING",
					"Set BAILIAN_WORKSPACE_ID before starting a Qwen realtime session");
		}
		try {
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
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw retryableFailure("QWEN_SIGNALING_FAILED",
						"Qwen signaling returned " + response.statusCode());
			}
			if (response.body().length() > properties.getMaxAnswerBytes()) {
				throw retryableFailure("QWEN_ANSWER_TOO_LARGE", "Qwen answer SDP exceeds the configured limit");
			}
			RealtimeFlowLog.info("flow.3.sdp.response status={} answerSdp={}",
					response.statusCode(),
					RealtimeFlowLog.sdpSummary(response.body()));
			return response.body();
		}
		catch (IOException exception) {
			throw retryableFailure("QWEN_SIGNALING_IO_ERROR", "Failed to call Qwen signaling");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure("QWEN_SIGNALING_INTERRUPTED", "Qwen signaling call was interrupted");
		}
	}

}
