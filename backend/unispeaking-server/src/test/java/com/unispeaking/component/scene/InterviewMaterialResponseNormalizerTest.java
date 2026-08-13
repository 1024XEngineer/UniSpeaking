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
}
