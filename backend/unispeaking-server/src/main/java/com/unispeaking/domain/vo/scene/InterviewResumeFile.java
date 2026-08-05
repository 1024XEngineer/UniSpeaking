package com.unispeaking.domain.vo.scene;

/**
 * In-memory resume upload. The content must never be persisted or logged.
 */
public record InterviewResumeFile(
		String filename,
		String mimeType,
		byte[] content) {

	public InterviewResumeFile {
		content = copy(content);
	}

	@Override
	public byte[] content() {
		return copy(content);
	}

	@Override
	public String toString() {
		return "InterviewResumeFile[filename=<redacted>, mimeType=" + mimeType
				+ ", contentBytes=" + (content == null ? 0 : content.length) + "]";
	}

	private static byte[] copy(byte[] content) {
		return content == null ? null : content.clone();
	}
}
