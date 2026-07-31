package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import com.unispeaking.common.prompt.FiveLayerPromptBuilder;
import com.unispeaking.service.scene.impl.SceneServiceImpl;
import com.unispeaking.service.scene.impl.CustomSceneGenerator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SceneServiceImplTest {

	@Test
	void customSceneUsesLlmDefinitionAndPersistentRepositoryBranch() {
		String userId = "11111111-1111-4111-8111-111111111111";
		AuthService authService = mock(AuthService.class);
		ProfileService profileService = mock(ProfileService.class);
		SceneRepository repository = mock(SceneRepository.class);
		FiveLayerPromptBuilder promptService = mock(FiveLayerPromptBuilder.class);
		CustomSceneGenerator generator = mock(CustomSceneGenerator.class);
		UserProfile profile = new UserProfile(
				userId,
				"B",
				"Katerina",
				"NATURAL",
				"zh-CN",
				"喜欢旅行");
		SceneConfig config = new SceneConfig(
				SceneType.CUSTOM_SCENE,
				ProviderType.QWEN,
				null,
				"Katerina",
				true);
		List<LearningContentItem> words = items("word", 5);
		List<LearningContentItem> phrases = items("phrase", 5);
		List<LearningContentItem> sentences = items("sentence", 3);
		CustomSceneDefinition definition = new CustomSceneDefinition(
				"custom_generated",
				userId,
				"酒店办理入住",
				"酒店前台",
				"前台接待员",
				"住客",
				"完成入住",
				"保持礼貌",
				"{\"minimum_user_turns\":5}",
				words,
				phrases,
				sentences);

		when(authService.requireUserId(userId)).thenReturn(userId);
		when(profileService.getProfile(userId)).thenReturn(profile);
		when(repository.findByType(SceneType.CUSTOM_SCENE))
				.thenReturn(Optional.of(config));
		when(generator.generate(
				any(String.class),
				eq(userId),
				eq("酒店办理入住"),
				eq("偏好"),
				same(profile)))
				.thenAnswer(invocation -> new CustomSceneDefinition(
						invocation.getArgument(0),
						definition.userId(),
						definition.title(),
						definition.background(),
						definition.aiRole(),
						definition.userRole(),
						definition.learningGoal(),
						definition.customInstruction(),
						definition.successFactorJson(),
						definition.wordList(),
						definition.phraseList(),
						definition.sentenceList()));
		when(promptService.compose(
				same(profile),
				same(config),
				eq(SceneType.CUSTOM_SCENE),
				eq("酒店办理入住"),
				eq("偏好"),
				anyList(),
				anyList(),
				anyList(),
				any(CustomSceneDefinition.class)))
				.thenReturn(List.of("layer one", "layer two"));
		when(repository.saveCustomScene(
				any(CustomSceneDefinition.class),
				any(SceneGenerationResponse.class)))
				.thenAnswer(invocation -> invocation.getArgument(1));
		var service = new SceneServiceImpl(
				authService,
				profileService,
					repository,
					promptService,
					generator,
					mock(AiProviderRegistry.class),
					new ObjectMapper());

		SceneGenerationResponse response = service.generateScene(
				new SceneGenerationRequest(
						userId,
						"偏好",
						SceneType.CUSTOM_SCENE,
						"酒店办理入住"));

		assertEquals(5, response.wordList().size());
		assertEquals(5, response.phraseList().size());
		assertEquals(3, response.sentenceList().size());
		assertEquals("layer one\n\nlayer two", response.scenePrompt());
		verify(repository).saveCustomScene(
				any(CustomSceneDefinition.class),
				same(response));
	}

	private List<LearningContentItem> items(String prefix, int count) {
		return java.util.stream.IntStream.range(0, count)
				.mapToObj(index -> new LearningContentItem(
						prefix + "_" + index,
						prefix + index,
						"翻译" + index,
						"/" + prefix + index + "/"))
				.toList();
	}
}
