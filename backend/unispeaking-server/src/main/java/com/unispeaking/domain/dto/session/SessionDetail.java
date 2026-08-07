package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.SceneType;
import java.util.List;

public record SessionDetail(
		String sessionId,
		String sceneId,
		SceneType sceneType,
		String stage,
		List<Message> dialogue) {

	public SessionDetail {
		dialogue = dialogue == null ? List.of() : List.copyOf(dialogue);
	}
}
