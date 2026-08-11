package com.unispeaking.infrastructure.ai.qiniu;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeTransportType;
import com.unispeaking.infrastructure.config.QiniuRealtimeProperties;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.RealtimeProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class QiniuRealtimeProvider extends RealtimeProvider {

	private final QiniuRtiClient client;
	private final QiniuRealtimeProperties properties;

	public QiniuRealtimeProvider(
			QiniuRtiClient client,
			QiniuRealtimeProperties properties) {
		super(ProviderType.QINIU, Set.of(AiProviderRegistry.QINIU_REALTIME_PLUS));
		this.client = client;
		this.properties = properties;
	}

	@Override
	public RealtimeConnectionResult connect(RealtimeConnectCommand command) {
		validate(command);
		String voiceProfile = properties.voiceProfile(command.voiceId());
		if (voiceProfile.isBlank()) {
			throw retryableFailure(
					"QINIU_VOICE_PROFILE_NOT_CONFIGURED",
					"No Qiniu voice profile is configured for voice " + command.voiceId());
		}

		QiniuRtiClient.CreatedSession created;
		try {
			created = client.createSession(command, voiceProfile);
		}
		catch (BusinessException exception) {
			throw classify(exception);
		}
		try {
			String answerSdp = client.exchangeSdp(created, command.offerSdp());
			return new RealtimeConnectionResult(
					created.sessionId(),
					answerSdp,
					created.expiresAt(),
					ProviderType.QINIU,
					command.modelId(),
					RealtimeTransportType.PLATFORM_RTC);
		}
		catch (BusinessException exception) {
			bestEffortStop(created.sessionId(), "signaling_failed");
			throw classify(exception);
		}
	}

	@Override
	public void stop(String providerSessionId) {
		try {
			client.stopSession(providerSessionId, "client_completed");
		}
		catch (BusinessException exception) {
			throw classify(exception);
		}
	}

	@Override
	public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
		throw nonRetryableFailure(
				"QINIU_SESSION_CONTEXT_REQUIRED",
				"Qiniu RTI requires a control-plane session before SDP exchange");
	}

	private void validate(RealtimeConnectCommand command) {
		if (command == null) {
			throw nonRetryableFailure(
					"INVALID_REALTIME_COMMAND",
					"Realtime connection command is required");
		}
		if (!supports(command.modelId())) {
			throw nonRetryableFailure(
					"QINIU_REALTIME_MODEL_NOT_SUPPORTED",
					"Qiniu realtime model is not registered: " + command.modelId());
		}
		if (command.offerSdp() == null || command.offerSdp().isBlank()) {
			throw nonRetryableFailure("INVALID_SDP", "WebRTC offer SDP is required");
		}
		if (command.localSessionId() == null || command.localSessionId().isBlank()
				|| command.userId() == null || command.userId().isBlank()) {
			throw nonRetryableFailure(
					"INVALID_REALTIME_SESSION",
					"Local session and user IDs are required");
		}
		if (properties.apiKey().isBlank()
				|| properties.appId().isBlank()
				|| properties.modelProfile().isBlank()
				|| properties.roleProfile().isBlank()) {
			throw retryableFailure(
					"QINIU_RTI_NOT_CONFIGURED",
					"Qiniu RTI credentials and profiles are not fully configured");
		}
	}

	private BusinessException classify(BusinessException exception) {
		if ("QINIU_RTI_INVALID_REQUEST".equals(exception.code())
				|| "QINIU_RTI_REQUEST_INTERRUPTED".equals(exception.code())) {
			return nonRetryableFailure(exception.code(), exception.getMessage());
		}
		return retryableFailure(exception.code(), exception.getMessage());
	}

	private void bestEffortStop(String providerSessionId, String reason) {
		try {
			client.stopSession(providerSessionId, reason);
		}
		catch (RuntimeException ignored) {
			// Preserve the original signaling failure.
		}
	}
}
