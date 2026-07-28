package com.unispeaking.infrastructure.ai.aliyun;

import com.unispeaking.domain.dto.ai.SpeechAudioRequest;
import com.unispeaking.domain.dto.ai.SpeechAudioResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.TtsProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AliyunTtsProvider extends TtsProvider {

	public AliyunTtsProvider() {
		super(ProviderType.ALIYUN, Set.of(AiProviderRegistry.ALIYUN_TTS));
	}

	@Override
	public SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request) {
		throw capabilityNotConfigured(AiProviderRegistry.ALIYUN_TTS);
	}
}
