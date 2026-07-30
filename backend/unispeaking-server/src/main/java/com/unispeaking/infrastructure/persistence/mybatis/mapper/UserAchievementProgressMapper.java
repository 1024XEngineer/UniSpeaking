package com.unispeaking.infrastructure.persistence.mybatis.mapper;

import com.unispeaking.infrastructure.persistence.mybatis.entity.UserAchievementProgressEntity;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserAchievementProgressMapper {

	@Select("""
			SELECT user_id,
			       achievement_id,
			       progress_value,
			       unlocked_at,
			       created_at,
			       updated_at
			FROM user_achievement_progress
			WHERE user_id = #{userId}
			  AND achievement_id = #{achievementId}
			""")
	UserAchievementProgressEntity find(
			@Param("userId") UUID userId,
			@Param("achievementId") UUID achievementId);

	@Insert("""
			INSERT INTO user_achievement_progress (
			    user_id,
			    achievement_id,
			    progress_value,
			    unlocked_at,
			    created_at,
			    updated_at
			)
			VALUES (
			    #{progress.userId},
			    #{progress.achievementId},
			    #{progress.progressValue},
			    #{progress.unlockedAt},
			    #{progress.createdAt},
			    #{progress.updatedAt}
			)
			ON CONFLICT (user_id, achievement_id) DO UPDATE
			SET progress_value = EXCLUDED.progress_value,
			    unlocked_at = COALESCE(
			        user_achievement_progress.unlocked_at,
			        EXCLUDED.unlocked_at
			    ),
			    updated_at = EXCLUDED.updated_at
			""")
	int upsert(@Param("progress") UserAchievementProgressEntity progress);
}
