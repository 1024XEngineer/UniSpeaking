package com.unispeaking.infrastructure.persistence.evaluation.mapper;

import com.unispeaking.infrastructure.persistence.evaluation.session.PracticeSessionEvaluationContext;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 评分模块对练习会话及场景上下文的只读数据库映射。
 */
public interface PracticeSessionEvaluationMapper {

	/**
	 * 查询一场练习的身份、状态和可选场景信息。
	 *
	 * <p>场景采用 LEFT JOIN，保证 FREE_CHAT 会话能够返回；不按 deleted_at
	 * 过滤，以便历史会话仍能使用其原场景上下文完成或查询评分。</p>
	 */
	@Select("""
			SELECT
			    ps.id AS "sessionId",
			    ps.user_id AS "userId",
			    ps.practice_mode AS "practiceMode",
			    ps.status AS status,
			    cs.background AS background,
			    cs.ai_role AS "aiRole",
			    cs.user_role AS "userRole",
			    cs.learning_goal AS "learningGoal"
			FROM public.practice_sessions ps
			LEFT JOIN public.custom_scenes cs ON cs.id = ps.scene_id
			WHERE ps.id = #{sessionId}
			""")
	PracticeSessionEvaluationContext findBySessionId(
			@Param("sessionId") UUID sessionId);
}
