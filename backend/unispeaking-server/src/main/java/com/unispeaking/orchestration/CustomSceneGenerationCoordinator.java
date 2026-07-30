package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.SceneRepository;
import com.unispeaking.service.scene.SceneService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomSceneGenerationCoordinator {

	private static final int DEFAULT_ESTIMATED_MINUTES = 10;

	private final SceneService sceneService;
	private final SceneRepository sceneRepository;
	private final ObjectMapper objectMapper;

	public CustomSceneGenerationCoordinator(
			SceneService sceneService,
			SceneRepository sceneRepository,
			ObjectMapper objectMapper) {
		this.sceneService = sceneService;
		this.sceneRepository = sceneRepository;
		this.objectMapper = objectMapper;
	}

	public CustomSceneGenerationResponse generate(SceneGenerationRequest request) {
		SceneGenerationResponse generated = sceneService.generateScene(request);
		CustomSceneDefinition definition = sceneRepository
				.findCustomDefinitionById(generated.sceneId())
				.orElseThrow(() -> new BusinessException(
						"CUSTOM_SCENE_NOT_FOUND",
						"生成的自定义场景不存在"));
		return new CustomSceneGenerationResponse(
				generated.sceneId(),
				definition.title(),
				definition.background(),
				definition.aiRole(),
				definition.userRole(),
				definition.learningGoal(),
				estimatedMinutes(definition.successFactorJson()),
				generated.wordList(),
				generated.phraseList(),
				generated.sentenceList(),
				generated.scenePrompt());
	}

	private int estimatedMinutes(String successFactorJson) {
		try {
			JsonNode root = objectMapper.readTree(successFactorJson);
			int value = root.path("estimated_minutes").intValue();
			return value >= 5 && value <= 30 ? value : DEFAULT_ESTIMATED_MINUTES;
		}
		catch (RuntimeException exception) {
			return DEFAULT_ESTIMATED_MINUTES;
		}
	}
}
