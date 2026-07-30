package com.unispeaking.service.tts.impl;

import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.tts.TtsService;
import org.springframework.stereotype.Service;

@Service
public class TtsServiceImpl implements TtsService {

	private final AiProviderRegistry providerRegistry;

	public TtsServiceImpl(AiProviderRegistry providerRegistry) {
		this.providerRegistry = providerRegistry;
	}

	@Override
	public byte[] synthesize(String text, String model) {
		Byte[] boxed = model == null || model.isBlank()
				? providerRegistry.generateSpeechAudio(text, null)
				: providerRegistry.generateSpeechAudio(model, text, null);
		if (boxed == null || boxed.length == 0) {
			throw new BusinessException("TTS_AUDIO_EMPTY", "TTS 未返回音频");
		}
		byte[] audio = new byte[boxed.length];
		for (int index = 0; index < boxed.length; index++) {
			if (boxed[index] == null) {
				throw new BusinessException("TTS_AUDIO_INVALID", "TTS 返回的音频无效");
			}
			audio[index] = boxed[index];
		}
		return audio;
	}
}
