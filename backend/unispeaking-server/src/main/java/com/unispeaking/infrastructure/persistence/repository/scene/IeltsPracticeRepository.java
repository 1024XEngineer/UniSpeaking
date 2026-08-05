package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.IeltsPracticeRecord;
import com.unispeaking.domain.po.scene.IeltsUserSettings;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.infrastructure.persistence.entity.scene.IeltsPracticeEntity;
import com.unispeaking.infrastructure.persistence.entity.scene.UserIeltsEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.IeltsPracticeMapper;
import com.unispeaking.infrastructure.persistence.mapper.scene.UserIeltsMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class IeltsPracticeRepository {

	private final IeltsPracticeMapper practiceMapper;
	private final UserIeltsMapper userIeltsMapper;
	private final ObjectMapper objectMapper;

	public IeltsPracticeRepository(
			IeltsPracticeMapper practiceMapper,
			UserIeltsMapper userIeltsMapper,
			ObjectMapper objectMapper) {
		this.practiceMapper = practiceMapper;
		this.userIeltsMapper = userIeltsMapper;
		this.objectMapper = objectMapper;
	}

	public IeltsUserSettings getOrCreateSettings(UUID userId) {
		UserIeltsEntity existing = userIeltsMapper.selectById(userId);
		if (existing != null) {
			return toSettings(existing);
		}
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UserIeltsEntity created = new UserIeltsEntity();
		created.setUserId(userId);
		created.setTodayCompletedCount(0);
		created.setCreatedAt(now);
		created.setUpdatedAt(now);
		try {
			userIeltsMapper.insert(created);
			return toSettings(created);
		}
		catch (RuntimeException exception) {
			UserIeltsEntity concurrent = userIeltsMapper.selectById(userId);
			if (concurrent != null) {
				return toSettings(concurrent);
			}
			throw persistenceFailure(exception);
		}
	}

	public IeltsUserSettings updateSettings(
			UUID userId,
			java.math.BigDecimal targetScore,
			String preferredVoice) {
		getOrCreateSettings(userId);
		LambdaUpdateWrapper<UserIeltsEntity> update =
				new LambdaUpdateWrapper<UserIeltsEntity>()
						.eq(UserIeltsEntity::getUserId, userId)
						.set(UserIeltsEntity::getUpdatedAt,
								OffsetDateTime.now(ZoneOffset.UTC));
		if (targetScore != null) {
			update.set(UserIeltsEntity::getTargetScore, targetScore);
		}
		if (preferredVoice != null && !preferredVoice.isBlank()) {
			update.set(UserIeltsEntity::getPreferredVoice, preferredVoice);
		}
		try {
			if (userIeltsMapper.update(null, update) != 1) {
				throw persistenceFailure(null);
			}
			return toSettings(userIeltsMapper.selectById(userId));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public void createPractice(IeltsPracticeRecord record) {
		IeltsPracticeEntity entity = new IeltsPracticeEntity();
		entity.setIeltsId(record.ieltsId());
		entity.setUserId(record.userId());
		entity.setMode(record.mode().name());
		entity.setSelectedPart(record.selectedPart() == null
				? null
				: record.selectedPart().name());
		entity.setSelectedTopicId(record.selectedTopicId());
		try {
			entity.setContent(objectMapper.writeValueAsString(record.content()));
		}
		catch (JacksonException exception) {
			throw new BusinessException(
					"IELTS_CONTENT_INVALID",
					"IELTS 训练内容无法序列化");
		}
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		try {
			if (practiceMapper.insert(entity) != 1) {
				throw persistenceFailure(null);
			}
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public Optional<IeltsPracticeRecord> findPractice(String ieltsId) {
		if (ieltsId == null || ieltsId.isBlank()) {
			return Optional.empty();
		}
		try {
			IeltsPracticeEntity entity = practiceMapper.selectById(ieltsId);
			if (entity == null) {
				return Optional.empty();
			}
			return Optional.of(new IeltsPracticeRecord(
					entity.getIeltsId(),
					entity.getUserId(),
					IeltsMode.valueOf(entity.getMode()),
					entity.getSelectedPart() == null
							? null
							: IeltsPart.valueOf(entity.getSelectedPart()),
					entity.getSelectedTopicId(),
					objectMapper.readValue(
							entity.getContent(),
							IeltsContent.class)));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public void incrementCompletedCount(UUID userId) {
		try {
			for (int attempt = 0; attempt < 5; attempt++) {
				UserIeltsEntity current = userIeltsMapper.selectById(userId);
				if (current == null) {
					throw persistenceFailure(null);
				}
				int completed = current.getTodayCompletedCount() == null
						? 0
						: current.getTodayCompletedCount();
				if (completed >= 5) {
					throw dailyLimitReached();
				}
				int updated = userIeltsMapper.update(
						null,
						new LambdaUpdateWrapper<UserIeltsEntity>()
								.eq(UserIeltsEntity::getUserId, userId)
								.eq(
										UserIeltsEntity::getTodayCompletedCount,
										completed)
								.set(
										UserIeltsEntity::getTodayCompletedCount,
										completed + 1));
				if (updated == 1) {
					return;
				}
			}
			throw persistenceFailure(null);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	public int resetCompletedCounts() {
		try {
			return userIeltsMapper.update(
					null,
					new LambdaUpdateWrapper<UserIeltsEntity>()
							.gt(UserIeltsEntity::getTodayCompletedCount, 0)
							.set(UserIeltsEntity::getTodayCompletedCount, 0));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	private BusinessException dailyLimitReached() {
		return new BusinessException(
				"IELTS_DAILY_LIMIT_REACHED",
				"今日已完成 5 次 IELTS 练习，请明天再试");
	}

	private IeltsUserSettings toSettings(UserIeltsEntity entity) {
		return new IeltsUserSettings(
				entity.getUserId(),
				entity.getTargetScore(),
				entity.getTodayCompletedCount() == null
						? 0
						: entity.getTodayCompletedCount(),
				entity.getPreferredVoice());
	}

	private BusinessException persistenceFailure(Throwable cause) {
		return new BusinessException(
				"IELTS_PERSISTENCE_FAILED",
				"IELTS 练习记录保存失败");
	}
}
