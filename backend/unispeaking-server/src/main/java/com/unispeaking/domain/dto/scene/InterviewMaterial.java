package com.unispeaking.domain.dto.scene;

import java.util.List;

/**
 * 面试岗位确认后的结构化可编辑材料。
 * <p>JD 核心字段 {@code responsibilities} 与 {@code qualificationRequirements} 必须非空；
 * 其余列表可为空。{@code finalText} 是服务端确定性渲染的展示文本，与事实来源零漂移。
 * 列表一律以 {@link List#copyOf} 保护，避免调用方修改共享可变集合。
 */
public record InterviewMaterial(
		String jobTitle,
		List<String> responsibilities,
		List<String> qualificationRequirements,
		List<String> requiredSkills,
		String otherJobInformation,
		List<String> education,
		List<String> workExperiences,
		List<String> projectExperiences,
		List<String> skillsAndAbilities,
		List<String> interviewableExperienceClues,
		String finalText) {

	public InterviewMaterial {
		responsibilities = copy(responsibilities);
		qualificationRequirements = copy(qualificationRequirements);
		requiredSkills = copy(requiredSkills);
		education = copy(education);
		workExperiences = copy(workExperiences);
		projectExperiences = copy(projectExperiences);
		skillsAndAbilities = copy(skillsAndAbilities);
		interviewableExperienceClues = copy(interviewableExperienceClues);
	}

	private static List<String> copy(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
