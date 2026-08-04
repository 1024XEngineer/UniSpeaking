package com.unispeaking.infrastructure.persistence.repository.feedback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.feedback.UserFeedback;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.infrastructure.persistence.entity.feedback.UserFeedbackEntity;
import com.unispeaking.infrastructure.persistence.mapper.feedback.UserFeedbackMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {

	private final UserFeedbackMapper mapper;

	public FeedbackRepository(UserFeedbackMapper mapper) {
		this.mapper = mapper;
	}

	public UserFeedback create(UserFeedback feedback) {
		try {
			if (mapper.insert(toEntity(feedback)) != 1) {
				throw persistenceFailure();
			}
			return feedback;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<UserFeedback> findByFeedbackNo(String feedbackNo) {
		try {
			UserFeedbackEntity entity = mapper.selectOne(
					new LambdaQueryWrapper<UserFeedbackEntity>()
							.eq(UserFeedbackEntity::getFeedbackNo, normalizeFeedbackNo(feedbackNo)));
			return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<UserFeedback> findAllByUserId(UUID userId) {
		try {
			return mapper.selectList(
						new LambdaQueryWrapper<UserFeedbackEntity>()
								.eq(UserFeedbackEntity::getUserId, userId)
								.orderByDesc(
										UserFeedbackEntity::getCreatedAt,
										UserFeedbackEntity::getFeedbackNo))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<UserFeedback> findAll(FeedbackStatus status) {
		try {
			LambdaQueryWrapper<UserFeedbackEntity> query =
					new LambdaQueryWrapper<UserFeedbackEntity>()
							.orderByDesc(
									UserFeedbackEntity::getUpdatedAt,
									UserFeedbackEntity::getFeedbackNo);
			if (status != null) {
				query.eq(UserFeedbackEntity::getStatus, status.name());
			}
			return mapper.selectList(query).stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public UserFeedback update(UserFeedback previous, UserFeedback updated) {
		try {
			int count = mapper.update(
					null,
					new LambdaUpdateWrapper<UserFeedbackEntity>()
							.eq(UserFeedbackEntity::getFeedbackNo, previous.feedbackNo())
							.eq(UserFeedbackEntity::getStatus, previous.status().name())
							.set(UserFeedbackEntity::getStatus, updated.status().name())
							.set(UserFeedbackEntity::getReply, updated.reply())
							.set(
									UserFeedbackEntity::getRepliedAt,
									updated.repliedAt() == null ? null : atUtc(updated.repliedAt()))
							.set(UserFeedbackEntity::getUpdatedAt, atUtc(updated.updatedAt())));
			if (count != 1) {
				throw new BusinessException(
						"FEEDBACK_UPDATE_CONFLICT",
						"反馈状态已发生变化，请刷新后重试");
			}
			return updated;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private String normalizeFeedbackNo(String feedbackNo) {
		if (feedbackNo == null || feedbackNo.isBlank()) {
			throw feedbackNotFound();
		}
		return feedbackNo.trim().toUpperCase(Locale.ROOT);
	}

	private UserFeedbackEntity toEntity(UserFeedback feedback) {
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
		entity.setReply(feedback.reply());
		entity.setRepliedAt(feedback.repliedAt() == null ? null : atUtc(feedback.repliedAt()));
		entity.setCreatedAt(atUtc(feedback.createdAt()));
		entity.setUpdatedAt(atUtc(feedback.updatedAt()));
		return entity;
	}

	private UserFeedback toDomain(UserFeedbackEntity entity) {
		return new UserFeedback(
				entity.getId(),
				entity.getFeedbackNo(),
				entity.getUserId(),
				entity.getLookupCodeHash(),
				entity.getCategoryId(),
				entity.getTitle(),
				entity.getDescription(),
				entity.getEnvironment(),
				FeedbackStatus.valueOf(entity.getStatus()),
				entity.getReply(),
				toInstant(entity.getRepliedAt()),
				toInstant(entity.getCreatedAt()),
				toInstant(entity.getUpdatedAt()));
	}

	private OffsetDateTime atUtc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	private BusinessException feedbackNotFound() {
		return new BusinessException("FEEDBACK_NOT_FOUND", "没有找到这条反馈");
	}

	private BusinessException persistenceFailure() {
		return new BusinessException("FEEDBACK_PERSISTENCE_FAILED", "反馈保存失败，请稍后重试");
	}
}
