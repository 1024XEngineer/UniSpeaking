package com.unispeaking.service.session;

import com.unispeaking.domain.dto.evaluation.InterviewEndResponse;
import com.unispeaking.domain.dto.evaluation.InterviewReportResponse;
import com.unispeaking.domain.dto.session.InterviewTurnResult;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

/**
 * 面试会话服务（独立接口，不 extends 任何已删除的 SessionService 基类）。
 * <p>本刀提供 {@link #startSession}、{@link #addMessage}（复用标准 WS 路径，
 * 由 {@code SessionMessageDispatcher} 消费）、{@link #submitTurn}、{@link #endInterview}
 * （幂等结束编排）与报告查询/重试/AI 音频上报。</p>
 */
public interface InterviewSessionService {

	/** 首面/复练统一启动：归属校验 + 配额 + 建会话 + 实时连接，不重复做场景准备。 */
	StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request);

	/** WS 消息投影入口，委托 SessionLifecycleManager 追加消息。 */
	void addMessage(String sessionId, Message message);

	/**
	 * 逐轮提交（multipart：transcript + audio）：在 {@code synchronized(session)}
	 * 临界区内完成幂等锚定（owner=1 消息数 + content 比对）+ 存录音并 attach，临界区外做
	 * LLM 主题识别并推进主题状态机；{@code shouldEnd=true} 时进入幂等结束编排。
	 */
	InterviewTurnResult submitTurn(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript,
			byte[] audio);

	/** 用户主动结束（幂等结束编排）：与 submitTurn 的 shouldEnd 分支共用 orchestrateEnd。 */
	InterviewEndResponse endInterview(String sceneId, String sessionId);

	/** 轮询报告：PROCESSING 过期时惰性重派；FAILED/COMPLETED 原样返回。 */
	InterviewReportResponse getReport(String sceneId, String sessionId);

	/** 手动重试：FAILED→PROCESSING CAS（幂等），成功后重提交报告任务。 */
	InterviewReportResponse retryReport(String sceneId, String sessionId);

	/** AI「实际播放的」音频上报：归属校验后落盘 ai-{uuid}.wav，不挂消息、不参与评分。 */
	String uploadAiAudio(String sceneId, String sessionId, byte[] audio);
}
