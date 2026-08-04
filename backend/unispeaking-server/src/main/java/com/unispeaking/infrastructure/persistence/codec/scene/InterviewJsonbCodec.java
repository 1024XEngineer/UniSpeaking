package com.unispeaking.infrastructure.persistence.codec.scene;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Strictly converts the role summary between its domain value and JSONB text.
 */
@Component
public final class InterviewJsonbCodec {

	private static final Set<String> ROLE_SUMMARY_FIELDS = Set.of(
			"overview",
			"responsibilities",
			"required_skills",
			"qualification_requirements");
	private static final String ERROR_CODE = "INTERVIEW_DATA_INVALID";
	private static final String ERROR_MESSAGE =
			"Interview persistence data is invalid";

	private final ObjectMapper objectMapper;
	private final ObjectReader strictTreeReader;
	private final ObjectWriter treeWriter;

	/**
	 * Creates isolated readers and writers without changing the shared mapper.
	 */
	public InterviewJsonbCodec(ObjectMapper objectMapper) {
		this.objectMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
		this.strictTreeReader = objectMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		this.treeWriter = objectMapper.writerFor(JsonNode.class);
	}

	public String encodeRoleSummary(TargetRoleSummary summary) {
		if (summary == null) {
			throw invalidData();
		}
		try {
			ObjectNode root = objectMapper.createObjectNode();
			root.put("overview", summary.overview());
			writeStrings(root, "responsibilities", summary.responsibilities());
			writeStrings(root, "required_skills", summary.requiredSkills());
			writeStrings(
					root,
					"qualification_requirements",
					summary.qualificationRequirements());
			return treeWriter.writeValueAsString(root);
		}
		catch (RuntimeException exception) {
			throw invalidData();
		}
	}

	public TargetRoleSummary decodeRoleSummary(String json) {
		if (json == null || json.isBlank()) {
			throw invalidData();
		}
		try {
			JsonNode root = strictTreeReader.readTree(json);
			if (root == null
					|| !root.isObject()
					|| !Set.copyOf(root.propertyNames())
							.equals(ROLE_SUMMARY_FIELDS)) {
				throw invalidData();
			}
			return new TargetRoleSummary(
					requireText(root.get("overview")),
					readStrings(root.get("responsibilities")),
					readStrings(root.get("required_skills")),
					readStrings(root.get("qualification_requirements")));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw invalidData();
		}
	}

	private void writeStrings(
			ObjectNode root,
			String fieldName,
			List<String> values) {
		ArrayNode array = root.putArray(fieldName);
		values.forEach(array::add);
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString() || value.asString().isBlank()) {
			throw invalidData();
		}
		return value.asString();
	}

	private List<String> readStrings(JsonNode value) {
		if (value == null || !value.isArray()) {
			throw invalidData();
		}
		List<String> values = new ArrayList<>();
		for (JsonNode element : value) {
			if (!element.isString()) {
				throw invalidData();
			}
			values.add(element.asString());
		}
		return values;
	}

	private BusinessException invalidData() {
		return new BusinessException(ERROR_CODE, ERROR_MESSAGE);
	}
}
