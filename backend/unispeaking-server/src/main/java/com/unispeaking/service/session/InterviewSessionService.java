package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

/**
 * 面试会话服务（独立接口，不 extends 任何已删除的 SessionService 基类）。
 * <p>本刀提供 {@link #startSession}（启动复用）与 {@link #addMessage}
 * （复用标准 WS 路径，由 {@code SessionMessageDispatcher} 消费）；
 * {@code endInterview}/{@code submitTurn} 留待后续。
 */
public interface InterviewSessionService {

	/** 首面/复练统一启动：归属校验 + 配额 + 建会话 + 实时连接，不重复做场景准备。 */
	StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request);

	/** WS 消息投影入口，委托 SessionLifecycleManager 追加消息。 */
	void addMessage(String sessionId, Message message);
}
