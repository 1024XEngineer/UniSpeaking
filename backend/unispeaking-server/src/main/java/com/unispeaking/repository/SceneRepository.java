package com.unispeaking.repository;

import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import java.util.Optional;

public interface SceneRepository {
	Optional<SceneConfig> findByType(SceneType type);
	SceneGenerationResponse saveGenerated(SceneGenerationResponse scene);
	Optional<SceneGenerationResponse> findGeneratedById(String sceneId);
}
