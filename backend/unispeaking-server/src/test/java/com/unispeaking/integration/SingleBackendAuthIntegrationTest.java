package com.unispeaking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import com.unispeaking.component.evaluation.EvaluationProcessor;
import com.unispeaking.infrastructure.persistence.repository.evaluation.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;

/**
 * Contract for the unified backend: the legacy JWT controller and the real
 * email-session/admin controllers must coexist in one Spring context.
 */
@SpringBootTest
@ActiveProfiles("test")
class SingleBackendAuthIntegrationTest {

    @MockitoBean
    private EvaluationProcessor evaluationProcessor;

    @MockitoBean
    private SceneRepository sceneRepository;

    @MockitoBean
    private InterviewSceneRepository interviewSceneRepository;

    @MockitoBean
    private InterviewReportRepository interviewReportRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void exposesUserEmailSessionAndAdminAuthenticationControllersTogether() {
        assertThat(applicationContext.containsBean("authController")).isTrue();
        assertThat(applicationContext.containsBean("userAuthController")).isTrue();
        assertThat(applicationContext.containsBean("adminAuthController")).isTrue();
    }
}
