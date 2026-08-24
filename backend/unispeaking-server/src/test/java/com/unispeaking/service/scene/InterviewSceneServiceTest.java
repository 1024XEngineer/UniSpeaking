package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.common.prompt.interview.InterviewPromptBuilder;
import com.unispeaking.component.document.MaterialDesensitizer;
import com.unispeaking.component.document.MaterialTextExtraction;
import com.unispeaking.component.policy.DailyQuotaPolicy;
import com.unispeaking.component.recording.RecordingStore;
import com.unispeaking.component.scene.InterviewMaterialFallbackExtractor;
import com.unispeaking.component.scene.InterviewMaterialResponseNormalizer;
import com.unispeaking.component.statemachine.InterviewTopicStateMachine;
import com.unispeaking.domain.dto.asset.InterviewAssetItem;
import com.unispeaking.domain.dto.scene.InterviewMaterial;
import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.InterviewMaterialDraft;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewSceneRequest;
import com.unispeaking.domain.dto.scene.InterviewSceneResult;
import com.unispeaking.domain.po.evaluation.InterviewReportRecord;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.domain.vo.evaluation.ReportStatus;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiProviderRegistry.RoutedResult;
import com.unispeaking.provider.LlmResponseFormat;
import com.unispeaking.provider.OcrProvider;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.InterviewSceneService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InterviewSceneServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AuthService authService = mock(AuthService.class);
	private final InterviewSceneRepository repository =
			mock(InterviewSceneRepository.class);
	private final AiProviderRegistry providerRegistry =
			mock(AiProviderRegistry.class);
	private final MaterialTextExtraction materialTextExtraction =
			mock(MaterialTextExtraction.class);
	private final MaterialDesensitizer materialDesensitizer =
			mock(MaterialDesensitizer.class);
	private final DailyQuotaPolicy dailyQuotaPolicy = mock(DailyQuotaPolicy.class);
	private final InterviewTopicStateMachine stateMachine =
			mock(InterviewTopicStateMachine.class);
	private final PracticeSessionRepository practiceSessionRepository =
			mock(PracticeSessionRepository.class);
	private final RecordingStore interviewRecordingStore = mock(RecordingStore.class);
	private final InterviewReportRepository interviewReportRepository =
			mock(InterviewReportRepository.class);
	private final OcrProvider ocrProvider = mock(OcrProvider.class);
	private final InterviewMaterialResponseNormalizer materialResponseNormalizer =
			new InterviewMaterialResponseNormalizer(objectMapper);
	private final InterviewMaterialFallbackExtractor materialFallbackExtractor =
			new InterviewMaterialFallbackExtractor();
	private final InterviewSceneService service = new InterviewSceneService(
			authService,
			repository,
			new InterviewPromptBuilder(),
			providerRegistry,
			materialTextExtraction,
			materialDesensitizer,
			dailyQuotaPolicy,
			stateMachine,
			practiceSessionRepository,
			interviewRecordingStore,
			interviewReportRepository,
			ocrProvider,
			objectMapper,
			materialResponseNormalizer,
			materialFallbackExtractor);

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

	@Test
	void rejectsNullRequestBeforeCallingLlm() {
		when(authService.requireUserId(null)).thenReturn("user-1");

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.generate(null));

		assertEquals(InterviewErrorCode.INTERVIEW_MATERIAL_INVALID, exception.code());
		verify(providerRegistry, never()).executeLlmTaskRouted(anyString(), isNull());
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsMaterialWithoutFinalTextOrQualifications() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewMaterial missingFinalText = new InterviewMaterial(
				"后端开发工程师", List.of("负责服务开发"), List.of("掌握 Java"),
				null, null, null, null, null, null, null, " ");
		BusinessException finalTextError = assertThrows(
				BusinessException.class,
				() -> service.generate(request(missingFinalText, InterviewDifficulty.EASY)));
		assertEquals(InterviewErrorCode.INTERVIEW_MATERIAL_INVALID, finalTextError.code());

		InterviewMaterial missingQualifications = new InterviewMaterial(
				"后端开发工程师", List.of("负责服务开发"), List.of(),
				null, null, null, null, null, null, null, "后端开发工程师");
		BusinessException qualificationsError = assertThrows(
				BusinessException.class,
				() -> service.generate(request(missingQualifications, InterviewDifficulty.EASY)));
		assertEquals(InterviewErrorCode.INTERVIEW_MATERIAL_INVALID, qualificationsError.code());
	}

	@Test
	void acceptsMinimumAndMaximumTopicCountsAndSupportedSelfIntroductionNames() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(completed(contextWithTopics(
						"Tell me about yourself", "项目经历", "技术栈", "职业规划")))
				.thenReturn(completed(contextWithTopics(
						"Self-intro", "项目经历", "技术栈", "职业规划", "反问环节")));

		service.generate(request(material(), InterviewDifficulty.STANDARD));
		service.generate(request(material(), InterviewDifficulty.STANDARD));

		verify(providerRegistry, times(2)).executeLlmTaskRouted(anyString(), isNull());
		verify(repository, times(2)).save(any(InterviewSceneDefinition.class));
	}

	@Test
	void rejectsContextJsonForMalformedFenceDuplicateKeysTrailingTokensAndInvalidTopics() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		String[] invalidResponses = {
				"```json\n" + validContext() + "\n``` trailing",
				"{\"candidate_overview\":\"a\",\"candidate_overview\":\"b\","
						+ "\"role_overview\":\"role\",\"interview_topics\":[\"自我介绍\",\"项目\",\"技术\",\"规划\"]}",
				validContext() + " trailing",
				contextWithTopics("项目经历", "技术栈", "职业规划", "反问环节"),
				contextWithTopics("自我介绍", "重复", "重复", "职业规划"),
				contextWithTopics("自我介绍", "项目经历", "技术栈")
		};

		for (String invalid : invalidResponses) {
			when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
					.thenReturn(completed(invalid), completed(invalid));

			BusinessException exception = assertThrows(
					BusinessException.class,
					() -> service.generate(request(material(), InterviewDifficulty.EASY)));
			assertEquals(
					InterviewErrorCode.INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID,
					exception.code());
		}

		verify(repository, never()).save(any());
	}

	@Test
	void rejectsContextTextThatIsBlankWrongTypeOrTooLong() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		String tooLong = "x".repeat(2001);
		String[] invalidResponses = {
				"null",
				"[]",
				"{\"candidate_overview\":\"\",\"role_overview\":\"role\","
						+ "\"interview_topics\":[\"自我介绍\",\"项目\",\"技术\",\"规划\"]}",
				"{\"candidate_overview\":\"" + tooLong + "\",\"role_overview\":\"role\","
						+ "\"interview_topics\":[\"自我介绍\",\"项目\",\"技术\",\"规划\"]}"
		};

		for (String invalid : invalidResponses) {
			when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
					.thenReturn(completed(invalid), completed(invalid));
			assertThrows(BusinessException.class,
					() -> service.generate(request(material(), InterviewDifficulty.EASY)));
		}
		verify(repository, never()).save(any());
	}

	@Test
	void parsesStoredTopicsByFilteringBlankEntriesAndRejectsMalformedJson() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewSceneDefinition definition = definitionWithContext(
				"interview_1", "user-1",
				"{\"interviewTopics\":[\" 自我介绍 \",\"\",42,null,\"项目经历\"]}");
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(repository.findOwnedById("interview_1", "user-1"))
				.thenReturn(Optional.of(definition));

		assertEquals(List.of("自我介绍", "项目经历"), service.interviewTopics("interview_1"));

		InterviewSceneDefinition malformed = definitionWithContext(
				"interview_bad", "user-1", "{not-json");
		when(repository.findById("interview_bad")).thenReturn(Optional.of(malformed));
		when(repository.findOwnedById("interview_bad", "user-1"))
				.thenReturn(Optional.of(malformed));
		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.interviewTopics("interview_bad"));
		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, exception.code());
	}

	@Test
	void propagatesQuotaAndPersistenceFailuresWithoutCallingUnrelatedDependencies() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		org.mockito.Mockito.doThrow(new BusinessException("QUOTA", "quota"))
				.when(dailyQuotaPolicy)
				.assertWithinQuota("user-1", SceneType.INTERVIEW_SCENE, 5);
		BusinessException quotaException = assertThrows(BusinessException.class,
				() -> service.generate(request(material(), InterviewDifficulty.EASY)));
		assertEquals("QUOTA", quotaException.code());
		verify(providerRegistry, never()).executeLlmTaskRouted(anyString(), isNull());

		org.mockito.Mockito.reset(dailyQuotaPolicy);
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(completed(validContext()));
		org.mockito.Mockito.doThrow(new IllegalStateException("database down"))
				.when(repository).save(any(InterviewSceneDefinition.class));
		assertThrows(IllegalStateException.class,
				() -> service.generate(request(material(), InterviewDifficulty.EASY)));
	}

	@Test
	void prepareMaterialsStructuresDesensitizedTextIntoMaterial() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(materialTextExtraction.extract(any()))
				.thenReturn(new MaterialTextExtraction.MaterialTextResult(
						"JD 原始文本", "简历原始文本", false));
		when(materialDesensitizer.desensitize("JD 原始文本"))
				.thenReturn("脱敏后 JD");
		when(materialDesensitizer.desensitize("简历原始文本"))
				.thenReturn("脱敏后简历");
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT)))
				.thenReturn(completed(validMaterial()));

		InterviewMaterialDraft draft = service.prepareMaterials(
				new InterviewMaterialPreparationInput(
						"简历原始文本", null, "JD 原始文本", null));

		InterviewMaterial material = draft.material();
		assertNotNull(material);
		assertEquals("后端开发工程师", material.jobTitle());
		assertEquals(List.of("负责支付系统设计"), material.responsibilities());
		assertEquals(List.of("掌握 Java"), material.qualificationRequirements());
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(providerRegistry).executeLlmTaskRouted(promptCaptor.capture(), isNull(), eq(LlmResponseFormat.JSON_OBJECT));
		String prompt = promptCaptor.getValue();
		assertTrue(prompt.contains("脱敏后 JD"));
		assertTrue(prompt.contains("脱敏后简历"));
		assertFalse(prompt.contains("JD 原始文本"));
		assertFalse(prompt.contains("简历原始文本"));
	}

	@Test
	void prepareMaterialsWithoutResumePassesNoResumeToLlm() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(materialTextExtraction.extract(any()))
				.thenReturn(new MaterialTextExtraction.MaterialTextResult(
						"JD 原始文本", null, true));
		when(materialDesensitizer.desensitize("JD 原始文本"))
				.thenReturn("脱敏后 JD");
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT)))
				.thenReturn(completed(validMaterial()));

		service.prepareMaterials(
				new InterviewMaterialPreparationInput(null, null, "JD 原始文本", null));

		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(providerRegistry).executeLlmTaskRouted(promptCaptor.capture(), isNull(), eq(LlmResponseFormat.JSON_OBJECT));
		assertTrue(promptCaptor.getValue().contains("No resume was provided."));
	}

	@Test
	void prepareMaterialsRetriesWhenFirstLlmMaterialResponseIsInvalid() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(materialTextExtraction.extract(any()))
				.thenReturn(new MaterialTextExtraction.MaterialTextResult(
						"JD 文本", "简历文本", false));
		when(materialDesensitizer.desensitize(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT)))
				.thenReturn(completed(invalidMaterial()), completed(validMaterial()));

		InterviewMaterialDraft draft = service.prepareMaterials(
				new InterviewMaterialPreparationInput(null, null, "JD 文本", null));

		assertEquals("后端开发工程师", draft.material().jobTitle());
		verify(providerRegistry, times(2)).executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT));
	}

	@Test
	void prepareMaterialsFailsWhenAllLlmMaterialResponsesAreInvalid() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(materialTextExtraction.extract(any()))
				.thenReturn(new MaterialTextExtraction.MaterialTextResult(
						"JD 文本", "简历文本", false));
		when(materialDesensitizer.desensitize(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT)))
				.thenReturn(completed(invalidMaterial()), completed(invalidMaterial()));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.prepareMaterials(
						new InterviewMaterialPreparationInput(null, null, "JD 文本", null)));

		assertEquals(InterviewErrorCode.INTERVIEW_MATERIAL_SOURCE_INSUFFICIENT, exception.code());
		verify(providerRegistry, times(2)).executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT));
	}

	@Test
	void preparesMaterialFromFallbackWhenLlmResponsesAreInvalid() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(materialTextExtraction.extract(any()))
				.thenReturn(new MaterialTextExtraction.MaterialTextResult(
						"职位：后端工程师\n岗位职责：\n负责服务开发\n任职要求：\n熟悉 Java",
						null,
						true));
		when(materialDesensitizer.desensitize(anyString()))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(providerRegistry.executeLlmTaskRouted(anyString(), isNull(), eq(LlmResponseFormat.JSON_OBJECT)))
				.thenReturn(completed(invalidMaterial()), completed(invalidMaterial()));

		InterviewMaterial material = service.prepareMaterials(
				new InterviewMaterialPreparationInput(null, null, "JD", null)).material();

		assertEquals("后端工程师", material.jobTitle());
		assertEquals("后端工程师 · 负责服务开发 · 熟悉 Java", material.finalText());
	}

	@Test
	void prepareDialogueReturnsOwnedSceneContext() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewSceneDefinition definition = definition("interview_1", "user-1");
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(repository.findOwnedById("interview_1", "user-1"))
				.thenReturn(Optional.of(definition));

		InterviewDialogueSceneContext context =
				service.prepareDialogue("interview_1");

		assertEquals("user-1", context.userId());
		assertEquals("interview_1", context.sceneId());
		assertEquals("面试系统提示词", context.scenePrompt());
		assertEquals(InterviewDifficulty.HARD, context.difficulty());
	}

	@Test
	void prepareDialogueRejectsMissingScene() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(repository.findById("interview_1")).thenReturn(Optional.empty());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.prepareDialogue("interview_1"));

		assertEquals(
				InterviewErrorCode.INTERVIEW_SCENE_NOT_FOUND,
				exception.code());
	}

	@Test
	void prepareDialogueRejectsSceneOwnedByAnotherUser() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewSceneDefinition definition = definition("interview_1", "user-2");
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(repository.findOwnedById("interview_1", "user-1"))
				.thenReturn(Optional.empty());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.prepareDialogue("interview_1"));

		assertEquals(
				InterviewErrorCode.INTERVIEW_SCENE_ACCESS_DENIED,
				exception.code());
	}

	@Test
	void listOwnedScenesBuildsAssetItemsFromScenesAndLatestReport() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		InterviewSceneDefinition scene = new InterviewSceneDefinition(
				"interview_1",
				"user-1",
				"{\"jobTitle\":\"后端开发工程师\"}",
				"final text",
				"{}",
				InterviewDifficulty.HARD,
				"prompt",
				now,
				now,
				null);
		when(repository.findByUserId("user-1")).thenReturn(List.of(scene));
		when(interviewReportRepository.findBySceneId("interview_1"))
				.thenReturn(List.of(completedReport("session-1", now)));

		List<InterviewAssetItem> items = service.listOwnedScenes();

		InterviewAssetItem item = items.getFirst();
		assertEquals("interview_1", item.sceneId());
		assertEquals("后端开发工程师", item.jobTitle());
		assertEquals("HARD", item.difficulty());
		assertEquals("session-1", item.latestSessionId());
		assertEquals("COMPLETED", item.latestReportStatus());
		assertEquals(new BigDecimal("85.0"), item.latestOverallScore());
		assertEquals(now, item.latestPracticedAt());
		assertEquals(1, item.practiceCount());
		assertEquals(now, item.createdAt());
	}

	@Test
	void listOwnedScenesWithNoReportsYieldsNullLatestFields() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(repository.findByUserId("user-1"))
				.thenReturn(List.of(definition("interview_1", "user-1")));
		when(interviewReportRepository.findBySceneId("interview_1"))
				.thenReturn(List.of());

		List<InterviewAssetItem> items = service.listOwnedScenes();

		InterviewAssetItem item = items.getFirst();
		assertEquals("interview_1", item.sceneId());
		assertNull(item.jobTitle());
		assertNull(item.latestSessionId());
		assertNull(item.latestReportStatus());
		assertNull(item.latestOverallScore());
		assertNull(item.latestPracticedAt());
		assertEquals(0, item.practiceCount());
	}

	@Test
	void advanceTopicStateInitializesFromPersistedTopicsBeforeAdvancing() {
		InterviewSceneDefinition definition = definitionWithContext(
				"interview_1", "user-1", "{\"interviewTopics\":[\"自我介绍\",\"项目经历\"]}");
		InterviewTopicState expected = new InterviewTopicState(
				"自我介绍", 0, 1, 0, 0, false, false, "继续追问");
		when(stateMachine.current("session-1")).thenReturn(null);
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(stateMachine.advance("session-1", 1,
				new InterviewTopicEvent("自我介绍", false))).thenReturn(expected);

		InterviewTopicState actual = service.advanceTopicState(
				"interview_1", "session-1", 1,
				new InterviewTopicEvent("自我介绍", false));

		assertEquals(expected, actual);
		verify(stateMachine).start(
				"session-1", List.of("自我介绍", "项目经历"), InterviewDifficulty.HARD);
		verify(stateMachine).advance("session-1", 1,
				new InterviewTopicEvent("自我介绍", false));
	}

	@Test
	void advanceTopicStateRejectsMissingSceneBeforeStartingStateMachine() {
		when(stateMachine.current("session-1")).thenReturn(null);
		when(repository.findById("missing")).thenReturn(Optional.empty());

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.advanceTopicState(
						"missing", "session-1", 1, InterviewTopicEvent.unknown()));

		assertEquals(InterviewErrorCode.INTERVIEW_SCENE_NOT_FOUND, exception.code());
		verify(stateMachine, never()).start(anyString(), any(), any());
		verify(stateMachine, never()).advance(
				anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
	}

	@Test
	void interviewTopicsRejectsStoredContextWithoutUsableTopics() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewSceneDefinition definition = definitionWithContext(
				"interview_1", "user-1", "{\"interviewTopics\":[]}");
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(repository.findOwnedById("interview_1", "user-1"))
				.thenReturn(Optional.of(definition));

		BusinessException exception = assertThrows(BusinessException.class,
				() -> service.interviewTopics("interview_1"));

		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, exception.code());
	}

	@Test
	void deleteSceneCleansUpEveryAssociatedRecordingAfterSoftDelete() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewSceneDefinition definition = definition("interview_1", "user-1");
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(repository.findOwnedById("interview_1", "user-1"))
				.thenReturn(Optional.of(definition));
		when(practiceSessionRepository.findBySceneId("interview_1")).thenReturn(List.of(
				new com.unispeaking.domain.po.session.PracticeSessionRecord(
						"session-1", UUID.randomUUID(), "interview_1",
						SceneType.INTERVIEW_SCENE, SessionStatus.COMPLETED,
						java.time.Instant.EPOCH, java.time.Instant.EPOCH),
				new com.unispeaking.domain.po.session.PracticeSessionRecord(
						"session-2", UUID.randomUUID(), "interview_1",
						SceneType.INTERVIEW_SCENE, SessionStatus.COMPLETED,
						java.time.Instant.EPOCH, java.time.Instant.EPOCH)));

		service.deleteScene("interview_1");

		verify(repository).softDelete("interview_1", "user-1");
		verify(interviewRecordingStore).deleteSessionAudio("session-1");
		verify(interviewRecordingStore).deleteSessionAudio("session-2");
	}

	@Test
	void isOcrAvailableDelegatesToOcrProvider() {
		when(ocrProvider.available()).thenReturn(true);
		assertTrue(service.isOcrAvailable());

		when(ocrProvider.available()).thenReturn(false);
		assertFalse(service.isOcrAvailable());
	}

	@Test
	void prepareDialogueAndTopicQueriesRejectMissingOrUnauthorizedScenes() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(repository.findById("missing")).thenReturn(Optional.empty());
		BusinessException missing = assertThrows(BusinessException.class,
				() -> service.prepareDialogue("missing"));
		assertEquals(InterviewErrorCode.INTERVIEW_SCENE_NOT_FOUND, missing.code());

		InterviewSceneDefinition definition = definition("interview_1", "owner");
		when(repository.findById("interview_1")).thenReturn(Optional.of(definition));
		when(repository.findOwnedById("interview_1", "user-1")).thenReturn(Optional.empty());
		BusinessException denied = assertThrows(BusinessException.class,
				() -> service.interviewTopics("interview_1"));
		assertEquals(InterviewErrorCode.INTERVIEW_SCENE_ACCESS_DENIED, denied.code());
	}

	@Test
	void listOwnedScenesNormalizesMalformedAndBlankJobTitlesAndNullReportStatus() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		InterviewSceneDefinition invalidJson = definitionWithContext("bad", "user-1", "{}");
		InterviewSceneDefinition malformedJson = new InterviewSceneDefinition(
				"malformed", "user-1", "not-json", "final", "{}", null,
				"prompt", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), null);
		InterviewReportRecord nullStatus = new InterviewReportRecord(
				"session-null", "bad", "user-1", null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, 0, null, null, null);
		when(repository.findByUserId("user-1")).thenReturn(List.of(invalidJson, malformedJson));
		when(interviewReportRepository.findBySceneId("bad")).thenReturn(List.of(nullStatus));
		when(interviewReportRepository.findBySceneId("malformed")).thenReturn(List.of());

		List<InterviewAssetItem> items = service.listOwnedScenes();

		assertEquals(2, items.size());
		assertNull(items.getFirst().jobTitle());
		assertNull(items.getFirst().latestReportStatus());
		assertEquals("session-null", items.getFirst().latestSessionId());
		assertNull(items.get(1).jobTitle());
		assertNull(items.get(1).difficulty());
	}

	@Test
	void advanceTopicStateUsesExistingStateWithoutReloadingScene() {
		InterviewTopicState current = new InterviewTopicState(
				"项目经历", 1, 1, 0, 0, false, false, "继续");
		InterviewTopicEvent event = new InterviewTopicEvent("项目经历", true);
		when(stateMachine.current("session-1")).thenReturn(current);
		when(stateMachine.advance("session-1", 2, event)).thenReturn(current);

		assertEquals(current, service.advanceTopicState("interview_1", "session-1", 2, event));
		verify(repository, never()).findById(anyString());
		verify(stateMachine, never()).start(anyString(), any(), any());
	}

	@Test
	void prepareMaterialsPropagatesExtractionAndDesensitizerFailures() {
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(materialTextExtraction.extract(any()))
				.thenThrow(new IllegalArgumentException("unsupported file"));
		assertThrows(IllegalArgumentException.class,
				() -> service.prepareMaterials(new InterviewMaterialPreparationInput("resume", null, "jd", null)));

		org.mockito.Mockito.reset(materialTextExtraction);
		when(materialTextExtraction.extract(any()))
				.thenReturn(new MaterialTextExtraction.MaterialTextResult("jd", "resume", false));
		when(materialDesensitizer.desensitize("jd"))
				.thenThrow(new IllegalStateException("desensitizer down"));
		assertThrows(IllegalStateException.class,
				() -> service.prepareMaterials(new InterviewMaterialPreparationInput("resume", null, "jd", null)));
	}

	private InterviewReportRecord completedReport(
			String sessionId,
			OffsetDateTime now) {
		return new InterviewReportRecord(
				sessionId,
				"interview_1",
				"user-1",
				ReportStatus.COMPLETED,
				new BigDecimal("85.0"),
				"整体表现良好。",
				null, null, null,
				null, null, null,
				new BigDecimal("80.0"), null, null,
				new BigDecimal("70.0"), null, null,
				new BigDecimal("90.0"), null, null,
				0,
				null,
				now,
				now);
	}

	private InterviewSceneRequest request(
			InterviewMaterial material,
			InterviewDifficulty difficulty) {
		return new InterviewSceneRequest(material, difficulty);
	}

	private InterviewSceneDefinition definition(String sceneId, String userId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return new InterviewSceneDefinition(
				sceneId,
				userId,
				"{}",
				"final text",
				"{}",
				InterviewDifficulty.HARD,
				"面试系统提示词",
				now,
				now,
				null);
	}

	private InterviewSceneDefinition definitionWithContext(
			String sceneId,
			String userId,
			String contextJson) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		return new InterviewSceneDefinition(
				sceneId, userId, "{}", "final text", contextJson,
				InterviewDifficulty.HARD, "面试系统提示词", now, now, null);
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
		return contextWithTopics("自我介绍", "项目经历", "技术栈", "职业规划");
	}

	private String contextWithTopics(String... topics) {
		return "{\"candidate_overview\":\"候选人有三年后端开发经验，熟悉支付系统。\","
				+ "\"role_overview\":\"负责支付系统的设计与性能优化。\",\"interview_topics\":"
				+ objectMapper.writeValueAsString(java.util.Arrays.asList(topics)) + "}";
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

	private String validMaterial() {
		return """
				{
				  "jobTitle": "后端开发工程师",
				  "responsibilities": ["负责支付系统设计"],
				  "qualificationRequirements": ["掌握 Java"],
				  "requiredSkills": ["Java", "Spring"],
				  "otherJobInformation": "团队氛围好",
				  "education": ["计算机本科"],
				  "workExperiences": ["某公司后端工程师"],
				  "projectExperiences": ["支付系统重构项目"],
				  "skillsAndAbilities": ["团队协作"],
				  "interviewableExperienceClues": ["候选人提到过支付网关"],
				  "finalText": "后端开发工程师 · 负责支付系统"
				}
				""";
	}

	private String invalidMaterial() {
		return """
				{
				  "jobTitle": "后端开发工程师",
				  "qualificationRequirements": ["掌握 Java"],
				  "finalText": "后端开发工程师"
				}
				""";
	}
}
