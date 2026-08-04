package com.unispeaking.infrastructure.persistence.repository.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.feedback.UserFeedback;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.infrastructure.persistence.entity.feedback.UserFeedbackEntity;
import com.unispeaking.infrastructure.persistence.mapper.feedback.UserFeedbackMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeedbackRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

	private UserFeedbackMapper mapper;
	private FeedbackRepository repository;

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "feedback-test"),
				UserFeedbackEntity.class);
	}

	@BeforeEach
	void setUp() {
		mapper = mock(UserFeedbackMapper.class);
		repository = new FeedbackRepository(mapper);
	}

	@Test
	void createsAndMapsFeedback() {
		when(mapper.insert(any(UserFeedbackEntity.class))).thenReturn(1);
		UserFeedback feedback = feedback();

		assertEquals(feedback, repository.create(feedback));
	}

	@Test
	void findsFeedbackAndListsByOwnerAndStatus() {
		UserFeedbackEntity entity = entity();
		when(mapper.selectOne(any())).thenReturn(entity);
		when(mapper.selectList(any())).thenReturn(List.of(entity));

		assertEquals("FB-20260804-ABCDEF123456", repository
				.findByFeedbackNo(" fb-20260804-abcdef123456 ")
				.orElseThrow()
				.feedbackNo());
		assertEquals(1, repository.findAllByUserId(entity.getUserId()).size());
		assertEquals(1, repository.findAll(FeedbackStatus.SUBMITTED).size());
	}

	@Test
	void updatesOnlyExpectedCurrentStatus() {
		when(mapper.update(isNull(), any())).thenReturn(1);
		UserFeedback previous = feedback();
		UserFeedback updated = previous.withResolution(
				FeedbackStatus.RESOLVED,
				"已经处理",
				NOW.plusSeconds(60));

		assertEquals(updated, repository.update(previous, updated));

		when(mapper.update(isNull(), any())).thenReturn(0);
		BusinessException conflict = assertThrows(
				BusinessException.class,
				() -> repository.update(previous, updated));
		assertEquals("FEEDBACK_UPDATE_CONFLICT", conflict.code());
	}

	private UserFeedback feedback() {
		return new UserFeedback(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"FB-20260804-ABCDEF123456",
				UUID.fromString("11111111-1111-4111-8111-111111111111"),
				"a".repeat(64),
				"audio",
				"麦克风无法使用",
				"已允许权限但没有声音",
				"Chrome 138",
				FeedbackStatus.SUBMITTED,
				null,
				null,
				NOW,
				NOW);
	}

	private UserFeedbackEntity entity() {
		UserFeedback feedback = feedback();
		UserFeedbackEntity entity = new UserFeedbackEntity();
		entity.setId(feedback.id());
		entity.setFeedbackNo(feedback.feedbackNo());
		entity.setUserId(feedback.userId());
		entity.setLookupCodeHash(feedback.lookupCodeHash());
		entity.setCategoryId(feedback.categoryId());
		entity.setTitle(feedback.title());
		entity.setDescription(feedback.description());
		entity.setEnvironment(feedback.environment());
		entity.setStatus(feedback.status().name());
		entity.setCreatedAt(NOW.atOffset(ZoneOffset.UTC));
		entity.setUpdatedAt(NOW.atOffset(ZoneOffset.UTC));
		return entity;
	}
}
