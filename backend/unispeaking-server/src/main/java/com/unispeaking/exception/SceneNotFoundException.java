package com.unispeaking.exception;

public class SceneNotFoundException extends BusinessException {

	public SceneNotFoundException(String sceneId) {
		super("SCENE_NOT_FOUND", "Scene not found: " + sceneId);
	}
}
