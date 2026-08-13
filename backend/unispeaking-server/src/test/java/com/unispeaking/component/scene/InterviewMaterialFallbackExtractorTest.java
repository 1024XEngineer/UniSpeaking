package com.unispeaking.component.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class InterviewMaterialFallbackExtractorTest {

	private final InterviewMaterialFallbackExtractor extractor =
			new InterviewMaterialFallbackExtractor();

	@Test
	void extractsCoreSectionsFromJobDescription() {
		var material = extractor.extract("""
				职位：后端工程师
				岗位职责：
				负责支付系统设计
				推进接口性能优化
				任职要求：
				熟悉 Java 和 Spring
				本科以上学历
				""", "", false);

		assertNotNull(material);
		assertEquals("后端工程师", material.jobTitle());
		assertEquals(2, material.responsibilities().size());
		assertEquals(2, material.qualificationRequirements().size());
	}

	@Test
	void classifiesUnheadedCoreLines() {
		var material = extractor.extract("负责客户沟通\n要求具备英文能力", null, true);

		assertNotNull(material);
		assertEquals(1, material.responsibilities().size());
		assertEquals(1, material.qualificationRequirements().size());
	}
}
