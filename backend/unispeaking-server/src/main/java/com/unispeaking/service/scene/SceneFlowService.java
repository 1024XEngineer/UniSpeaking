package com.unispeaking.service.scene;

/**
 * Stable stage-flow contract for scenes that have a learning or exam flow.
 */
public interface SceneFlowService<S> {

	/** 初始化场景流程并返回第一个阶段。 */
	S start(String sceneId);

	/** 返回场景当前所处的阶段。 */
	S current(String sceneId);

	/** 推进场景流程并返回新的阶段。 */
	S next(String sceneId);

	/** 判断场景流程是否已经到达结束阶段。 */
	boolean isCompleted(String sceneId);
}
