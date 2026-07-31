package com.unispeaking.infrastructure.persistence.repository.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.unispeaking.infrastructure.persistence.entity.session.SessionMessageEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.SessionMessageMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionMessageRepositoryTest {

	@Test
	void findsSceneIdFromFirstOrderedMessageWithoutSqlFragment() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageEntity first = new SessionMessageEntity();
		first.setSceneId("custom_scene_1");
		first.setSessionId("custom_session_1");
		first.setMessageNo(1);
		when(mapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(first));

		SessionMessageRepository repository =
				new SessionMessageRepository(mapper);

		assertEquals(
				"custom_scene_1",
				repository.findSceneId("custom_session_1").orElseThrow());
		verify(mapper).selectList(any(Wrapper.class));
	}
}
