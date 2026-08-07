package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.vo.scene.CustomStage;
import java.util.List;

/** 自定义场景流程服务，继承通用阶段流转能力并处理自定义对话状态。 */
public interface CustomSceneFlowService extends SceneFlowService<CustomStage> {

	/** 覆写通用流程方法，初始化并返回自定义场景的首个阶段。 */
	@Override
	CustomStage start(String sceneId);

	/** 覆写通用流程方法，返回自定义场景当前阶段。 */
	@Override
	CustomStage current(String sceneId);

	/** 覆写通用流程方法，推进并返回自定义场景的新阶段。 */
	@Override
	CustomStage next(String sceneId);

	/** 覆写通用流程方法，判断自定义场景是否已经完成。 */
	@Override
	boolean isCompleted(String sceneId);

	/** 清除指定自定义场景缓存的流程阶段。 */
	void clear(String sceneId);

	/** 返回供客户端使用的自定义场景流程快照。 */
	SceneFlowResponse response(String sceneId);

	/** 根据当前阶段返回自定义场景对应的学习内容。 */
	List<LearningContentItem> content(String sceneId);

	/** 为新启动的场景会话初始化自定义对话状态。 */
	ScenarioDialogueStateResponse startDialogueState(
			String sceneId,
			String sessionId,
			String successFactorJson,
			String learningGoal);

	/** 根据学习者的一轮转写推进自定义对话状态。 */
	ScenarioDialogueStateResponse advanceDialogueState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript);

	/** 获取指定自定义对话会话当前的状态。 */
	ScenarioDialogueStateResponse getDialogueState(
			String sceneId,
			String sessionId);

	/** 在状态存在时将自定义对话推进到收尾阶段。 */
	ScenarioDialogueStateResponse beginDialogueClosing(
			String sceneId,
			String sessionId);

	/** 在会话完成或启动失败后清除自定义对话状态。 */
	void clearDialogueState(String sessionId);
}
