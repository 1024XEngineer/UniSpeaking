package com.unispeaking.component.scene;

import com.unispeaking.domain.dto.scene.InterviewMaterial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * 面试 LLM-1 响应归一化器：只放宽可恢复的格式漂移，核心岗位字段仍然严格校验。
 */
@Component
public final class InterviewMaterialResponseNormalizer {

	private static final int MAX_LIST_ITEMS = 50;
	private static final int MAX_ITEM_LENGTH = 2000;
	private static final int MAX_TITLE_LENGTH = 100;

	private final ObjectReader strictReader;

	public InterviewMaterialResponseNormalizer(ObjectMapper objectMapper) {
		this.strictReader = objectMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}

	public ParseResult parse(String content) {
		List<String> errors = new ArrayList<>();
		JsonNode root = readObject(content, errors);
		if (root == null) {
			return new ParseResult(null, List.copyOf(errors));
		}
		JsonNode materialNode = root.path("material");
		if (materialNode.isObject()) {
			root = materialNode;
		}

		String jobTitle = optionalText(root, errors, MAX_TITLE_LENGTH,
				"jobTitle", "job_title", "position", "role", "title");
		List<String> responsibilities = list(root, errors,
				"responsibilities", "responsibility", "duties", "job_responsibilities");
		List<String> qualificationRequirements = list(root, errors,
				"qualificationRequirements", "qualification_requirements",
				"requirements", "qualifications");
		List<String> requiredSkills = list(root, errors,
				"requiredSkills", "required_skills", "skills", "technical_skills");
		String otherJobInformation = optionalText(root, errors, 2000,
				"otherJobInformation", "other_job_information", "additional_information");
		List<String> education = list(root, errors, "education", "educations");
		List<String> workExperiences = list(root, errors,
				"workExperiences", "work_experiences", "workExperience", "experience");
		List<String> projectExperiences = list(root, errors,
				"projectExperiences", "project_experiences", "projects");
		List<String> skillsAndAbilities = list(root, errors,
				"skillsAndAbilities", "skills_and_abilities", "abilities");
		List<String> interviewableExperienceClues = list(root, errors,
				"interviewableExperienceClues", "interviewable_experience_clues",
				"experience_clues");
		String finalText = optionalText(root, errors, 2000, "finalText", "final_text", "summary");

		if (responsibilities.isEmpty()) {
			errors.add("responsibilities must contain at least one item");
		}
		if (qualificationRequirements.isEmpty()) {
			errors.add("qualificationRequirements must contain at least one item");
		}

		InterviewMaterial material = new InterviewMaterial(
				jobTitle,
				responsibilities,
				qualificationRequirements,
				requiredSkills,
				otherJobInformation,
				education,
				workExperiences,
				projectExperiences,
				skillsAndAbilities,
				interviewableExperienceClues,
				finalText);
		return new ParseResult(material, distinctErrors(errors));
	}

	private JsonNode readObject(String content, List<String> errors) {
		String value = content == null ? "" : content.strip();
		if (value.isBlank()) {
			errors.add("response is blank");
			return null;
		}

		for (String candidate : candidates(value)) {
			try {
				JsonNode root = strictReader.readTree(candidate);
				if (root != null && root.isObject()) {
					return root;
				}
			}
			catch (RuntimeException ignored) {
				// Try the next narrowly extracted JSON object.
			}
		}
		errors.add("response does not contain a valid JSON object");
		return null;
	}

	private List<String> candidates(String value) {
		LinkedHashSet<String> candidates = new LinkedHashSet<>();
		candidates.add(value);
		String unfenced = removeFence(value);
		candidates.add(unfenced);
		for (int start = 0; start < value.length(); start++) {
			if (value.charAt(start) != '{') {
				continue;
			}
			int end = balancedObjectEnd(value, start);
			if (end > start) {
				candidates.add(value.substring(start, end));
			}
		}
		return List.copyOf(candidates);
	}

	private String removeFence(String value) {
		String normalized = value.strip();
		if (!normalized.startsWith("```") || !normalized.endsWith("```")) {
			return normalized;
		}
		int firstLineEnd = normalized.indexOf('\n');
		if (firstLineEnd < 0) {
			return normalized;
		}
		return normalized.substring(firstLineEnd + 1, normalized.length() - 3).strip();
	}

	private int balancedObjectEnd(String value, int start) {
		int depth = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = start; index < value.length(); index++) {
			char current = value.charAt(index);
			if (quoted) {
				if (escaped) {
					escaped = false;
				}
				else if (current == '\\') {
					escaped = true;
				}
				else if (current == '"') {
					quoted = false;
				}
				continue;
			}
			if (current == '"') {
				quoted = true;
			}
			else if (current == '{') {
				depth++;
			}
			else if (current == '}' && --depth == 0) {
				return index + 1;
			}
		}
		return -1;
	}

	private String optionalText(
			JsonNode root,
			List<String> errors,
			int maxLength,
			String... names) {
		JsonNode value = first(root, names);
		if (value == null || value.isNull() || value.isMissingNode()) {
			return null;
		}
		if (!value.isString()) {
			errors.add(names[0] + " must be a string");
			return null;
		}
		String text = value.asString("").strip();
		if (text.isBlank()) {
			return null;
		}
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	private List<String> list(
			JsonNode root,
			List<String> errors,
			String... names) {
		JsonNode value = first(root, names);
		if (value == null || value.isNull() || value.isMissingNode()) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		if (value.isArray()) {
			for (JsonNode item : value) {
				if (!item.isString()) {
					continue;
				}
				addSplitValues(values, item.asString(""));
			}
		}
		else if (value.isString()) {
			addSplitValues(values, value.asString(""));
		}
		else {
			errors.add(names[0] + " must be an array or string");
		}

		Set<String> unique = new HashSet<>();
		List<String> normalized = new ArrayList<>();
		for (String item : values) {
			String text = item.strip();
			if (text.isBlank()) {
				continue;
			}
			if (text.length() > MAX_ITEM_LENGTH) {
				text = text.substring(0, MAX_ITEM_LENGTH);
			}
			if (unique.add(text.toLowerCase(Locale.ROOT))) {
				normalized.add(text);
			}
			if (normalized.size() >= MAX_LIST_ITEMS) {
				break;
			}
		}
		return List.copyOf(normalized);
	}

	private void addSplitValues(List<String> values, String value) {
		for (String line : value.split("\\r?\\n|[；;]")) {
			String normalized = line.strip().replaceFirst("^(?:[-*•·]\\s*)+", "");
			if (!normalized.isBlank()) {
				values.add(normalized);
			}
		}
	}

	private JsonNode first(JsonNode root, String... names) {
		for (String name : names) {
			JsonNode value = root.path(name);
			if (!value.isMissingNode()) {
				return value;
			}
		}
		return null;
	}

	private List<String> distinctErrors(List<String> errors) {
		return List.copyOf(new LinkedHashSet<>(errors));
	}

	public record ParseResult(InterviewMaterial material, List<String> errors) {
		public ParseResult {
			errors = errors == null ? List.of() : List.copyOf(errors);
		}

		public boolean valid() {
			return material != null
					&& !material.responsibilities().isEmpty()
					&& !material.qualificationRequirements().isEmpty();
		}
	}
}
