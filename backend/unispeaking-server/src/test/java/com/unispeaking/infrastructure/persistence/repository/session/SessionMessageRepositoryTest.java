package com.unispeaking.infrastructure.persistence.repository.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.infrastructure.persistence.entity.session.SessionMessageEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.SessionMessageMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

	@Test
	void appendsTrimmedMessageAndReadsOrderedMessagesWithoutAudio() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		when(mapper.insert(any(SessionMessageEntity.class))).thenReturn(1);
		SessionMessageEntity first = new SessionMessageEntity();
		first.setOwner(1);
		first.setContent("Answer");
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(first));
		SessionMessageRepository repository =
				new SessionMessageRepository(mapper);

		repository.append(
				"interview_1",
				"session_1",
				2,
				new Message(1, "  Answer  ", new byte[] {1, 2}));
		assertEquals(List.of(new Message(1, "Answer", null)),
				repository.findMessages("session_1"));

		ArgumentCaptor<SessionMessageEntity> captor =
				ArgumentCaptor.forClass(SessionMessageEntity.class);
		verify(mapper).insert(captor.capture());
		assertEquals("interview_1", captor.getValue().getSceneId());
		assertEquals("session_1", captor.getValue().getSessionId());
		assertEquals(2, captor.getValue().getMessageNo());
		assertEquals("Answer", captor.getValue().getContent());
		assertEquals(captor.getValue().getCreatedAt(),
				captor.getValue().getUpdateAt());
	}

	@Test
	void blankOrMissingSessionHasNoSceneAndObsoleteRowsAreDeleted() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(mapper.delete(any(Wrapper.class))).thenReturn(3);
		SessionMessageRepository repository =
				new SessionMessageRepository(mapper);

		assertTrue(repository.findSceneId(null).isEmpty());
		assertTrue(repository.findSceneId(" ").isEmpty());
		assertTrue(repository.findSceneId("missing").isEmpty());
		assertEquals(3, repository.deleteObsoleteForScene(
				"interview_1",
				"session_current"));
	}

	@Test
	void attachesAndReadsLearnerAudioUrlsInMessageOrder() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageEntity learner = new SessionMessageEntity();
		learner.setMessageNo(2);
		learner.setOwner(1);
		learner.setAudioUrl("/api/ielts/recordings/session_1/turn-1.wav");
		when(mapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(learner));
		when(mapper.update(
				any(SessionMessageEntity.class),
				any(Wrapper.class))).thenReturn(1);
		SessionMessageRepository repository =
				new SessionMessageRepository(mapper);

		repository.attachLearnerAudioUrl(
				"session_1",
				1,
				"/api/ielts/recordings/session_1/turn-1.wav");

		assertEquals(
				List.of("/api/ielts/recordings/session_1/turn-1.wav"),
				repository.findAudioUrls("session_1"));
		verify(mapper).update(
				any(SessionMessageEntity.class),
				any(Wrapper.class));
	}

	@Test
	void translatesWriteReadAndDeleteFailures() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageRepository repository =
				new SessionMessageRepository(mapper);
		when(mapper.insert(any(SessionMessageEntity.class))).thenReturn(0);
		assertFailure(() -> repository.append(
				"interview_1", "session_1", 1, new Message(0, "Q", null)));

		when(mapper.selectList(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("read"));
		assertFailure(() -> repository.findMessages("session_1"));
		assertFailure(() -> repository.findSceneId("session_1"));

		when(mapper.delete(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("delete"));
		assertFailure(() -> repository.deleteObsoleteForScene(
				"interview_1", "session_1"));
	}

	private void assertFailure(org.junit.jupiter.api.function.Executable action) {
		BusinessException exception = assertThrows(BusinessException.class, action);
		assertEquals("SESSION_MESSAGE_PERSISTENCE_FAILED", exception.code());
	}
}
