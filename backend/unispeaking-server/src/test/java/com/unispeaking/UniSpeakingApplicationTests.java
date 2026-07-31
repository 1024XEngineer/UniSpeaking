package com.unispeaking;

import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.service.evaluation.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UniSpeakingApplicationTests {

	@MockitoBean
	private EvaluationService evaluationService;

	@MockitoBean
	private SceneRepository sceneRepository;

	@Test
	void contextLoads() {
	}

}
