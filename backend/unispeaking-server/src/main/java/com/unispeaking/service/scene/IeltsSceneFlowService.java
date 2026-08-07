package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.IeltsDialogueStateResponse;
import com.unispeaking.domain.dto.session.IeltsPart2StateResponse;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import com.unispeaking.domain.vo.scene.IeltsStage;

/** IELTS 流程服务，继承通用阶段流转能力并处理题目状态机。 */
public interface IeltsSceneFlowService extends SceneFlowService<IeltsStage> {

	/** 覆写通用流程方法，初始化并返回 IELTS 场景的首个阶段。 */
	@Override
	IeltsStage start(String sceneId);

	/** 覆写通用流程方法，返回 IELTS 场景当前阶段。 */
	@Override
	IeltsStage current(String sceneId);

	/** 覆写通用流程方法，推进并返回 IELTS 场景的新阶段。 */
	@Override
	IeltsStage next(String sceneId);

	/** 覆写通用流程方法，判断 IELTS 场景是否已经完成。 */
	@Override
	boolean isCompleted(String sceneId);

	/** 返回供客户端使用的 IELTS 流程快照。 */
	SceneFlowResponse response(String sceneId);

	/** 清除指定 IELTS 练习缓存的流程阶段。 */
	void clear(String sceneId);

	/** 根据当前 Part 为新会话初始化题目或 Part 2 状态。 */
	void startSessionState(String sceneId, String sessionId, IeltsPart part);

	/** 推进 Part 1 或 Part 3 的题目状态。 */
	IeltsDialogueStateResponse advanceDialogueState(
			String sceneId,
			String sessionId,
			int turnNo,
			boolean timedOut);

	/** 获取 Part 1 或 Part 3 当前题目状态。 */
	IeltsDialogueStateResponse getDialogueState(
			String sceneId,
			String sessionId);

	/** 根据事件推进 Part 2 的准备或答题状态。 */
	IeltsPart2StateResponse advancePart2State(
			String sceneId,
			String sessionId,
			IeltsPart2Event event);

	/** 获取 Part 2 当前准备或答题状态。 */
	IeltsPart2StateResponse getPart2State(
			String sceneId,
			String sessionId);

	/** 清除指定会话的全部 IELTS 流程状态。 */
	void clearSessionState(String sessionId);
}
