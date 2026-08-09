package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.common.prompt.interview.InterviewPromptBuilder;
import com.unispeaking.domain.dto.scene.InterviewMaterial;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiProviderRegistry.RoutedResult;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.impl.InterviewSceneServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InterviewSceneServiceImplTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AuthService authService = mock(AuthService.class);
	private final InterviewSceneRepository repository =
			mock(InterviewSceneRepository.class);
	private final AiProviderRegistry providerRegistry =
			mock(AiProviderRegistry.class);
	private final InterviewSceneServiceImpl service = new InterviewSceneServiceImpl(
			authService,
			repository,
			new InterviewPromptBuilder(),
			providerRegistry,
			objectMapper);

	@Test
	void generatePersistsSceneAndReturnsSceneIdAndPrompt() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(completed(validContext()));

		InterviewSceneResult result = service.generate(request(
				material(),
				InterviewDifficulty.STANDARD));

		assertTrue(result.sceneId().startsWith("interview_"));
		assertTrue(result.scenePrompt().contains("自我介绍"));
		assertTrue(result.scenePrompt().contains("STANDARD"));
		ArgumentCaptor<InterviewSceneDefinition> definition =
				ArgumentCaptor.forClass(InterviewSceneDefinition.class);
		verify(repository).save(definition.capture());
		InterviewSceneDefinition saved = definition.getValue();
		assertEquals("user-1", saved.userId());
		assertEquals(InterviewDifficulty.STANDARD, saved.difficulty());
		assertEquals(material().finalText(), saved.finalText());
		JsonNode materialJson = objectMapper.readTree(saved.confirmedMaterialJson());
		assertEquals("后端开发工程师", materialJson.path("jobTitle").asString());
		JsonNode contextJson = objectMapper.readTree(saved.interviewContextJson());
		assertEquals(4, contextJson.path("interviewTopics").size());
		assertEquals(result.sceneId(), saved.sceneId());
	}

	@Test
	void rejectsMaterialWithoutCoreJobFields() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewMaterial invalid = new InterviewMaterial(
				"后端开发工程师",
				List.of(),
				List.of("掌握 Java"),
				null, null, null, null, null, null, null,
				"展示文本");

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.generate(request(invalid, InterviewDifficulty.EASY)));

		assertEquals(InterviewErrorCode.INTERVIEW_MATERIAL_INVALID, exception.code());
		verify(providerRegistry, never()).executeLlmTaskRouted(anyString(), isNull());
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsNullDifficulty() {
		when(authService.requireUserId(null)).thenReturn("user-1");

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.generate(new InterviewSceneRequest(material(), null)));

		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, exception.code());
		verify(repository, never()).save(any());
	}

	@Test
	void retriesWhenFirstLlmResponseIsInvalid() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(completed(invalidContext()), completed(validContext()));

		InterviewSceneResult result = service.generate(request(
				material(),
				InterviewDifficulty.HARD));

		assertTrue(result.sceneId().startsWith("interview_"));
		verify(providerRegistry, times(2)).executeLlmTaskRouted(anyString(), isNull());
		verify(repository).save(any(InterviewSceneDefinition.class));
	}

	@Test
	void failsWhenAllLlmResponsesAreInvalid() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(completed(invalidContext()), completed(invalidContext()));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.generate(request(
						material(),
						InterviewDifficulty.EASY)));

		assertEquals(
				InterviewErrorCode.INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID,
				exception.code());
		verify(repository, never()).save(any());
	}

	private InterviewSceneRequest request(
			InterviewMaterial material,
			InterviewDifficulty difficulty) {
		return new InterviewSceneRequest(material, difficulty);
	}

	private InterviewMaterial material() {
		return new InterviewMaterial(
				"后端开发工程师",
				List.of("负责支付系统设计", "负责接口性能优化"),
				List.of("掌握 Java", "三年以上后端经验"),
				List.of("Java", "Spring"),
				"团队氛围好",
				List.of("计算机本科"),
				List.of("某公司后端工程师"),
				List.of("支付系统重构项目"),
				List.of("团队协作"),
				List.of("候选人提到过支付网关"),
				"后端开发工程师 · 负责支付系统");
	}

	private RoutedResult<String> completed(String content) {
		return new RoutedResult<>(
				"qwen3.5-plus",
				"qwen",
				AiCapability.LLM,
				content);
	}

	private String validContext() {
		return """
				{
				  "candidate_overview": "候选人有三年后端开发经验，熟悉支付系统。",
				  "role_overview": "负责支付系统的设计与性能优化。",
				  "interview_topics": [
				    "自我介绍",
				    "项目经历",
				    "技术栈",
				    "职业规划"
				  ]
				}
				""";
	}

	private String invalidContext() {
		return """
				{
				  "candidate_overview": "候选人有三年后端开发经验。",
				  "role_overview": "负责支付系统。",
				  "interview_topics": ["自我介绍", "项目经历"]
				}
				""";
	}
}
