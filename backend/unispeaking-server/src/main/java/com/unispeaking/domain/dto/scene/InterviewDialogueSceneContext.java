package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;

/**
 * 面试对话场景上下文：由 {@code prepareDialogue} 完成归属校验后交给会话启动使用。
 *
 * @param userId       归属校验通过的当前用户标识
 * @param sceneId      面试场景标识
 * @param scenePrompt  实时会话使用的系统 Prompt
 * @param difficulty   面试难度（复练不可修改）
 */
public record InterviewDialogueSceneContext(
		String userId,
		String sceneId,
		String scenePrompt,
		InterviewDifficulty difficulty) {
}
