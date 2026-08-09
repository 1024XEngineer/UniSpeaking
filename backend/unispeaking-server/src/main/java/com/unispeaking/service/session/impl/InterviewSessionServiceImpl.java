package com.unispeaking.service.session.impl;

import com.unispeaking.component.policy.DailyQuotaPolicy;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.scene.InterviewDialogueSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.InterviewSceneService;
import com.unispeaking.service.session.InterviewSessionService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Interview 会话实现。镜像 {@code CustomSessionServiceImpl.startSession}：
 * prepareDialogue（归属校验 + 读 scenePrompt + userId）→ 配额 → 建会话 → 实时连接 → 响应。
 */
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {

	private static final int DAILY_PRACTICE_LIMIT = 5;
	private static final String SCENE_NAME = "模拟面试";

	private final InterviewSceneService interviewSceneService;
	private final DailyQuotaPolicy dailyQuotaPolicy;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;

	public InterviewSessionServiceImpl(
			InterviewSceneService interviewSceneService,
			DailyQuotaPolicy dailyQuotaPolicy,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator) {
		this.interviewSceneService = interviewSceneService;
		this.dailyQuotaPolicy = dailyQuotaPolicy;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
	}

	@Override
	public StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request) {
		InterviewDialogueSceneContext prepared =
				interviewSceneService.prepareDialogue(sceneId);
		dailyQuotaPolicy.assertWithinQuota(
				prepared.userId(),
				SceneType.INTERVIEW_SCENE,
				DAILY_PRACTICE_LIMIT);
		StartSessionResponse started = sessionLifecycle.startSession(
				new StartSessionCommand(
						prepared.userId(),
						prepared.sceneId(),
						SceneType.INTERVIEW_SCENE,
						SceneFlowStage.DIALOGUE.name(),
						prepared.scenePrompt()));
		return sessionCoordinator.connect(
				new SceneGenerationResponse(
						prepared.sceneId(),
						List.of(),
						List.of(),
						List.of(),
						prepared.scenePrompt()),
				SCENE_NAME,
				SceneFlowStage.DIALOGUE,
				true,
				started,
				SceneType.INTERVIEW_SCENE,
				prepared.sceneId(),
				prepared.scenePrompt(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}
}
