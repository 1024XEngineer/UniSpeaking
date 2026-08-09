package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.InterviewTurnRequest;
import com.unispeaking.domain.dto.session.InterviewTurnResult;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

/**
 * 面试会话服务（独立接口，不 extends 任何已删除的 SessionService 基类）。
 * <p>本刀提供 {@link #startSession}、{@link #addMessage}（复用标准 WS 路径，
 * 由 {@code SessionMessageDispatcher} 消费）与 {@link #submitTurn}；
 * {@code endInterview} 留待第五刀。</p>
 */
public interface InterviewSessionService {

	/** 首面/复练统一启动：归属校验 + 配额 + 建会话 + 实时连接，不重复做场景准备。 */
	StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request);

	/** WS 消息投影入口，委托 SessionLifecycleManager 追加消息。 */
	void addMessage(String sessionId, Message message);

	/**
	 * 逐轮提交（multipart：{@link InterviewTurnRequest}）：在 {@code synchronized(session)}
	 * 临界区内完成幂等锚定（owner=1 消息数 + content 比对），临界区外做 LLM 主题识别并推进
	 * 主题状态机。不写 {@code session_message}（WS 已写）、不写 {@code turn_evaluation}、不评分。
	 */
	InterviewTurnResult submitTurn(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript,
			byte[] audio);
}
