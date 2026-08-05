package com.unispeaking.domain.vo.scene;

/**
 * Temporary recognized JD text. This type has no persistence representation.
 */
public final class InterviewJobDescriptionOcrText {

	private final String text;

	InterviewJobDescriptionOcrText(String text) {
		this.text = text;
	}

	public String text() {
		return text;
	}

	@Override
	public String toString() {
		return "InterviewJobDescriptionOcrText[codePoints="
				+ text.codePointCount(0, text.length()) + "]";
	}
}
