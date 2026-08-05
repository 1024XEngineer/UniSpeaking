package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.IeltsPart;

/**
 * IELTS 当前 Part 的确定性题目推进状态。
 */
public record IeltsDialogueStateResponse(
		String sceneId,
		String sessionId,
		IeltsPart part,
		boolean openingCompleted,
		int answeredQuestions,
		int totalQuestions,
		boolean completed,
		String controlInstruction) {
}
