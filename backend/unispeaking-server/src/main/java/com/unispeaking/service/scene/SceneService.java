package com.unispeaking.service.scene;

public interface SceneService<REQUEST, RESPONSE> {

	RESPONSE generate(REQUEST request);
}
