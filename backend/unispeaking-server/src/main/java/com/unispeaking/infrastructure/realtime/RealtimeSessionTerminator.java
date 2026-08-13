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
				|| session.getProviderSessionId() == null
				|| session.getProviderSessionId().isBlank()
				|| session.getModel() == null
				|| session.getModel().isBlank()) {
			return;
		}
		try {
			var provider = providerRegistry.getRealtimeProvider(session.getModel());
			provider.stopSession(session.getProviderSessionId(), null, reason);
		}
		catch (RuntimeException exception) {
			RealtimeFlowLog.warn(
					"realtime.provider.stop.failed localSessionId={} providerSessionId={} provider={} error={}",
					session.getId(),
					session.getProviderSessionId(),
					session.getProviderType(),
					exception.getMessage());
		}
	}
}
