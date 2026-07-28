package com.unispeaking.service.memory;

import com.unispeaking.domain.po.session.AbstractSceneSession;

public interface SessionMemoryService {
	void updateAfterCompletion(AbstractSceneSession session);
}
