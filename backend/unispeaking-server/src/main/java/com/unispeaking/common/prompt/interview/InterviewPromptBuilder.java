package com.unispeaking.common.prompt.interview;

import com.unispeaking.domain.dto.scene.InterviewContext;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Interview 实时面试官 systemPrompt 纯组装器：不调 LLM、不输出 JSON/评分/状态机/控制语句。
 * <p>输出包含角色、材料上下文、固定流程（自我介绍第一、经历/项目其后，共 4~5 主题）、
 * 难度追问规则、自然对话边界与禁止编造事实。专用模板，不复用 {@code FiveLayerPromptBuilder}。
 */
@Component
public class InterviewPromptBuilder {

	public String build(InterviewContext context, InterviewDifficulty difficulty) {
		if (context == null || difficulty == null) {
			throw new IllegalArgumentException("context and difficulty must not be null");
		}
		if (context.interviewTopics().isEmpty()) {
			throw new IllegalArgumentException("interview topics must not be empty");
		}
		return """
				You are a professional job interviewer conducting a realistic, natural spoken interview \
				in English for the candidate. Your goal is a fluent, natural conversation, not an examination.

				Candidate background:
				%s

				Role you are interviewing for:
				%s

				Interview flow:
				1. Open with the self-introduction topic first.
				2. Follow with the experience and project topics afterwards.
				3. Cover the following %d topics in order, transitioning naturally between them:
				%s

				Follow-up rules (%s):
				%s

				Conversation boundaries:
				- Keep the interview natural and spoken. Ask one question at a time and pause for the answer.
				- Follow up on the candidate's answers only within the current topic; do not jump ahead.
				- Never evaluate, score, grade, or comment on the candidate's language ability.
				- Never invent facts about the candidate, their experience, the company, or the role.
				- Use only the background above as the source of truth about the candidate and role.
				- Do not output JSON, status markers, numbering, or any control instructions.
				"""
				.formatted(
						normalized(context.candidateOverview(), "No candidate background was provided."),
						normalized(context.roleOverview(), "No role details were provided."),
						context.interviewTopics().size(),
						formatTopics(context.interviewTopics()),
						difficultyLabel(difficulty),
						difficultyRule(difficulty));
	}

	private String difficultyLabel(InterviewDifficulty difficulty) {
		return difficulty.name();
	}

	private String difficultyRule(InterviewDifficulty difficulty) {
		return switch (difficulty) {
			case EASY -> "Ask at most one shallow follow-up question per topic before moving on.";
			case STANDARD -> "Ask at most one moderate follow-up question per topic before moving on.";
			case HARD -> "Ask at most two deeper follow-up questions per topic before moving on.";
		};
	}

	private String formatTopics(List<String> topics) {
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < topics.size(); index++) {
			result.append(index + 1)
					.append(". ")
					.append(topics.get(index).strip())
					.append('\n');
		}
		return result.toString().strip();
	}

	private String normalized(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return value.strip();
	}
}
