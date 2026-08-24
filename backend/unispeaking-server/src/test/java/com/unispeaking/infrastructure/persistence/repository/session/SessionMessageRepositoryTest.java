package com.unispeaking.infrastructure.persistence.repository.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.session.LearnerMessageRecord;
import com.unispeaking.infrastructure.persistence.entity.session.SessionMessageEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.SessionMessageMapper;
import java.util.List;
import java.util.Arrays;
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
				"custom_1",
				"session_1",
				2,
				new Message(1, "  Answer  ", new byte[] {1, 2}));
		assertEquals(List.of(new Message(1, "Answer", null)),
				repository.findMessages("session_1"));

		ArgumentCaptor<SessionMessageEntity> captor =
				ArgumentCaptor.forClass(SessionMessageEntity.class);
		verify(mapper).insert(captor.capture());
		assertEquals("custom_1", captor.getValue().getSceneId());
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
				"custom_1",
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
	void readsLearnerMessagesAndAudioObjectKeysAndPreservesOnlyNonBlankUrls() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageEntity assistant = new SessionMessageEntity();
		assistant.setOwner(0);
		assistant.setContent("question");
		SessionMessageEntity learner = new SessionMessageEntity();
		learner.setOwner(1);
		learner.setContent("answer");
		learner.setMessageNo(2);
		learner.setAudioUrl("/audio/answer.wav");
		learner.setAudioObjectKey("sessions/s1/turn-1.wav");
		SessionMessageEntity blankUrl = new SessionMessageEntity();
		blankUrl.setOwner(1);
		blankUrl.setContent("second answer");
		blankUrl.setAudioUrl(" ");
		blankUrl.setAudioObjectKey(null);
		when(mapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(learner, blankUrl))
				.thenReturn(List.of(learner, blankUrl))
				.thenReturn(List.of(learner, blankUrl));
		SessionMessageRepository repository = new SessionMessageRepository(mapper);

		assertEquals(List.of(
				new Message(1, "answer", null),
				new Message(1, "second answer", null)),
				repository.findLearnerMessages("s1"));
		assertEquals(List.of("/audio/answer.wav"), repository.findAudioUrls("s1"));
		assertEquals(Arrays.asList("sessions/s1/turn-1.wav", null), repository.findAudioObjectKeys("s1"));
	}

	@Test
	void mapsLearnerMessagesWithAudioKeys() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageEntity learner = new SessionMessageEntity();
		learner.setOwner(1);
		learner.setMessageNo(4);
		learner.setContent("answer");
		learner.setAudioObjectKey("sessions/s1/turn-4.wav");
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(learner));
		SessionMessageRepository repository = new SessionMessageRepository(mapper);

		assertEquals(List.of(new LearnerMessageRecord(4, "answer", "sessions/s1/turn-4.wav")),
				repository.findMessagesWithAudioObjectKeys("s1"));
	}

	@Test
	void rejectsObjectKeyUpdateWhenNoRowWasChangedOrMapperFails() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageEntity learner = new SessionMessageEntity();
		learner.setOwner(1);
		learner.setMessageNo(1);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(learner));
		org.mockito.Mockito.doAnswer(invocation -> 0).when(mapper)
				.update(isNull(SessionMessageEntity.class), any(Wrapper.class));
		SessionMessageRepository repository = new SessionMessageRepository(mapper);

		assertFailure(() -> repository.attachLearnerAudioObjectKey("s1", 1, "new.wav"));

		org.mockito.Mockito.doThrow(new IllegalStateException("update"))
				.when(mapper).update(org.mockito.ArgumentMatchers.nullable(SessionMessageEntity.class),
						any(Wrapper.class));
		assertFailure(() -> repository.attachLearnerAudioObjectKey("s1", 1, "new.wav"));
	}

	@Test
	void translatesAudioUrlUpdateAndAudioKeyReadFailures() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageEntity learner = new SessionMessageEntity();
		learner.setOwner(1);
		learner.setMessageNo(1);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(learner));
		when(mapper.update(any(SessionMessageEntity.class), any(Wrapper.class))).thenReturn(0);
		SessionMessageRepository repository = new SessionMessageRepository(mapper);

		assertFailure(() -> repository.attachLearnerAudioUrl("s1", 1, "audio.wav"));

		when(mapper.selectList(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("read"));
		assertFailure(() -> repository.findLearnerMessages("s1"));
		assertFailure(() -> repository.findAudioUrls("s1"));
		assertFailure(() -> repository.findAudioObjectKeys("s1"));
		assertFailure(() -> repository.findMessagesWithAudioObjectKeys("s1"));
	}

	@Test
	void translatesWriteReadAndDeleteFailures() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageRepository repository =
				new SessionMessageRepository(mapper);
		when(mapper.insert(any(SessionMessageEntity.class))).thenReturn(0);
		assertFailure(() -> repository.append(
				"custom_1", "session_1", 1, new Message(0, "Q", null)));

		when(mapper.selectList(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("read"));
		assertFailure(() -> repository.findMessages("session_1"));
		assertFailure(() -> repository.findSceneId("session_1"));

		when(mapper.delete(any(Wrapper.class)))
				.thenThrow(new IllegalStateException("delete"));
		assertFailure(() -> repository.deleteObsoleteForScene(
				"custom_1", "session_1"));
	}

	@Test
	void rejectsInvalidAudioAttachmentAndMissingLearnerTurn() {
		SessionMessageMapper mapper = mock(SessionMessageMapper.class);
		SessionMessageRepository repository = new SessionMessageRepository(mapper);
		assertFailure(() -> repository.attachLearnerAudioObjectKey("session", 0, "audio.wav"));
		assertFailure(() -> repository.attachLearnerAudioUrl("session", 1, " "));
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		assertFailure(() -> repository.attachLearnerAudioObjectKey("session", 1, "audio.wav"));
		verify(mapper, never()).update(any(), any(Wrapper.class));
	}

	private void assertFailure(org.junit.jupiter.api.function.Executable action) {
		BusinessException exception = assertThrows(BusinessException.class, action);
		assertEquals("SESSION_MESSAGE_PERSISTENCE_FAILED", exception.code());
	}
}
