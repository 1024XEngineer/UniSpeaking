package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 生成面试场景请求：确认后的结构化材料 + 难度。
 * <p>不接收原始 JD/简历/图片；{@code InterviewContext} 由后端生成保存、不返前端。
 */
public record InterviewSceneRequest(
		@Valid
		@NotNull(message = "确认材料不能为空")
		InterviewMaterial material,
		@NotNull(message = "面试难度不能为空")
		InterviewDifficulty difficulty) {
}
