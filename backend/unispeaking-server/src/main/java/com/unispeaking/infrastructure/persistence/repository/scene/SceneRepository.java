package com.unispeaking.infrastructure.persistence.repository.scene;

import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.SceneAssetSnapshot;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import java.util.List;
import java.util.Optional;

public interface SceneRepository {
	Optional<SceneConfig> findByType(SceneType type);
	SceneGenerationResponse saveCustomScene(
			CustomSceneDefinition definition,
			SceneGenerationResponse response);
	Optional<SceneGenerationResponse> findGeneratedById(String sceneId);
	Optional<CustomSceneDefinition> findCustomDefinitionById(String sceneId);
	List<SceneAssetSnapshot> findAssetsByUserId(String userId);
	boolean softDelete(String sceneId, String userId);
	long countActiveByUserId(String userId);
	long countAllByUserId(String userId);
	List<String> findAllIdsByUserId(String userId);
}
