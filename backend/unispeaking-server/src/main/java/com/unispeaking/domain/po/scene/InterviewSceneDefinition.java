package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import java.time.OffsetDateTime;

/**
 * 面试场景资产持久化记录（PO ↔ Entity 双层转换的上层领域对象）。
 * <p>{@code confirmedMaterialJson} 是 LLM-2 事实来源；{@code finalText} 为确定性渲染展示文本；
 * {@code interviewContextJson} 为 LLM-2 生成的面试上下文；{@code difficulty} 保存但不重复返回。
 */
public record InterviewSceneDefinition(
		String sceneId,
		String userId,
		String confirmedMaterialJson,
		String finalText,
		String interviewContextJson,
		InterviewDifficulty difficulty,
		String scenePrompt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		OffsetDateTime deletedAt) {
}
