package com.unispeaking.domain.dto.profile;

import com.unispeaking.domain.vo.profile.CefrLevel;
import com.unispeaking.domain.vo.profile.PreferredAiSpeechSpeed;
import com.unispeaking.domain.vo.profile.PreferredVoice;
import jakarta.validation.constraints.Size;

public record UpdateUserPreferenceRequest(
		PreferredVoice preferredVoice,
		PreferredAiSpeechSpeed preferredAiSpeechSpeed,
		CefrLevel cefrLevel,
		@Size(max = 4000, message = "长期用户资料不能超过4000个字符")
		String memoryText) {
}
