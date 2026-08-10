package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.dto.ocr.OcrImage;

/**
 * 面试材料准备输入：原始 JD（必填，文本 XOR 单张图片）+ 简历（可选，文本 XOR PDF/DOCX 文件）。
 * <p>JD 必须提供 {@code jobDescriptionText} 或 {@code jobDescriptionImage} 二选一；简历两者均可缺省。
 * 空白文本在构造时归一为 {@code null}，避免空串被误判为已提供文本。
 */
public record InterviewMaterialPreparationInput(
		String resumeText,
		InterviewResumeFile resumeFile,
		String jobDescriptionText,
		OcrImage jobDescriptionImage) {

	public InterviewMaterialPreparationInput {
		resumeText = blankToNull(resumeText);
		jobDescriptionText = blankToNull(jobDescriptionText);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
