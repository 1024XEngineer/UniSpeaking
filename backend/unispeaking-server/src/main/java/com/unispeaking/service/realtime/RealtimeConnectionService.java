package com.unispeaking.service.realtime;

import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.dto.command.StartCommand;

public interface RealtimeConnectionService {
	RealtimeConnectionResult connect(
			ProviderType type, AbstractSceneSession session, SessionPrompt prompt, StartCommand command);
}
