package com.unispeaking.domain.dto.scene;

import java.util.List;

/**
 * LLM-2 生成的面试上下文，驱动实时面试官 systemPrompt。
 * <p>{@code candidateOverview} 无简历时须明确"无简历依据"；{@code interviewTopics}
 * 为 4~5 个主题，只描述话题不生成具体问题。列表用 {@link List#copyOf} 保护。
 */
public record InterviewContext(
		String candidateOverview,
		String roleOverview,
		List<String> interviewTopics) {

	public InterviewContext {
		interviewTopics = interviewTopics == null ? List.of() : List.copyOf(interviewTopics);
	}
}
