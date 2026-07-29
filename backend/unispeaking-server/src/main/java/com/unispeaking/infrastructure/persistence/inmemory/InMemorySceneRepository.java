package com.unispeaking.infrastructure.persistence.inmemory;

import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.repository.SceneRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySceneRepository implements SceneRepository {

	private final Map<String, SceneGenerationResponse> generatedScenes =
			new ConcurrentHashMap<>();

	@Override
	public Optional<SceneConfig> findByType(SceneType type) {
		return Optional.of(new SceneConfig(type, ProviderType.QWEN, null, "Katerina", true));
	}

	@Override
	public SceneGenerationResponse saveGenerated(SceneGenerationResponse scene) {
		generatedScenes.put(scene.sceneId(), scene);
		return scene;
	}

	@Override
	public Optional<SceneGenerationResponse> findGeneratedById(String sceneId) {
		return Optional.ofNullable(generatedScenes.get(sceneId));
	}
}
