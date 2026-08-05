package com.unispeaking.domain.vo.scene;

import com.unispeaking.common.exception.BusinessException;
import java.util.Arrays;

public enum IeltsExaminerVoice {

	DANIEL("daniel", "Daniel", "Harvey"),
	MARCUS("marcus", "Marcus", "Aiden"),
	MARGARET("margaret", "Margaret", "Mione"),
	SOPHIA("sophia", "Sophia", "Maia");

	private final String examinerId;
	private final String examinerName;
	private final String voiceId;

	IeltsExaminerVoice(
			String examinerId,
			String examinerName,
			String voiceId) {
		this.examinerId = examinerId;
		this.examinerName = examinerName;
		this.voiceId = voiceId;
	}

	public String examinerId() {
		return examinerId;
	}

	public String voiceId() {
		return voiceId;
	}

	public String examinerName() {
		return examinerName;
	}

	public static IeltsExaminerVoice fromExaminerId(String examinerId) {
		return Arrays.stream(values())
				.filter(value -> value.examinerId.equalsIgnoreCase(examinerId))
				.findFirst()
				.orElseThrow(IeltsExaminerVoice::unsupported);
	}

	public static IeltsExaminerVoice fromVoiceId(String voiceId) {
		return Arrays.stream(values())
				.filter(value -> value.voiceId.equalsIgnoreCase(voiceId))
				.findFirst()
				.orElseThrow(IeltsExaminerVoice::unsupported);
	}

	private static BusinessException unsupported() {
		return new BusinessException(
				"IELTS_EXAMINER_VOICE_INVALID",
				"请选择有效的 IELTS 考官音色");
	}
}
