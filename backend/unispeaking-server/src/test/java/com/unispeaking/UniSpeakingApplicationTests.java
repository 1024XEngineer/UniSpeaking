package com.unispeaking;

import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UniSpeakingApplicationTests {

	@MockitoBean
	private EvaluationProcessor evaluationProcessor;

	@MockitoBean
	private SceneRepository sceneRepository;

	@MockitoBean
	private InterviewSceneRepository interviewSceneRepository;

	@Test
	void contextLoads() {
	}

}
