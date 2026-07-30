package com.unispeaking.service.tts;

public interface TtsService {

	byte[] synthesize(String text, String model);
}
