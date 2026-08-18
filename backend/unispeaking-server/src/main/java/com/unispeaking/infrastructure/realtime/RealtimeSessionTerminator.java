package com.unispeaking.infrastructure.realtime;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.provider.AiProviderRegistry;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSessionTerminator {

	private final AiProviderRegistry providerRegistry;

	public RealtimeSessionTerminator(AiProviderRegistry providerRegistry) {
		this.providerRegistry = providerRegistry;
	}

	public void stopBestEffort(AbstractSceneSession session, String reason) {
		if (session == null
				|| session.getModel() == null
				|| session.getModel().isBlank()) {
			return;
		}
		try {
			if (session.getProviderSessionId() != null && !session.getProviderSessionId().isBlank()) {
				var provider = providerRegistry.getRealtimeProvider(session.getModel());
				provider.stopSession(session.getProviderSessionId(), null, reason);
			}
		}
		catch (RuntimeException exception) {
			RealtimeFlowLog.warn(
					"realtime.provider.stop.failed localSessionId={} providerSessionId={} provider={} error={}",
					session.getId(),
					session.getProviderSessionId(),
					session.getProviderType(),
					exception.getMessage());
		}
		finally {
			try {
				providerRegistry.recordRealtimeSession(
						session.getUserId(), session.getId(), session.getModel(),
						session.getCreatedAt(), session.getEndedAt() == null ? java.time.Instant.now() : session.getEndedAt());
			}
			catch (RuntimeException exception) {
				RealtimeFlowLog.warn(
						"realtime.usage.record.failed localSessionId={} model={} error={}",
						session.getId(), session.getModel(), exception.getMessage());
			}
		}
	}
}
