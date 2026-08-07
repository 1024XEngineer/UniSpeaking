package com.unispeaking.service.scene;

public interface SceneService<REQUEST, RESPONSE> {

	/** 根据请求生成并持久化一个场景。 */
	RESPONSE generate(REQUEST request);
}
