package com.unispeaking.service.scene;

/**
 * Stable stage-flow contract for scenes that have a learning or exam flow.
 */
public interface SceneFlowService<S> {

	S start(String sceneId);

	S current(String sceneId);

	S next(String sceneId);

	boolean isCompleted(String sceneId);
}
