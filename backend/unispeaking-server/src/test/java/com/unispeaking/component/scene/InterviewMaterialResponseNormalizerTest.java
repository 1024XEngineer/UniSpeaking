package com.unispeaking.component.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InterviewMaterialResponseNormalizerTest {

	private final InterviewMaterialResponseNormalizer normalizer =
			new InterviewMaterialResponseNormalizer(new ObjectMapper());

	@Test
	void normalizesFenceSnakeCaseAndStringLists() {
		var result = normalizer.parse("""
				```JSON
				{
				  "job_title": "产品经理",
				  "responsibilities": "负责产品规划；负责产品规划\\n推进项目落地",
				  "qualification_requirements": ["本科以上", "本科以上"],
				  "required_skills": null
				}
				```
				""");

		assertTrue(result.valid(), result.errors().toString());
		assertEquals("产品经理", result.material().jobTitle());
		assertEquals(2, result.material().responsibilities().size());
		assertEquals(1, result.material().qualificationRequirements().size());
		assertTrue(result.material().requiredSkills().isEmpty());
	}

	@Test
	void extractsObjectFromShortExplanatoryPrefix() {
		var result = normalizer.parse("结果如下： {\"responsibilities\":[\"负责研发\"],"
				+ "\"qualificationRequirements\":[\"熟悉 Java\"]}");

		assertTrue(result.valid(), result.errors().toString());
	}

	@Test
	void reportsMissingCoreFields() {
		var result = normalizer.parse("{\"jobTitle\":\"岗位\"}");

		assertTrue(result.errors().stream().anyMatch(value -> value.contains("responsibilities")));
		assertTrue(result.errors().stream().anyMatch(value -> value.contains("qualificationRequirements")));
		assertTrue(!result.valid());
	}

	@Test
	void acceptsNestedMaterialAndNormalizesAllSupportedAliases() {
		var result = normalizer.parse("""
				{"material":{"position":"Engineer","duties":["- Build", "build", "* Test"],
				"requirements":"Java; SQL","skills":["Java"],"additional_information":" remote ",
				"educations":"本科\\n硕士","work_experience":["2 years"],"projects":["Demo"],
				"abilities":["Communicate"],"experience_clues":["API"],"summary":"Done"}}
				""");

		assertTrue(result.valid(), result.errors().toString());
		assertEquals("Engineer", result.material().jobTitle());
		assertEquals(java.util.List.of("Build", "Test"), result.material().responsibilities());
		assertEquals(java.util.List.of("Java", "SQL"), result.material().qualificationRequirements());
		assertEquals("remote", result.material().otherJobInformation());
		assertEquals("Done", result.material().finalText());
	}

	@Test
	void reportsTypeErrorsAndKeepsOptionalInvalidValuesNull() {
		var result = normalizer.parse("""
				{"jobTitle":123,"responsibilities":{},"qualificationRequirements":[1,"valid"],
				"requiredSkills":{},"finalText":false}
				""");

		assertTrue(result.errors().stream().anyMatch(error -> error.contains("jobTitle must be a string")));
		assertTrue(result.errors().stream().anyMatch(error -> error.contains("responsibilities must be an array or string")));
		assertTrue(result.errors().stream().anyMatch(error -> error.contains("requiredSkills must be an array or string")));
		assertTrue(result.errors().stream().anyMatch(error -> error.contains("finalText must be a string")));
		assertEquals(java.util.List.of("valid"), result.material().qualificationRequirements());
	}

	@Test
	void truncatesItemsAndCapsListsWhileDeduplicatingCaseInsensitively() {
		StringBuilder items = new StringBuilder("[");
		for (int index = 0; index < 55; index++) {
			if (index > 0) items.append(',');
			items.append("\"Item").append(index).append("\"");
		}
		items.append(']');
		var result = normalizer.parse("{\"responsibilities\":" + items
				+ ",\"qualificationRequirements\":[\"" + "x".repeat(2100) + "\"]}");

		assertTrue(result.valid());
		assertEquals(50, result.material().responsibilities().size());
		assertEquals(2000, result.material().qualificationRequirements().getFirst().length());
	}

	@Test
	void handlesBlankMalformedAndNonObjectResponsesIncludingFences() {
		assertTrue(normalizer.parse(null).errors().contains("response is blank"));
		assertTrue(normalizer.parse("   ").errors().contains("response is blank"));
		assertTrue(normalizer.parse("not json").errors().contains(
				"response does not contain a valid JSON object"));
		assertTrue(normalizer.parse("[1,2]").errors().contains(
				"response does not contain a valid JSON object"));
		assertTrue(normalizer.parse("```json\nnot json\n```").errors().stream()
				.anyMatch(error -> error.contains("valid JSON object")));
	}

	@Test
	void supportsQuotedBracesAndRejectsDuplicateKeysWithoutLosingValidExtractedObject() {
		var valid = normalizer.parse("prefix {\"responsibilities\":[\"A {brace}\"],"
				+ "\"qualificationRequirements\":[\"B\"]} suffix");
		assertTrue(valid.valid(), valid.errors().toString());

		var duplicate = normalizer.parse("{\"responsibilities\":[\"A\"],"
				+ "\"responsibilities\":[\"B\"],\"qualificationRequirements\":[\"Q\"]}");
		assertTrue(!duplicate.valid());
		assertTrue(duplicate.errors().stream().anyMatch(error -> error.contains("valid JSON object")));
	}
}
