package com.unispeaking.infrastructure.ai.minimax;

import com.unispeaking.domain.dto.ai.SpeechAudioRequest;
import com.unispeaking.domain.dto.ai.SpeechAudioResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.TtsProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MiniMaxTtsProvider extends TtsProvider {

	public MiniMaxTtsProvider() {
		super(ProviderType.MINIMAX, Set.of(AiProviderRegistry.MINIMAX_TTS));
	}

	@Override
	public SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request) {
		throw capabilityNotConfigured(AiProviderRegistry.MINIMAX_TTS);
	}
}
