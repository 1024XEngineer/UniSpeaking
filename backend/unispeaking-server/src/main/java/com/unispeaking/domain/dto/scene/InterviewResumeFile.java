package com.unispeaking.domain.dto.scene;

import java.util.Arrays;

/**
 * 面试简历上传文件（仅接受 PDF/DOCX 文本简历）。
 * <p>{@code .doc} 在材料解析阶段被明确拒绝（{@code DOCUMENT_FORMAT_UNSUPPORTED}），
 * 本 DTO 不复制未脱敏内容之外的敏感信息。
 */
public record InterviewResumeFile(
		String filename,
		String mimeType,
		byte[] content) {

	public InterviewResumeFile {
		content = content == null ? null : Arrays.copyOf(content, content.length);
	}

	@Override
	public byte[] content() {
		return content == null ? null : Arrays.copyOf(content, content.length);
	}
}
