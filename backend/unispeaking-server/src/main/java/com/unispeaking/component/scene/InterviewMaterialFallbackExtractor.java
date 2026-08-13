package com.unispeaking.component.scene;

import com.unispeaking.domain.dto.scene.InterviewMaterial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 面试材料的确定性兜底提取器：只从已经脱敏的 JD/简历文本中提取，不调用模型。
 */
@Component
public final class InterviewMaterialFallbackExtractor {

	private static final Pattern RESPONSIBILITY_HEADING = Pattern.compile(
			"(?i)^(?:岗位职责|工作职责|主要职责|职责描述|responsibilities|job responsibilities|what you(?:'|’)ll do|duties)$");
	private static final Pattern REQUIREMENT_HEADING = Pattern.compile(
			"(?i)^(?:任职要求|岗位要求|资格要求|职位要求|requirements|qualifications|what you bring|preferred qualifications)$");
	private static final Pattern TITLE_LINE = Pattern.compile(
			"(?i)^(?:职位|岗位|招聘职位|职位名称|position|role|job title)\\s*[：:]\\s*(.+)$");
	private static final Pattern RESPONSIBILITY_HINT = Pattern.compile(
			"(?i)(负责|参与|推进|设计|开发|维护|搭建|管理|协调|优化|own|lead|build|design|develop|maintain|manage)");
	private static final Pattern REQUIREMENT_HINT = Pattern.compile(
			"(?i)(要求|具备|熟悉|掌握|本科|硕士|经验|年以上|proficient|experience|knowledge|bachelor|master|degree)");

	public InterviewMaterial extract(
			String jobDescriptionText,
			String resumeText,
			boolean resumeAbsent) {
		List<String> lines = lines(jobDescriptionText);
		List<String> responsibilities = section(lines, RESPONSIBILITY_HEADING);
		List<String> requirements = section(lines, REQUIREMENT_HEADING);
		if (responsibilities.isEmpty() || requirements.isEmpty()) {
			List<String> classifiedResponsibilities = new ArrayList<>();
			List<String> classifiedRequirements = new ArrayList<>();
			for (String line : contentLines(lines)) {
				if (RESPONSIBILITY_HINT.matcher(line).find()) {
					classifiedResponsibilities.add(line);
				}
				if (REQUIREMENT_HINT.matcher(line).find()) {
					classifiedRequirements.add(line);
				}
			}
			if (responsibilities.isEmpty()) {
				responsibilities = classifiedResponsibilities;
			}
			if (requirements.isEmpty()) {
				requirements = classifiedRequirements;
			}
		}

		responsibilities = distinct(responsibilities);
		requirements = distinct(requirements);
		if (responsibilities.isEmpty() || requirements.isEmpty()) {
			return null;
		}

		String jobTitle = extractTitle(lines);
		List<String> skills = extractSkillLines(lines);
		String otherInformation = extractOtherInformation(lines);
		List<String> education = extractResumeSection(resumeText, "(?i)^(?:教育经历|教育背景|education)$");
		List<String> work = extractResumeSection(resumeText, "(?i)^(?:工作经历|工作经验|work experience|employment)$");
		List<String> projects = extractResumeSection(resumeText, "(?i)^(?:项目经历|项目经验|projects|project experience)$");
		return new InterviewMaterial(
				jobTitle,
				responsibilities,
				requirements,
				skills,
				otherInformation,
				education,
				work,
				projects,
				List.of(),
				List.of(),
				null);
	}

	private List<String> section(List<String> lines, Pattern heading) {
		List<String> values = new ArrayList<>();
		boolean active = false;
		for (String line : lines) {
			if (heading.matcher(normalizeHeading(line)).matches()) {
				active = true;
				continue;
			}
			if (active && isHeading(line)) {
				break;
			}
			if (active && !isHeading(line)) {
				values.add(line);
			}
		}
		return values;
	}

	private boolean isHeading(String line) {
		String normalized = normalizeHeading(line);
		return RESPONSIBILITY_HEADING.matcher(normalized).matches()
				|| REQUIREMENT_HEADING.matcher(normalized).matches()
				|| normalized.matches("(?i)^(?:福利|薪资|地点|location|salary|benefits|about us|公司介绍)$");
	}

	private String normalizeHeading(String line) {
		return line.strip().replaceAll("[：:]$", "");
	}

	private List<String> contentLines(List<String> lines) {
		return lines.stream().filter(line -> !isHeading(line)).toList();
	}

	private String extractTitle(List<String> lines) {
		for (String line : lines) {
			var matcher = TITLE_LINE.matcher(line);
			if (matcher.matches()) {
				return matcher.group(1).strip();
			}
		}
		return null;
	}

	private List<String> extractSkillLines(List<String> lines) {
		for (String line : lines) {
			if (line.matches("(?i)^(?:技能|必备技能|skills|technical skills)\\s*[：:].+$")) {
				return splitValue(line.replaceFirst("(?i)^(?:技能|必备技能|skills|technical skills)\\s*[：:]", ""));
			}
		}
		return List.of();
	}

	private String extractOtherInformation(List<String> lines) {
		List<String> values = new ArrayList<>();
		for (String line : lines) {
			if (line.matches("(?i)^(?:工作地点|地点|location|薪资|salary|福利|benefits)\\s*[：:].+$")) {
				values.add(line);
			}
		}
		return values.isEmpty() ? null : String.join("；", values);
	}

	private List<String> extractResumeSection(String text, String headingExpression) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		Pattern heading = Pattern.compile(headingExpression);
		return section(lines(text), heading);
	}

	private List<String> lines(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (String raw : text.replace('\r', '\n').split("\\n+")) {
			String line = raw.strip().replaceFirst("^(?:[-*•·]\\s*)+", "");
			if (!line.isBlank()) {
				values.add(line);
			}
		}
		return List.copyOf(values);
	}

	private List<String> splitValue(String value) {
		return distinct(List.of(value.split("\\r?\\n|[；;、,，]")));
	}

	private List<String> distinct(List<String> values) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			String normalized = value == null ? "" : value.strip();
			if (!normalized.isBlank()) {
				result.add(normalized);
			}
		}
		return result.stream().limit(50).toList();
	}
}
