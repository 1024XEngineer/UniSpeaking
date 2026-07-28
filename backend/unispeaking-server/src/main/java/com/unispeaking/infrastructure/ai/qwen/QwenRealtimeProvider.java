package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeRequest;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.exception.BusinessException;
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
	public RealtimeSdpExchangeResponse exchangeRealtimeSdp(RealtimeSdpExchangeRequest request) {
		if (request == null) {
			throw new BusinessException("INVALID_SDP_REQUEST", "Realtime SDP exchange request is required");
		}
		String offerSdp = request.offerSdp();
		if (offerSdp == null || offerSdp.isBlank()) {
			throw new BusinessException("INVALID_SDP", "WebRTC offer SDP is required");
		}
		if (request.apiKey() == null || request.apiKey().isBlank()) {
			throw new BusinessException("QWEN_CREDENTIAL_MISSING", "Qwen bearer credential is not configured");
		}
		String model = request.model() == null || request.model().isBlank()
				? AiProviderRegistry.QWEN_REALTIME_FLASH
				: request.model().trim();
		if (!supports(model)) {
			throw new BusinessException(
					"QWEN_REALTIME_MODEL_NOT_SUPPORTED",
					"Qwen realtime model is not registered: " + model);
		}
		String sdpExchangeUrl = properties.getWebRtcSdpExchangeUrl(model);
		if (sdpExchangeUrl.isBlank()) {
			throw new BusinessException(
					"QWEN_WORKSPACE_OR_MODEL_MISSING",
					"Set BAILIAN_WORKSPACE_ID before starting a Qwen realtime session");
		}
		String userId = request.context() == null ? null : request.context().userId();
		String localSessionId = request.context() == null ? null : request.context().businessId();
		try {
			RealtimeFlowLog.info("flow.3.sdp.request localSessionId={} userId={} provider={} url={} model={} temporaryToken={} offerSdp={}",
					localSessionId, userId, type(), sdpExchangeUrl, model,
					RealtimeFlowLog.maskSecret(request.apiKey()),
					RealtimeFlowLog.sdpSummary(offerSdp));
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(URI.create(sdpExchangeUrl))
					.timeout(properties.getReadTimeout())
					.header("Authorization", "Bearer " + request.apiKey())
					.header("Content-Type", "application/sdp")
					.POST(HttpRequest.BodyPublishers.ofString(offerSdp))
					.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new BusinessException("QWEN_SIGNALING_FAILED",
						"Qwen signaling returned " + response.statusCode());
			}
			if (response.body().length() > properties.getMaxAnswerBytes()) {
				throw new BusinessException("QWEN_ANSWER_TOO_LARGE", "Qwen answer SDP exceeds the configured limit");
			}
			RealtimeFlowLog.info("flow.3.sdp.response localSessionId={} status={} answerSdp={}",
					localSessionId, response.statusCode(),
					RealtimeFlowLog.sdpSummary(response.body()));
			return new RealtimeSdpExchangeResponse(response.body(), null);
		}
		catch (IOException exception) {
			throw new BusinessException("QWEN_SIGNALING_IO_ERROR", "Failed to call Qwen signaling");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BusinessException("QWEN_SIGNALING_INTERRUPTED", "Qwen signaling call was interrupted");
		}
	}

}
