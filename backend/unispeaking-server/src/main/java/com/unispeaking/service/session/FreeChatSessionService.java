package com.unispeaking.service.session;

import com.unispeaking.component.SessionIdGenerator;
import com.unispeaking.domain.dto.session.AddSessionMessageRequest;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionRequest;
import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.vo.conversation.SpeakerType;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.conversation.FreeChatConversationService;
import com.unispeaking.service.memory.SessionMemoryService;
import com.unispeaking.service.quota.UsageQuotaService;
import com.unispeaking.service.realtime.RealtimeConnectionService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FreeChatSessionService extends SessionService {

	private final FreeChatConversationService conversationService;
	private final SessionMemoryService memoryService;

	public FreeChatSessionService(
			SessionStateStore sessionStateStore,
			AuthService authService,
			UsageQuotaService usageQuotaService,
			SessionIdGenerator sessionIdGenerator,
			RealtimeConnectionService realtimeConnectionService,
			FreeChatConversationService conversationService,
			SessionMemoryService memoryService) {
		super(
				sessionStateStore,
				authService,
				usageQuotaService,
				sessionIdGenerator,
				realtimeConnectionService);
		this.conversationService = conversationService;
		this.memoryService = memoryService;
	}

	@Override
	protected AbstractSceneSession createSession(String localSessionId, String userId) {
		return new FreeChatSceneSession(localSessionId, userId);
	}

	@Override
	protected SessionPrompt prepareScene(AbstractSceneSession session, StartSessionRequest request) {
		return new SessionPrompt(request.prompt());
	}

	@Override
	protected void appendMessage(AbstractSceneSession session, AddSessionMessageRequest request) {
		Message message = request.message();
		if (message == null || message.content() == null || message.content().isBlank()) {
			return;
		}
		conversationService.appendMessage(new ConversationMessage(
				"msg_" + UUID.randomUUID(),
				session.getId(),
				toSpeaker(message.owner()),
				message.content(),
				Instant.now()));
	}

	@Override
	protected void handleSessionCompleted(AbstractSceneSession session) {
		memoryService.updateAfterCompletion(session);
	}

	private SpeakerType toSpeaker(Integer owner) {
		if (owner == null) {
			return SpeakerType.USER;
		}
		return owner == 0 ? SpeakerType.ASSISTANT : SpeakerType.USER;
	}
}
