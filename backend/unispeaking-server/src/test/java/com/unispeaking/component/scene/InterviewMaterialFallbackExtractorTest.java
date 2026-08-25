package com.unispeaking.component.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
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

	@Test
	void returnsNullWhenEitherCoreCategoryCannotBeRecovered() {
		assertNull(extractor.extract(null, null, true));
		assertNull(extractor.extract("负责系统开发", "", false));
		assertNull(extractor.extract("要求熟悉 Java", "", false));
	}

	@Test
	void extractsEnglishHeadingsSkillsMetadataAndResumeSections() {
		var material = extractor.extract("""
				Job title: Platform Engineer
				Responsibilities:
				- Build APIs
				- Build APIs
				Qualifications:
				* Experience with Java
				Skills: Java, Spring；PostgreSQL
				Location: Shanghai
				Benefits: Flexible hours
				""", """
				Education
				BSc Computer Science
				Work Experience
				Backend Engineer
				Projects
				Speaking platform
				""", false);

		assertNotNull(material);
		assertEquals("Platform Engineer", material.jobTitle());
		assertEquals(1, material.responsibilities().size());
		assertEquals(3, material.requiredSkills().size());
		assertEquals("Location: Shanghai；Benefits: Flexible hours", material.otherJobInformation());
		assertEquals(List.of("BSc Computer Science", "Work Experience", "Backend Engineer",
				"Projects", "Speaking platform"), material.education());
		assertEquals(List.of("Backend Engineer", "Projects", "Speaking platform"),
				material.workExperiences());
		assertEquals(List.of("Speaking platform"), material.projectExperiences());
	}

	@Test
	void omitsOptionalTitleSkillsMetadataAndBlankResume() {
		var material = extractor.extract(
				"负责开发服务\r\n要求具备经验", " \n", true);

		assertNotNull(material);
		assertNull(material.jobTitle());
		assertEquals(List.of(), material.requiredSkills());
		assertNull(material.otherJobInformation());
		assertEquals(List.of(), material.education());
	}
}
