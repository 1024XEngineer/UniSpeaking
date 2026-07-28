package com.unispeaking.infrastructure.ai.iflytek;

import com.unispeaking.domain.dto.ai.PronunciationEvaluationRequest;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.ScoringProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class IflytekScoringProvider extends ScoringProvider {

	public IflytekScoringProvider() {
		super(
				ProviderType.IFLYTEK,
				Set.of(AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING));
	}

	@Override
	public PronunciationEvaluationResponse evaluatePronunciation(
			PronunciationEvaluationRequest request) {
		throw capabilityNotConfigured(AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING);
	}
}
