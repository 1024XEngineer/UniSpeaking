package com.unispeaking.service.prompt;

import com.unispeaking.domain.vo.prompt.SessionPrompt;

public abstract class ScenePromptService<T> {

	public final SessionPrompt build(T context) {
		return new SessionPrompt(buildSystemPrompt(context));
	}

	protected abstract String buildSystemPrompt(T context);
}
