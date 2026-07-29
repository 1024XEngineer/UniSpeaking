package com.unispeaking.infrastructure.persistence.evaluation.mapper;

import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedbackRow;
import com.unispeaking.infrastructure.persistence.evaluation.typehandler.PostgresTextArrayTypeHandler;
import java.util.UUID;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

/**
 * 整场反馈的 PostgreSQL 写入和查询映射。
 */
public interface PracticeResultFeedbackMapper {

	/**
	 * 以 session_id 为冲突键新增或整体覆盖整场反馈。
	 */
	@Insert("""
			INSERT INTO public.practice_result_feedbacks (
			    session_id,
			    summary,
			    strengths,
			    improvements
			)
			VALUES (
			    #{feedback.sessionId},
			    #{feedback.summary},
			    #{feedback.strengths,
			        jdbcType=ARRAY,
			        typeHandler=com.unispeaking.infrastructure.persistence.evaluation.typehandler.PostgresTextArrayTypeHandler},
			    #{feedback.improvements,
			        jdbcType=ARRAY,
			        typeHandler=com.unispeaking.infrastructure.persistence.evaluation.typehandler.PostgresTextArrayTypeHandler}
			)
			ON CONFLICT (session_id) DO UPDATE
			SET summary = EXCLUDED.summary,
			    strengths = EXCLUDED.strengths,
			    improvements = EXCLUDED.improvements,
			    updated_at = CURRENT_TIMESTAMP
			""")
	int upsert(@Param("feedback") PracticeResultFeedbackRow feedback);

	/**
	 * 按会话标识读取整场反馈，并显式使用 TEXT[] TypeHandler 构造行投影。
	 */
	@Select("""
			SELECT
			    session_id AS "sessionId",
			    summary AS summary,
			    strengths AS strengths,
			    improvements AS improvements
			FROM public.practice_result_feedbacks
			WHERE session_id = #{sessionId}
			""")
	@ConstructorArgs({
		@Arg(column = "sessionId", javaType = UUID.class, id = true),
		@Arg(column = "summary", javaType = String.class),
		@Arg(
				column = "strengths",
				javaType = String[].class,
				jdbcType = JdbcType.ARRAY,
				typeHandler = PostgresTextArrayTypeHandler.class),
		@Arg(
				column = "improvements",
				javaType = String[].class,
				jdbcType = JdbcType.ARRAY,
				typeHandler = PostgresTextArrayTypeHandler.class)
	})
	PracticeResultFeedbackRow findBySessionId(
			@Param("sessionId") UUID sessionId);
}
