package com.unispeaking.infrastructure.persistence.codec.scene;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InterviewJsonbCodecTest {

	private static final String VALID_JSON = """
			{
			  "overview": "SaaS product role",
			  "responsibilities": ["Plan delivery"],
			  "required_skills": ["Communication"],
			  "qualification_requirements": []
			}
			""";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final InterviewJsonbCodec codec =
			new InterviewJsonbCodec(objectMapper);

	@Test
	void encodesExactSnakeCaseFieldsAndEmptyArrays() throws Exception {
		TargetRoleSummary summary = new TargetRoleSummary(
				"SaaS product role",
				List.of(),
				List.of(),
				List.of());

		String json = codec.encodeRoleSummary(summary);
		JsonNode root = objectMapper.readTree(json);

		assertAll(
				() -> assertEquals(
						Set.of(
								"overview",
								"responsibilities",
								"required_skills",
								"qualification_requirements"),
						Set.copyOf(root.propertyNames())),
				() -> assertFalse(root.has("requiredSkills")),
				() -> assertFalse(root.has("qualificationRequirements")),
				() -> assertEquals(0, root.get("responsibilities").size()),
				() -> assertEquals(0, root.get("required_skills").size()),
				() -> assertEquals(0,
						root.get("qualification_requirements").size()),
				() -> assertEquals(
						"{\"overview\":\"SaaS product role\","
								+ "\"responsibilities\":[],"
								+ "\"required_skills\":[],"
								+ "\"qualification_requirements\":[]}",
						json));
	}

	@Test
	void roundTripsRoleSummaryWithImmutableLists() {
		TargetRoleSummary decoded = codec.decodeRoleSummary(VALID_JSON);

		assertAll(
				() -> assertEquals("SaaS product role", decoded.overview()),
				() -> assertEquals(List.of("Plan delivery"),
						decoded.responsibilities()),
				() -> assertEquals(List.of("Communication"),
						decoded.requiredSkills()),
				() -> assertEquals(List.of(),
						decoded.qualificationRequirements()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> decoded.requiredSkills().clear()),
				() -> assertEquals(decoded,
						codec.decodeRoleSummary(codec.encodeRoleSummary(decoded))));
	}

	@Test
	void rejectsMalformedDuplicateAndTrailingJson() {
		assertAll(
				() -> assertInvalid("not-json"),
				() -> assertInvalid(VALID_JSON.replace(
						"\"overview\": \"SaaS product role\",",
						"\"overview\": \"SaaS product role\","
								+ "\"overview\": \"Duplicate\",")),
				() -> assertInvalid(VALID_JSON + "{}"),
				() -> assertInvalid("[]"),
				() -> assertInvalid(null),
				() -> assertInvalid(" "));
	}

	@Test
	void rejectsMissingUnknownAndWrongTypeFields() {
		assertAll(
				() -> assertInvalid(VALID_JSON.replace(
						"\"required_skills\": [\"Communication\"],",
						"")),
				() -> assertInvalid(VALID_JSON.replace(
						"\"required_skills\":",
						"\"unknown\": [], \"required_skills\":")),
				() -> assertInvalid(VALID_JSON.replace(
						"[\"Plan delivery\"]",
						"null")),
				() -> assertInvalid(VALID_JSON.replace(
						"[\"Communication\"]",
						"[null]")),
				() -> assertInvalid(VALID_JSON.replace(
						"\"SaaS product role\"",
						"\" \"")));
	}

	@Test
	void convertsFailuresWithoutLeakingUserContentOrMapperState() {
		boolean trailingTokensInitiallyEnabled = objectMapper.isEnabled(
				DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		InterviewJsonbCodec isolatedCodec =
				new InterviewJsonbCodec(objectMapper);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> isolatedCodec.decodeRoleSummary(
						"{\"resume_secret\":\"private candidate data\"}"));

		assertAll(
				() -> assertEquals("INTERVIEW_DATA_INVALID", exception.code()),
				() -> assertEquals(
						"Interview persistence data is invalid",
						exception.getMessage()),
				() -> assertFalse(exception.getMessage().contains("resume_secret")),
				() -> assertFalse(exception.getMessage().contains(
						"private candidate data")),
				() -> assertNull(exception.getCause()),
				() -> assertEquals(
						trailingTokensInitiallyEnabled,
						objectMapper.isEnabled(
								DeserializationFeature.FAIL_ON_TRAILING_TOKENS)),
				() -> assertThrows(
						BusinessException.class,
						() -> isolatedCodec.encodeRoleSummary(null)));
	}

	private void assertInvalid(String json) {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> codec.decodeRoleSummary(json));

		assertAll(
				() -> assertEquals("INTERVIEW_DATA_INVALID", exception.code()),
				() -> assertEquals(
						"Interview persistence data is invalid",
						exception.getMessage()),
				() -> assertNull(exception.getCause()));
	}
}
