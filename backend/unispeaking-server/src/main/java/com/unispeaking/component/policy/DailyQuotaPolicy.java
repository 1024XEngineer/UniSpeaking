package com.unispeaking.component.policy;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 独立配额策略（派生计数，无需每日重置 job）：按 {@code practice_session} 当日
 * COMPLETED 会话计数。复用 {@code idx_practice_session_user_completed_at}
 * （user_id, ended_at DESC WHERE status='COMPLETED' AND ended_at IS NOT NULL）方向。
 *
 * <p>当前消费方为 Interview（generate 与 startSession 双处校验，复练也计次）；
 * 预期第二消费方为 IELTS（迁移），故入参携带 {@link SceneType}。
 */
@Component
public class DailyQuotaPolicy {

	private final PracticeSessionRepository practiceSessionRepository;

	public DailyQuotaPolicy(PracticeSessionRepository practiceSessionRepository) {
		this.practiceSessionRepository = practiceSessionRepository;
	}

	/**
	 * 校验用户当日已完成会话数未超过 {@code limit}，超过抛
	 * {@code INTERVIEW_DAILY_LIMIT_REACHED}。
	 */
	public void assertWithinQuota(
			String userId,
			SceneType sceneType,
			int limit) {
		UUID ownerId = requireUserId(userId);
		if (sceneType == null) {
			throw new BusinessException(
					"SCENE_TYPE_REQUIRED",
					"配额校验必须指定场景类型");
		}
		long completedToday = practiceSessionRepository.countCompletedOnDate(
				ownerId,
				sceneType,
				LocalDate.now(ZoneOffset.UTC));
		if (completedToday >= limit) {
			throw new BusinessException(
					InterviewErrorCode.INTERVIEW_DAILY_LIMIT_REACHED,
					"今日练习次数已达上限，请明天再试");
		}
	}

	private UUID requireUserId(String userId) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException(
					"AUTHENTICATION_REQUIRED",
					"请先登录");
		}
		try {
			return UUID.fromString(userId);
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(
					"INVALID_USER_ID",
					"用户标识必须是 UUID");
		}
	}
}
